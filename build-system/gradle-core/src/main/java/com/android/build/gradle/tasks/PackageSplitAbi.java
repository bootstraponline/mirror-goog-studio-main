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

package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.signing.SigningConfigProvider;
import com.android.build.gradle.internal.signing.SigningConfigProviderParams;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTaskKt;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.BuildException;

/** Package a abi dimension specific split APK */
public abstract class PackageSplitAbi extends NonIncrementalTask {

    private boolean jniDebuggable;

    private SigningConfigProvider signingConfig;

    private FileCollection jniFolders;

    private AndroidVersion minSdkVersion;

    private File incrementalDir;

    private Collection<String> aaptOptionsNoCompress;

    private Set<String> splits;

    private String createdBy;

    @InputFiles
    public abstract DirectoryProperty getProcessedAbiResources();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    @Input
    public boolean isJniDebuggable() {
        return jniDebuggable;
    }

    @Nested
    public SigningConfigProvider getSigningConfig() {
        return signingConfig;
    }

    @InputFiles
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    @Input
    public int getMinSdkVersion() {
        return minSdkVersion.getFeatureLevel();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    @Input
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    protected void doTaskAction() throws IOException {
        FileUtils.cleanOutputDir(incrementalDir);

        ExistingBuildElements.from(
                        InternalArtifactType.ABI_PROCESSED_SPLIT_RES, getProcessedAbiResources())
                .transform(
                        getWorkerFacadeWithWorkers(),
                        PackageSplitAbiTransformRunnable.class,
                        (apkInfo, input) ->
                                new PackageSplitAbiTransformParams(apkInfo, input, this))
                .into(
                        InternalArtifactType.ABI_PACKAGED_SPLIT,
                        getOutputDirectory().get().getAsFile());
    }

    private static class PackageSplitAbiTransformRunnable extends BuildElementsTransformRunnable {

        @Inject
        public PackageSplitAbiTransformRunnable(@NonNull PackageSplitAbiTransformParams params) {
            super(params);
        }

        @Override
        public void run() {
            PackageSplitAbiTransformParams params = (PackageSplitAbiTransformParams) getParams();
            try (IncrementalPackager pkg =
                    new IncrementalPackagerBuilder(IncrementalPackagerBuilder.ApkFormat.FILE)
                            .withOutputFile(params.getOutput())
                            .withSigning(params.signingConfig.resolve(), params.minSdkVersion)
                            .withCreatedBy(params.createdBy)
                            // .withManifest(manifest)
                            .withAaptOptionsNoCompress(params.aaptOptionsNoCompress)
                            .withIntermediateDir(params.incrementalDir)
                            .withDebuggableBuild(params.isJniDebuggable)
                            .withJniDebuggableBuild(params.isJniDebuggable)
                            .withAcceptedAbis(ImmutableSet.of(params.apkInfo.getFilterName()))
                            .withApkCreatorType(ApkCreatorType.APK_Z_FILE_CREATOR)
                            .withChangedNativeLibs(
                                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                                            params.jniFolders))
                            .withChangedAndroidResources(
                                    IncrementalRelativeFileSets.fromZip(params.input))
                            .build()) {
                pkg.updateFiles();
            } catch (IOException e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static class PackageSplitAbiTransformParams extends BuildElementsTransformParams {
        private final File input;
        private final ApkData apkInfo;
        private final File output;
        private final File incrementalDir;
        private final SigningConfigProviderParams signingConfig;
        private final String createdBy;
        private final Collection<String> aaptOptionsNoCompress;
        private final Set<File> jniFolders;
        private final boolean isJniDebuggable;
        private final int minSdkVersion;

        PackageSplitAbiTransformParams(ApkData apkInfo, File input, PackageSplitAbi task) {
            this.apkInfo = apkInfo;
            this.input = input;
            output =
                    new File(
                            task.getOutputDirectory().get().getAsFile(),
                            getApkName(
                                    apkInfo,
                                    (String)
                                            task.getProject()
                                                    .getProperties()
                                                    .get("archivesBaseName"),
                                    task.signingConfig != null));
            incrementalDir = task.incrementalDir;
            signingConfig = task.signingConfig.convertToParams();
            createdBy = task.getCreatedBy();
            aaptOptionsNoCompress = task.aaptOptionsNoCompress;
            jniFolders = task.getJniFolders().getFiles();
            isJniDebuggable = task.jniDebuggable;
            minSdkVersion = task.getMinSdkVersion();
        }

        @NonNull
        @Override
        public File getOutput() {
            return output;
        }
    }

    private static String getApkName(
            final ApkData apkData, String archivesBaseName, boolean isSigned) {
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName + (isSigned ? "" : "-unsigned") + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<PackageSplitAbi> {

        private final boolean packageCustomClassDependencies;

        public CreationAction(VariantScope scope, boolean packageCustomClassDependencies) {
            super(scope);
            this.packageCustomClassDependencies = packageCustomClassDependencies;
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("package", "SplitAbi");
        }

        @Override
        @NonNull
        public Class<PackageSplitAbi> getType() {
            return PackageSplitAbi.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends PackageSplitAbi> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageSplitAbiTask(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.ABI_PACKAGED_SPLIT,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            PackageSplitAbi::getOutputDirectory,
                            "out");
        }

        @Override
        public void configure(@NonNull PackageSplitAbi task) {
            super.configure(task);
            VariantScope scope = getVariantScope();
            final GlobalScope globalScope = scope.getGlobalScope();

            VariantConfiguration config = scope.getVariantConfiguration();
            scope.getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.ABI_PROCESSED_SPLIT_RES,
                            task.getProcessedAbiResources());
            task.signingConfig = SigningConfigProvider.create(scope);
            task.minSdkVersion = config.getMinSdkVersion();
            task.incrementalDir = scope.getIncrementalDir(task.getName());

            task.aaptOptionsNoCompress =
                    globalScope.getExtension().getAaptOptions().getNoCompress();
            task.jniDebuggable = config.getBuildType().isJniDebuggable();

            task.jniFolders =
                    PerModuleBundleTaskKt.getNativeLibsFiles(scope, packageCustomClassDependencies);
            task.jniDebuggable = config.getBuildType().isJniDebuggable();
            task.splits = scope.getVariantData().getFilters(OutputFile.FilterType.ABI);

            task.createdBy = globalScope.getCreatedBy();

            MutableTaskContainer taskContainer = scope.getTaskContainer();

            if (taskContainer.getExternalNativeBuildTask() != null) {
                task.dependsOn(taskContainer.getExternalNativeBuildTask());
            }
        }
    }
}
