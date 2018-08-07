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

import static com.android.sdklib.BuildToolInfo.PathId.SPLIT_SELECT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.LazyTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.internal.InstallUtils;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConfigProviderImpl;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
public class InstallVariantTask extends AndroidBuilderTask {

    private Supplier<File> adbExe;
    private Supplier<File> splitSelectExe;

    private ProcessExecutor processExecutor;

    private String projectName;

    private int timeOutInMs = 0;

    private Collection<String> installOptions;

    private BuildableArtifact apkDirectory;

    private BaseVariantData variantData;

    public InstallVariantTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Install task is always run.");
            return false;
        });
    }

    @TaskAction
    public void install() throws DeviceException, ProcessException {
        final ILogger iLogger = getILogger();
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(adbExe.get(),
                getTimeOutInMs(),
                iLogger);
        deviceProvider.init();

        try {
            BaseVariantData variantData = getVariantData();
            GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            List<OutputFile> outputs =
                    ImmutableList.copyOf(
                            ExistingBuildElements.from(
                                    InternalArtifactType.APK,
                                    BuildableArtifactUtil.singleFile(apkDirectory)));

            install(
                    getProjectName(),
                    variantConfig.getFullName(),
                    deviceProvider,
                    variantConfig.getMinSdkVersion(),
                    getProcessExecutor(),
                    getSplitSelectExe(),
                    outputs,
                    variantConfig.getSupportedAbis(),
                    getInstallOptions(),
                    getTimeOutInMs(),
                    getLogger());
        } finally {
            deviceProvider.terminate();
        }
    }

    static void install(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull DeviceProvider deviceProvider,
            @NonNull AndroidVersion minSkdVersion,
            @NonNull ProcessExecutor processExecutor,
            @NonNull File splitSelectExe,
            @NonNull List<OutputFile> outputs,
            @Nullable Set<String> supportedAbis,
            @NonNull Collection<String> installOptions,
            int timeOutInMs,
            @NonNull Logger logger)
            throws DeviceException, ProcessException {
        ILogger iLogger = new LoggerWrapper(logger);
        int successfulInstallCount = 0;
        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        for (final DeviceConnector device : devices) {
            if (InstallUtils.checkDeviceApiLevel(
                    device, minSkdVersion, iLogger, projectName, variantName)) {
                // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
                final List<File> apkFiles =
                        SplitOutputMatcher.computeBestOutput(
                                processExecutor,
                                splitSelectExe,
                                new DeviceConfigProviderImpl(device),
                                outputs,
                                supportedAbis);

                if (apkFiles.isEmpty()) {
                    logger.lifecycle(
                            "Skipping device '{}' for '{}:{}': Could not find build of variant "
                                    + "which supports density {} and an ABI in {}",
                            device.getName(),
                            projectName,
                            variantName,
                            device.getDensity(),
                            Joiner.on(", ").join(device.getAbis()));
                } else {
                    logger.lifecycle(
                            "Installing APK '{}' on '{}' for {}:{}",
                            FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                            device.getName(),
                            projectName,
                            variantName);

                    final Collection<String> extraArgs =
                            MoreObjects.firstNonNull(installOptions, ImmutableList.<String>of());

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

    @InputFile
    public File getAdbExe() {
        return adbExe.get();
    }

    @InputFile
    @Optional
    public File getSplitSelectExe() {
        return splitSelectExe.get();
    }

    public ProcessExecutor getProcessExecutor() {
        return processExecutor;
    }

    public void setProcessExecutor(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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
    public BuildableArtifact getApkDirectory() {
        return apkDirectory;
    }

    public void setApkDirectory(BuildableArtifact apkDirectory) {
        this.apkDirectory = apkDirectory;
    }

    public BaseVariantData getVariantData() {
        return variantData;
    }

    public void setVariantData(BaseVariantData variantData) {
        this.variantData = variantData;
    }

    public static class CreationAction extends LazyTaskCreationAction<InstallVariantTask> {

        private final VariantScope scope;

        public CreationAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("install");
        }

        @NonNull
        @Override
        public Class<InstallVariantTask> getType() {
            return InstallVariantTask.class;
        }

        @Override
        public void configure(@NonNull InstallVariantTask installTask) {
            installTask.setDescription(
                    "Installs the " + scope.getVariantData().getDescription() + ".");
            installTask.setVariantName(scope.getVariantConfiguration().getFullName());
            installTask.setGroup(TaskManager.INSTALL_GROUP);
            installTask.setProjectName(scope.getGlobalScope().getProject().getName());
            installTask.setVariantData(scope.getVariantData());
            installTask.setApkDirectory(
                    scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.APK));
            installTask.setTimeOutInMs(
                    scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());
            installTask.setInstallOptions(
                    scope.getGlobalScope().getExtension().getAdbOptions().getInstallOptions());
            installTask.setProcessExecutor(
                    scope.getGlobalScope().getAndroidBuilder().getProcessExecutor());
            installTask.adbExe = TaskInputHelper.memoize(() -> {
                final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                return (info == null ? null : info.getAdb());
            });
            installTask.splitSelectExe = TaskInputHelper.memoize(() -> {
                // SDK is loaded somewhat dynamically, plus we don't want to do all this logic
                // if the task is not going to run, so use a supplier.
                final TargetInfo info =
                        scope.getGlobalScope().getAndroidBuilder().getTargetInfo();
                String path = info == null ? null : info.getBuildTools().getPath(SPLIT_SELECT);
                if (path != null) {
                    File splitSelectExe = new File(path);
                    return splitSelectExe.exists() ? splitSelectExe : null;
                } else {
                    return null;
                }
            });
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends InstallVariantTask> taskProvider) {
            super.handleProvider(taskProvider);
            scope.getTaskContainer().setInstallTask(taskProvider);
        }
    }
}
