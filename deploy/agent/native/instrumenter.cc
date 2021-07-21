/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "tools/base/deploy/agent/native/instrumenter.h"

#include <fcntl.h>
#include <jni.h>
#include <jvmti.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>

#include "tools/base/deploy/agent/native/class_finder.h"
#include "tools/base/deploy/agent/native/crash_logger.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/agent/native/native_callbacks.h"
#include "tools/base/deploy/agent/native/runtime.jar.cc"
#include "tools/base/deploy/agent/native/transform/hook_transform.h"
#include "tools/base/deploy/agent/native/transform/modify_parameter_transform.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

const char* kBreadcrumbClass = "com/android/tools/deploy/instrument/Breadcrumb";
const char* kHandlerWrapperClass =
    "com/android/tools/deploy/instrument/InstrumentationHooks";

const char* kDexUtilityClass = "com/android/tools/deploy/instrument/DexUtility";
const char* kPhaseClass = "com/android/tools/deploy/instrument/Phase";

const std::string kInstrumentationJarName =
    "instruments-"_s + runtime_jar_hash + ".jar";

namespace {

// Holds the transform that will be applied by Agent_ClassFileLoadHook.
const Transform* current_transform = nullptr;

// Holds the transformed bytes of the last class transformed by
// Agent_ClassFileLoadHook.
std::vector<dex::u4> last_class_bytes;

// Event that fires when the agent loads a class file.
extern "C" void JNICALL Agent_ClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  if (current_transform == nullptr ||
      current_transform->GetClassName() != name) {
    return;
  }

