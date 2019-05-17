/*
 * Copyright (C) 2019 The Android Open Source Project
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
 */

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.cxx.configure.defaultCmakeVersion
import com.android.build.gradle.internal.cxx.settings.Environment.*

/**
 * Define built-in macros for CMakeSettings.json. Built-in means they're defined by the host
 * (Android Studio) and their names can be known in advance.
 *
 * Examples of macros that aren't here:
 * - User defined macros in their own CMakeSettings.json environments.
 * - Bash or cmd.exe environment variables like $HOME.
 */
enum class Macro(
    // A user readable description of this variable suitable for inclusion in errors and warnings
    val description: String,
    // The environment where the value of this macro would naturally come from. For example, NDK
    // means that the current Android NDK is entity that would naturally produce this information.
    val environment: Environment,
    // The name that is used to reference this macro from within CMakeSettings.json. For example,
    // in ${ndk.minPlatform} the tag is "minPlatform".
    val tag: String,
    // An example value for this kind of macro. Suitable for including in error messages and
    // warnings about this macro.
    val example: String) {

    MIN_PLATFORM(
        description = "The minimum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "minPlatform",
        example = "16"),
    MAX_PLATFORM(
        description = "The maximum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "maxPlatform",
        example = "29"),
    PLATFORM_SYSTEM_VERSION(
        description = "The currently targeted Android system version, suitable for passing to " +
                "CMake in CMAKE_SYSTEM_VERSION.",
        environment = NDK_PLATFORM,
        tag = "systemVersion",
        example = "19"),
    PLATFORM(
        description = "The currently targeted Android platform string, that can be passed to " +
                "CMake in ANDROID_PLATFORM.",
        environment = NDK_PLATFORM,
        tag = "platform",
        example = "android-19"),
    PLATFORM_CODE(
        description = "The currently targeted Android platform code name.",
        environment = NDK_PLATFORM,
        tag = "platformCode",
        example = "K"),
    ABI_BITNESS(
        description = "The bitness of the targeted ABI.",
        environment = NDK_ABI,
        tag = "abiBitness",
        example = "64"),
    ABI_IS_64_BITS(
        description = "Whether the targeted ABI is 64-bits.",
        environment = NDK_ABI,
        tag = "abiIs64Bits",
        example = "1"),
    ABI_IS_DEFAULT(
        description = "Whether the targeted ABI is a default ABI in the current Android NDK.",
        environment = NDK_ABI,
        tag = "abiIsDefault",
        example = "1"),
    ABI_IS_DEPRECATED(
        description = "True if the targeted ABI is deprecated in the current Android NDK.",
        environment = NDK_ABI,
        tag = "abiIsDeprecated",
        example = "0"),
    ABI(
        description = "Currently targeted ABI.",
        environment = GRADLE,
        tag = "abi",
        example = "x86_64"),
    SDK_DIR(
        description = "Folder of the current Android SDK.",
        environment = GRADLE,
        tag = "sdkDir",
        example = "\$HOME/Library/Android/sdk"),
    NDK_DIR(
        description = "Folder of the current Android NDK.",
        environment = NDK,
        tag = "dir",
        example = "${SDK_DIR.ref}/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION"),
    NDK_CMAKE_TOOLCHAIN(
        description = "Path to the current Android NDK's CMake toolchain.",
        environment = NDK,
        tag = "toolchain",
        example = "${NDK_DIR.ref}/build/cmake/android.toolchain.cmake"),
    CMAKE_EXE(
        description = "Path to CMake executable.",
        environment = NDK,
        tag = "cmakeExecutable",
        example = "${SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/cmake"),
    NINJA_EXE(
        description = "Path to Ninja executable if one was found.",
        environment = NDK,
        tag = "ninjaExecutable",
        example = "${SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/ninja"),
    NDK_VERSION(
        description = "Version of NDK.",
        environment = NDK,
        tag = "version",
        example = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION),
    NDK_VERSION_MAJOR(
        description = "Version number major part.",
        environment = NDK,
        tag = "versionMajor",
        example = "19"),
    NDK_VERSION_MINOR(
        description = "Version number minor part.",
        environment = NDK,
        tag = "versionMinor",
        example = "2"),
    GRADLE_PROJECT_DIR(
        description = "Folder of the gradle root project build.gradle.",
        environment = GRADLE,
        tag = "projectDir",
        example = "\$PROJECTS/MyProject/Source/Android"),
    GRADLE_MODULE_DIR(
        description = "Folder of the module level build.gradle.",
        environment = GRADLE,
        tag = "moduleDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1"),
    GRADLE_VARIANT_NAME(
        description = "Name of the gradle variant.",
        environment = GRADLE,
        tag = "variantName",
        example = "debug"),
    GRADLE_MODULE_NAME(
        description = "Name of the gradle module.",
        environment = GRADLE,
        tag = "moduleName",
        example = "app1"),
    GRADLE_BUILD_ROOT(
        description = "The default CMake build root that gradle uses.",
        environment = GRADLE,
        tag = "buildRoot",
        example = "${GRADLE_MODULE_DIR.ref}/.cxx/cmake/debug/x86_64"),
    GRADLE_LIBRARY_OUTPUT_DIRECTORY(
        description = "The default CMake CMAKE_LIBRARY_OUTPUT_DIRECTORY that gradle uses.",
        environment = GRADLE,
        tag = "libraryOutputDir",
        example = "${GRADLE_MODULE_DIR.ref}/build/intermediates/cmake/debug/obj/x86_64"),
    GRADLE_CMAKE_BUILD_TYPE(
        description = "The CMAKE_BUILD_TYPE derived from the suffix of gradle variant name. " +
                "May be Debug, Release, RelWithDebInfo, or MinSizeRel.",
        environment = GRADLE,
        tag = "buildType",
        example = "Debug"),
    BUILT_IN_THIS_FILE(
        description = "Path to this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFile",
        example = "\$PROJECTS/MyProject/CMakeSettings.json"),
    BUILT_IN_THIS_FILE_DIR(
        description = "Folder of this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFileDir",
        example = "\$PROJECTS/MyProject"),
    BUILT_IN_WORKSPACE_ROOT(
        description = "Folder of the project level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "workspaceRoot",
        example = "\$PROJECTS/MyProject/Source/Android"),
    BUILT_IN_PROJECT_DIR(
        description = "Folder of the module level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "projectDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1"),
    GRADLE_IS_HOSTING(
        description = "True if Android Gradle Plugin is hosting this CMakeSettings.json.",
        environment = NDK_EXPOSED_BY_HOST,
        tag = "androidGradleIsHosting",
        example = "1");

    /**
     * The literal value of this macro in CMakeSettings.json. For example, "${ndk.minPlatform}".
     * Macros in the namespace "env" are reduced to just the name. For example, "${thisFile}".
     */
    val ref =
        if (environment.namespace == "env") "\${$tag}"
        else "\${${environment.namespace}.$tag}"

    /**
     * The namespace-qualified name of this macro.
     */
    val qualifiedName get() = "${environment.namespace}.$tag"

    companion object {
        /**
         * Look up the [Macro] enum for [name]. If [name] doesn't have a namespace then 'env' is
         * used. If [name] cannot be found then null is returned.
         */
        @JvmStatic
        fun lookup(name: String): Macro? {
            val qualifiedName =
                when {
                    name.contains(".") -> name
                    else -> "env.$name"
                }
            return values().singleOrNull { it.qualifiedName == qualifiedName }
        }
    }
}

/**
 * The environment that a macro exists within.
 *
 * In CMakeSettings.json environments may inherit other environments. Inheritance means values from
 * parent environments exist in the child environment and can be replaced in the child environment.
 */
enum class Environment(
    // The name of this environment so that it can be referenced in CMakeSettings.json
    // inheritEnvironments lists.
    val environment : String,
    // The namespace of macros exposed by this environment.
    val namespace : String,
    // Environments that this environment inherits from.
    vararg inheritEnvironments : Environment) {
    /**
     * Environment for holding the built-in macros exposed by Microsoft. For example, ${thisFile}.
     * Even though the names of the macros are defined by Microsoft, the values in this particular
     * environment come from us. Therefore, the name of the environment contains "from-android-ndk".
     */
    MICROSOFT_BUILT_IN("microsoft-built-ins-from-android-ndk", "env"),
    /**
     * Environment that would be produced by the current Android NDK.
     */
    NDK("android-ndk", "ndk"),
    /**
     * Environment produced by the NDK for an explicit platform ID.
     */
    NDK_PLATFORM("android-ndk-platform-\${ndk.systemVersion}", "ndk"),
    /**
     * Environment produced by the NDK for particular ABI.
     */
    NDK_ABI("android-ndk-abi-\${gradle.abi}", "ndk"),
    /**
     * Environment for macros exposed by Android Gradle Plugin. For example, ${gradle.ABI} is the
     * name of the ABI that is currently being built.
     */
    GRADLE("android-gradle", "ndk"),
    /**
     * This is a special purpose environment that inherits from the others in the defined order.
     *
     * In this ordering, earlier environment values can be replaced by later environment values.
     *
     * NDK is first (for global NDK values), then the more specific NDK environments NDK_PLATFORM
     * and NDK_ABI. Next GRADLE so that Android Gradle Plugin can replace NDK values if needed.
     * Lastly, there is MICROSOFT_BUILT_IN which shouldn't be replaceable.
     *
     * It exposes a single macro ${ndk.androidGradleIsHosting} that can be used to detect whether
     * CMakeSettings.json is being evaluated by Android Gradle Plugin.
     */
    NDK_EXPOSED_BY_HOST("ndk", "ndk", NDK, NDK_PLATFORM, NDK_ABI,
        GRADLE, MICROSOFT_BUILT_IN);

    /**
     * Environments that this environment inherits from.
     */
    val inheritEnvironments = inheritEnvironments.toList()
}
