/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_ZIP;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.Deflater;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

/** Task to bundle a bundle of feature APKs. */
public abstract class BundleInstantApp extends NonIncrementalTask {
    private final WorkerExecutorFacade workers;

    @Inject
    public BundleInstantApp(WorkerExecutor workerExecutor) {
        this.workers =
                Workers.INSTANCE.preferWorkers(getProject().getName(), getPath(), workerExecutor);
    }

    @Override
    protected void doTaskAction() throws IOException {
        // FIXME: Make this task incremental.
        try (WorkerExecutorFacade workers = this.workers) {
            workers.submit(
                    BundleInstantAppRunnable.class,
                    new BundleInstantAppParams(
                            getProject().getName(),
                            getPath(),
                            getBundleDirectory().get().getAsFile(),
                            bundleName,
                            ModuleMetadata.load(applicationId.getSingleFile()).getApplicationId(),
                            new TreeSet<>(apkDirectories.getFiles())));
        }
    }

    @OutputDirectory
    @NonNull
    public abstract DirectoryProperty getBundleDirectory();

    @Input
    @NonNull
    public String getBundleName() {
        return bundleName;
    }

    @InputFiles
    @NonNull
    public FileCollection getApplicationId() {
        return applicationId;
    }

    @InputFiles
    @NonNull
    public FileCollection getApkDirectories() {
        return apkDirectories;
    }

    private String bundleName;
    private FileCollection applicationId;
    private FileCollection apkDirectories;

    public static class CreationAction extends TaskCreationAction<BundleInstantApp> {

        public CreationAction(@NonNull VariantScope scope, @NonNull File bundleDirectory) {
            this.scope = scope;
            this.bundleDirectory = bundleDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package", "InstantAppBundle");
        }

        @NonNull
        @Override
        public Class<BundleInstantApp> getType() {
            return BundleInstantApp.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends BundleInstantApp> taskProvider) {
            super.handleProvider(taskProvider);
            scope.getArtifacts()
                    .producesDir(
                            InternalArtifactType.INSTANTAPP_BUNDLE,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            BundleInstantApp::getBundleDirectory,
                            scope.getGlobalScope()
                                    .getProject()
                                    .getLayout()
                                    .getBuildDirectory()
                                    .dir(bundleDirectory.getAbsolutePath()),
                            "");
        }

        @Override
        public void configure(@NonNull BundleInstantApp bundleInstantApp) {
            bundleInstantApp.setVariantName(scope.getFullVariantName());
            bundleInstantApp.bundleName =
                    scope.getGlobalScope().getProjectBaseName()
                            + "-"
                            + scope.getVariantConfiguration().getBaseName()
                            + DOT_ZIP;
            bundleInstantApp.applicationId =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION);
            bundleInstantApp.apkDirectories =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.APK);
        }

        private final VariantScope scope;
        private final File bundleDirectory;
    }

    private static class BundleInstantAppRunnable implements Runnable {
        private final BundleInstantAppParams params;

        @Inject
        public BundleInstantAppRunnable(BundleInstantAppParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            try {
                FileUtils.mkdirs(params.bundleDirectory);

                File bundleFile = new File(params.bundleDirectory, params.bundleName);
                FileUtils.deleteIfExists(bundleFile);

                ZFileOptions zFileOptions = new ZFileOptions();

                try (ExecutorServiceAdapter executor =
                        Workers.INSTANCE.withThreads(params.projectName, params.taskOwnerName)) {
                    zFileOptions.setCompressor(
                            new DeflateExecutionCompressor(
                                    (compressJob) ->
                                            executor.submit(
                                                    CompressorRunnable.class,
                                                    new CompressorParams(compressJob)),
                                    Deflater.DEFAULT_COMPRESSION));
                    try (ZFile file = ZFile.openReadWrite(bundleFile, zFileOptions)) {
                        for (File apkDirectory : params.apkDirectories) {
                            for (BuildOutput buildOutput :
                                    ExistingBuildElements.from(
                                            InternalArtifactType.APK, apkDirectory)) {
                                File apkFile = buildOutput.getOutputFile();
                                try (FileInputStream fileInputStream =
                                        new FileInputStream(apkFile)) {
                                    file.add(apkFile.getName(), fileInputStream);
                                }
                            }
                        }
                    }
                }

                // Write the json output.
                InstantAppOutputScope instantAppOutputScope =
                        new InstantAppOutputScope(
                                params.applicationId,
                                bundleFile,
                                new ArrayList<>(params.apkDirectories));
                instantAppOutputScope.save(params.bundleDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        static class CompressorRunnable implements Runnable {
            private final Runnable compressJob;

            @Inject
            CompressorRunnable(CompressorParams params) {
                this.compressJob = params.compressJob;
            }

            @Override
            public void run() {
                compressJob.run();
            }
        }

        static class CompressorParams implements Serializable {
            private final Runnable compressJob;

            CompressorParams(Runnable compressJob) {
                this.compressJob = compressJob;
            }
        }
    }

    private static class BundleInstantAppParams implements Serializable {
        @NonNull private final String projectName;
        @NonNull private final String taskOwnerName;
        @NonNull private final File bundleDirectory;
        @NonNull private final String bundleName;
        @NonNull private final String applicationId;
        @NonNull private final Set<File> apkDirectories;

        BundleInstantAppParams(
                @NonNull String projectName,
                @NonNull String taskOwnerName,
                @NonNull File bundleDirectory,
                @NonNull String bundleName,
                @NonNull String applicationId,
                @NonNull Set<File> apkDirectories) {
            this.projectName = projectName;
            this.taskOwnerName = taskOwnerName;
            this.bundleDirectory = bundleDirectory;
            this.bundleName = bundleName;
            this.applicationId = applicationId;
            this.apkDirectories = apkDirectories;
        }
    }
}