  // The class name needs to be in JNI-format.
  std::string descriptor = "L" + current_transform->GetClassName() + ";";

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(descriptor.c_str());
  if (class_index == dex::kNoIndex) {
    // TODO: Handle failure.
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  current_transform->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  last_class_bytes.clear();
  last_class_bytes.resize(new_image_size);
  memcpy(last_class_bytes.data(), new_image, new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
}

}  // namespace

#define FILE_MODE (S_IRUSR | S_IWUSR)

// Check if the jar_path exists. If it doesn't, generate its content using the
// jar embedded in the .data section of this executable.
// TODO: Don't write to disk. Have jvmti load the jar directly from a memory
// mapped fd to agent.so.
bool WriteJarToDiskIfNecessary(const std::string& jar_path) {
  // If file exists, there is no need to do anything.
  if (IO::access(jar_path, F_OK) != -1) {
    return true;
  }

  // TODO: Would be more efficient to have the offset and size and use
  // sendfile() to avoid a userland trip.
  int fd = IO::open(jar_path, O_WRONLY | O_CREAT, FILE_MODE);
  if (fd == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to open().");
    return false;
  }
  int written = write(fd, runtime_jar, runtime_jar_len);
  if (written == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to write().");
    return false;
  }

  int closeResult = close(fd);
  if (closeResult == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to close().");
    return false;
  }

  return true;
}

bool LoadInstrumentationJar(jvmtiEnv* jvmti, JNIEnv* jni,
                            const std::string& jar_path) {
  // Check for the existence of a breadcrumb class, indicating a previous agent
  // has already loaded instrumentation. If no previous agent has run on this
  // jvm, add our instrumentation classes to the bootstrap class loader.
  jclass klass = jni->FindClass(kBreadcrumbClass);
  if (klass == nullptr) {
    Log::V("No existing instrumentation found. Loading instrumentation from %s",
           kInstrumentationJarName.c_str());
    jni->ExceptionClear();
    const std::string root_aware_jar_path = IO::ResolvePath(jar_path);
    if (jvmti->AddToBootstrapClassLoaderSearch(root_aware_jar_path.c_str()) !=
        JVMTI_ERROR_NONE) {
      return false;
    }
  } else {
    // Ensure that the jar hasn't changed since we last instrumented. If it has,
    // fail out for now. This is an important scenario to guard against, since
    // it would likely cause silent failures.
    JniClass breadcrumb(jni, klass);
    jstring jar_hash = jni->NewStringUTF(runtime_jar_hash);
    jboolean matches = breadcrumb.CallStaticBooleanMethod(
        "checkHash", "(Ljava/lang/String;)Z", jar_hash);
    jni->DeleteLocalRef(jar_hash);

    if (!matches) {
      Log::E(
          "The instrumentation jar at %s does not match the jar previously "
          "used to instrument. The application must be restarted.",
          kInstrumentationJarName.c_str());
      return false;
    }
  }
  return true;
}

void Instrumenter::SetCachingEnabled(bool is_enabled) {
  caching_enabled_ = is_enabled;
}

bool Instrumenter::Instrument(const Transform& transform) const {
  return Instrument({&transform});
}

bool Instrumenter::Instrument(
    const std::vector<const Transform*>& transforms) const {
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_ClassFileLoadHook;

  bool success =
      CheckJvmti(
          jvmti_->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)),
          "Error setting event callbacks.") &&
      CheckJvmti(jvmti_->SetEventNotificationMode(
                     JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
                 "Could not enable class file load hook event") &&
      ApplyTransforms(transforms);

  // Failing either of these does not prevent us from proceeding, but we should
  // still log the event.
  CheckJvmti(jvmti_->SetEventNotificationMode(
                 JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not disable class file load hook event");
  CheckJvmti(jvmti_->SetEventCallbacks(nullptr, sizeof(jvmtiEventCallbacks)),
             "Could not clear event callbacks");
  return success;
}

bool Instrumenter::ApplyCachedTransforms(
    const std::vector<jclass> classes,
    const std::vector<const Transform*>& transforms) const {
  std::vector<std::vector<dex::u4>> cached_classes;
  for (const auto& transform : transforms) {
    std::vector<dex::u4> dex;
    if (!cache_.ReadClass(transform->GetClassName(), &dex)) {
      return false;
    }
    cached_classes.emplace_back(std::move(dex));
  }

  Log::V("Applying transforms with cached classes");

  std::vector<jvmtiClassDefinition> defs;
  for (int i = 0; i < classes.size(); ++i) {
    jvmtiClassDefinition def;
    def.klass = classes[i];
    def.class_byte_count = cached_classes[i].size();
    def.class_bytes =
        reinterpret_cast<const unsigned char*>(cached_classes[i].data());
    defs.push_back(def);
  }

  return CheckJvmti(jvmti_->RedefineClasses(defs.size(), defs.data()),
                    "Could not redefine with cached classes");
}

bool Instrumenter::ApplyTransforms(
    const std::vector<const Transform*>& transforms) const {
  ClassFinder finder(jvmti_, jni_);
  std::vector<jclass> classes;
  for (const auto& transform : transforms) {
    jclass klass = finder.FindClass(transform->GetClassName().c_str());
    if (klass == nullptr) {
      ErrEvent("Could not find class for instrumentation: " +
               transform->GetClassName());
      jni_->ExceptionClear();
      return false;
    }
    classes.push_back(klass);
  }

  // Instead of using slicer to instrument the dex, which is expensive, attempt
  // to read previously instrumented classes from a cache and use
  // RedefineClasses instead of RetransformClasses.
  if (caching_enabled_ && ApplyCachedTransforms(classes, transforms)) {
    return true;
  }

  std::vector<std::string> failed_classes;
  for (int i = 0; i < classes.size(); ++i) {
    current_transform = transforms[i];
    bool success = CheckJvmti(
        jvmti_->RetransformClasses(1, &classes[i]),
        "Could not retransform class: " + transforms[i]->GetClassName());
    jni_->DeleteLocalRef(classes[i]);

    // We intentionally do not stop if one transformation fails, because it's
    // useful to collect data on every failing transform - and if one is failing
    // due to platform/OEM changes, others might as well.
    if (!success) {
      failed_classes.push_back(transforms[i]->GetClassName());
      continue;
    }

    // RetransformClasses causes the Agent_ClassFileLoadHook callback to
    // populate last_class_bytes.
    if (caching_enabled_) {
      cache_.WriteClass(transforms[i]->GetClassName(), last_class_bytes);
    }
  }

  if (!failed_classes.empty()) {
    CrashLogger::Instance().LogInstrumentationFailures(failed_classes);
  }

  return failed_classes.empty();
}

bool Instrument(const Instrumenter& instrumenter, bool overlay_swap) {
  const ModifyParameterTransform loaders(
      /* target class */ "android/app/ApplicationLoaders",
      /* target method */ "getClassLoader",
      /* target signature */
      "(Ljava/lang/String;IZLjava/lang/String;Ljava/lang/String;Ljava/lang/"
      "ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Ljava/"
      "lang/ClassLoader;",
      /* parameter index to modify */ 3,
      /* parameter transform function */ "modifyNativeSearchPath");

  const HookTransform thread(
      /* target class */ "java/lang/Thread",
      /* target method */ "dispatchUncaughtException",
      /* target signature */ "(Ljava/lang/Throwable;)V",
      "logUnhandledException", MethodHooks::kNoHook);

  const HookTransform activity_thread(
      /* target class */ "android/app/ActivityThread",
      /* target method */ "handleDispatchPackageBroadcast",
      /* target signature */ "(I[Ljava/lang/String;)V",
      "handleDispatchPackageBroadcastEntry",
      "handleDispatchPackageBroadcastExit");

  const HookTransform dex_path_list_element(
      /* target class */ "dalvik/system/DexPathList$Element",
      /* target method */ "findResource",
      /* target signature */ "(Ljava/lang/String;)Ljava/net/URL;",
      "handleFindResourceEntry", MethodHooks::kNoHook);

  const HookTransform dex_path_list(
      /* target class */ "dalvik/system/DexPathList",
      /* target method */ "splitDexPath",
      /* target signature */ "(Ljava/lang/String;)Ljava/util/List;",
      MethodHooks::kNoHook, "handleSplitDexPathExit");

  const HookTransform res_manager(
      /* target class */ "android/app/ResourcesManager",
      /* target method */ "applyNewResourceDirsLocked",
      /* target signature */
      "(Landroid/content/pm/ApplicationInfo;[Ljava/lang/String;)V",
      "addResourceOverlays", MethodHooks::kNoHook);

  const HookTransform loaded_apk(
      /* target class */ "android/app/LoadedApk",
      /* target method */ "getResources",
      /* target signature */
      "()Landroid/content/res/Resources;", MethodHooks::kNoHook,
      "addResourceOverlays");

  if (overlay_swap) {
    return instrumenter.Instrument(
        {&loaders, &thread, &dex_path_list, &loaded_apk, &res_manager});
  } else {
    return instrumenter.Instrument({&activity_thread, &dex_path_list_element});
  }
}

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name, bool overlay_swap) {
  std::string instrument_jar_path =
      Sites::AppStudio(package_name) + kInstrumentationJarName;

  // Make sure the instrumentation jar is ready on disk.
  if (!WriteJarToDiskIfNecessary(instrument_jar_path)) {
    Log::E("Error writing instrumentation.jar to disk.");
    return false;
  }

  if (!LoadInstrumentationJar(jvmti, jni, instrument_jar_path)) {
    Log::E("Error loading instrumentation dex.");
    return false;
  }

  // Check if we need to instrument, or if a previous agent successfully did.
  JniClass breadcrumb(jni, kBreadcrumbClass);
  if (breadcrumb.CallStaticBooleanMethod("isFinishedInstrumenting", "()Z")) {
    return true;
  }

  auto cache = TransformCache::Create(instrument_jar_path + ".cache");
  Instrumenter instrumenter(jvmti, jni, cache);

  // Disable caching for non-overlay swap, as the cache directory gets cleared
  // on installation.
  if (!overlay_swap) {
    instrumenter.SetCachingEnabled(false);
  }

  if (!Instrument(instrumenter, overlay_swap)) {
    Log::E("Error instrumenting application.");
    return false;
  }

  breadcrumb.CallStaticVoidMethod("setFinishedInstrumenting", "()V");
  LogEvent("Finished instrumenting");

  // Need to register native methods every time; otherwise, the Java methods
  // could potentially call old versions if a previous agent.so was loaded.
  RegisterNative(jni, {kHandlerWrapperClass, "fixAppContext",
                       "(Ljava/lang/Object;)Ljava/lang/Object;",
                       (void*)&Native_FixAppContext});

  RegisterNative(jni, {kHandlerWrapperClass, "getActivityClientRecords",
                       "(Ljava/lang/Object;)Ljava/util/Collection;",
                       (void*)&Native_GetActivityClientRecords});

  RegisterNative(jni, {kHandlerWrapperClass, "fixActivityContext",
                       "(Ljava/lang/Object;Ljava/lang/Object;)V",
                       (void*)&Native_FixActivityContext});

  RegisterNative(
      jni, {kHandlerWrapperClass, "updateApplicationInfo",
            "(Ljava/lang/Object;)V", (void*)&Native_UpdateApplicationInfo});

  if (overlay_swap) {
    RegisterNative(jni, {kHandlerWrapperClass, "logUnhandledException",
                         "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
                         (void*)&Native_LogUnhandledException});
  }

  // Register utility methods.
  RegisterNative(jni,
                 {kDexUtilityClass, "makeInMemoryDexElements",
                  "([Ljava/nio/ByteBuffer;Ljava/util/List;)[Ljava/lang/Object;",
                  (void*)&Native_MakeInMemoryDexElements});

  RegisterNative(jni, {kPhaseClass, "start", "(Ljava/lang/String;)V",
                       (void*)&Native_Phase_Start});
  RegisterNative(jni, {kPhaseClass, "end", "()V", (void*)&Native_Phase_End});

  return true;
}

}  // namespace deploy
