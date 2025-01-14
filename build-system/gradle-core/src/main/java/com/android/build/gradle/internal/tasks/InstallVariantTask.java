/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS;
import static com.android.build.gradle.internal.tasks.BundleInstallUtils.extractApkFilesBypassingBundleTool;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.dsl.Installation;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.api.variant.impl.VariantApiExtensionsKt;
import com.android.build.gradle.internal.BuildToolsExecutableInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.internal.InstallUtils;
import com.android.builder.testing.api.DeviceConfigProviderImpl;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEPLOYMENT)
public abstract class InstallVariantTask extends NonIncrementalTask {

    private int timeOutInMs = 0;

    private Collection<String> installOptions;

    private String variantName;
    private Set<String> supportedAbis;
    private AndroidVersion minSdkVersion;

    @Inject
    public InstallVariantTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Install task is always run.");
            return false;
        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException {
        final ILogger iLogger = new LoggerWrapper(getLogger());
        DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        getBuildTools().adbExecutable(),
                        getTimeOutInMs(),
                        iLogger,
                        System.getenv("ANDROID_SERIAL"));
        deviceProvider.use(
                () -> {
                    install(
                            getProjectPath().get(),
                            variantName,
                            deviceProvider,
                            minSdkVersion,
                            getApkDirectory().get(),
                            getPrivacySandboxSdksApksFiles().getFiles(),
                            supportedAbis,
                            getInstallOptions(),
                            getTimeOutInMs(),
                            getLogger());
                    return null;
                });
    }

    static void install(
            @NonNull String projectPath,
            @NonNull String variantName,
            @NonNull DeviceProvider deviceProvider,
            @NonNull AndroidVersion minSdkVersion,
            @NonNull Directory apkDirectory,
            @NonNull Set<File> privacySandboxSdksApksFiles,
            @NonNull Set<String> supportedAbis,
            @NonNull Collection<String> installOptions,
            int timeOutInMs,
            @NonNull Logger logger)
            throws DeviceException {
        install(
                projectPath,
                variantName,
                deviceProvider,
                minSdkVersion,
                new BuiltArtifactsLoaderImpl().load(apkDirectory),
                privacySandboxSdksApksFiles,
                supportedAbis,
                installOptions,
                timeOutInMs,
                logger);
    }

    private static void install(
            @NonNull String projectPath,
            @NonNull String variantName,
            @NonNull DeviceProvider deviceProvider,
            @NonNull AndroidVersion minSkdVersion,
            @NonNull BuiltArtifactsImpl builtArtifacts,
            @NonNull Set<File> privacySandboxSdksApksFiles,
            @NonNull Set<String> supportedAbis,
            @NonNull Collection<String> installOptions,
            int timeOutInMs,
            @NonNull Logger logger)
            throws DeviceException {
        ILogger iLogger = new LoggerWrapper(logger);
        int successfulInstallCount = 0;
        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        for (final DeviceConnector device : devices) {
            // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
            if (InstallUtils.checkDeviceApiLevel(
                    device, minSkdVersion, iLogger, projectPath, variantName)) {
                final Collection<String> extraArgs =
                        MoreObjects.firstNonNull(installOptions, ImmutableList.of());
                DeviceConfigProviderImpl deviceConfigProvider =
                        new DeviceConfigProviderImpl(device);
                privacySandboxSdksApksFiles.forEach(
                        file -> {
                            List<File> apkFiles =
                                    extractApkFilesBypassingBundleTool(file.toPath()).stream()
                                            .map(Path::toFile)
                                            .collect(Collectors.toUnmodifiableList());
                            try {
                                device.installPackages(apkFiles, extraArgs, timeOutInMs, iLogger);
                            } catch (DeviceException e) {
                                logger.error(
                                        String.format(
                                                "Failed to install privacy sandbox SDK APKs from %s",
                                                file.toPath()),
                                        e);
                            }
                        });
                List<File> apkFiles =
                        BuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(
                                deviceConfigProvider, builtArtifacts, supportedAbis);

                if (apkFiles.isEmpty()) {
                    logger.lifecycle(
                            "Skipping device '{}' for '{}:{}': Could not find build of variant "
                                    + "which supports density {} and an ABI in {}",
                            device.getName(),
                            projectPath,
                            variantName,
                            device.getDensity(),
                            Joiner.on(", ").join(device.getAbis()));
                } else {
                    logger.lifecycle(
                            "Installing APK '{}' on '{}' for {}:{}",
                            FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                            device.getName(),
                            projectPath,
                            variantName);


                    if (apkFiles.size() > 1) {
                        device.installPackages(apkFiles, extraArgs, timeOutInMs, iLogger);
                        successfulInstallCount++;
                    } else {
                        device.installPackage(apkFiles.get(0), extraArgs, timeOutInMs, iLogger);
                        successfulInstallCount++;
                    }
                }
            }
        }

        if (successfulInstallCount == 0) {
            throw new GradleException("Failed to install on any devices.");
        } else {
            logger.quiet(
                    "Installed on {} {}.",
                    successfulInstallCount,
                    successfulInstallCount == 1 ? "device" : "devices");
        }
    }

    @Input
    public int getTimeOutInMs() {
        return timeOutInMs;
    }

    public void setTimeOutInMs(int timeOutInMs) {
        this.timeOutInMs = timeOutInMs;
    }

    @Input
    @Optional
    public Collection<String> getInstallOptions() {
        return installOptions;
    }

    public void setInstallOptions(Collection<String> installOptions) {
        this.installOptions = installOptions;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract ConfigurableFileCollection getPrivacySandboxSdksApksFiles();

    @Nested
    public abstract BuildToolsExecutableInput getBuildTools();

    public static class CreationAction
            extends VariantTaskCreationAction<InstallVariantTask, ApkCreationConfig> {

        public CreationAction(@NonNull ApkCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("install");
        }

        @NonNull
        @Override
        public Class<InstallVariantTask> getType() {
            return InstallVariantTask.class;
        }

        @Override
        public void configure(@NonNull InstallVariantTask task) {
            super.configure(task);

            task.variantName = creationConfig.getBaseName();

            if (creationConfig.getNativeBuildCreationConfig() == null) {
                task.supportedAbis = Collections.emptySet();
            } else {
                task.supportedAbis =
                        creationConfig.getNativeBuildCreationConfig().getSupportedAbis();
            }

            task.minSdkVersion =
                    VariantApiExtensionsKt.toSharedAndroidVersion(
                            creationConfig.getMinSdkVersion());

            task.setDescription("Installs the " + creationConfig.getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                            SingleArtifact.APK.INSTANCE, task.getApkDirectory());
            if (creationConfig
                    .getServices()
                    .getProjectOptions()
                    .get(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT)) {
                task.getPrivacySandboxSdksApksFiles()
                        .setFrom(
                                creationConfig
                                        .getVariantDependencies()
                                        .getArtifactFileCollection(
                                                AndroidArtifacts.ConsumedConfigType
                                                        .RUNTIME_CLASSPATH,
                                                AndroidArtifacts.ArtifactScope.ALL,
                                                ANDROID_PRIVACY_SANDBOX_SDK_APKS));
            }
            task.getPrivacySandboxSdksApksFiles().disallowChanges();

            Installation installationOptions = creationConfig.getGlobal().getInstallationOptions();
            task.setTimeOutInMs(installationOptions.getTimeOutInMs());
            task.setInstallOptions(installationOptions.getInstallOptions());

            SdkComponentsKt.initialize(task.getBuildTools(), creationConfig);
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<InstallVariantTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getTaskContainer().setInstallTask(taskProvider);
        }
    }
}
