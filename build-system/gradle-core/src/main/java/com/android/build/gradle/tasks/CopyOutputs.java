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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Copy the location our various tasks outputs into a single location.
 *
 * <p>This is useful when having configuration or feature splits which are located in different
 * folders since they are produced by different tasks.
 */
public class CopyOutputs extends AndroidVariantTask {

    FileCollection fullApks;
    FileCollection abiSplits;
    FileCollection resourcesSplits;
    File destinationDir;
    OutputScope outputScope;

    @OutputDirectory
    public java.io.File getDestinationDir() {
        return destinationDir;
    }

    @InputFiles
    public FileCollection getFullApks() {
        return fullApks;
    }

    @InputFiles
    @Optional
    public FileCollection getAbiSplits() {
        return abiSplits;
    }

    @InputFiles
    @Optional
    public FileCollection getResourcesSplits() {
        return resourcesSplits;
    }

    // FIX ME : add incrementality
    @TaskAction
    protected void copy() throws IOException {

        FileUtils.cleanOutputDir(getDestinationDir());
        // TODO : parallelize at this level.
        parallelCopy(TaskOutputType.FULL_APK, fullApks);
        parallelCopy(TaskOutputType.ABI_PACKAGED_SPLIT, abiSplits);
        parallelCopy(TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT, resourcesSplits);
        // now save the merged list.
        outputScope.save(TaskOutputType.APK, getDestinationDir());
    }

    private void parallelCopy(TaskOutputType inputType, FileCollection inputs) {
        outputScope.parallelForEachOutput(
                BuildOutputs.load(inputType, inputs),
                inputType,
                TaskOutputType.APK,
                (split, output) -> {
                    if (output != null) {
                        File destination = new File(getDestinationDir(), output.getName());
                        FileUtils.copyFile(output, destination);
                        return destination;
                    }
                    return null;
                });
    }

    public static class ConfigAction implements TaskConfigAction<CopyOutputs> {

        private final VariantScope variantScope;
        private final File outputDirectory;

        public ConfigAction(VariantScope variantScope, File outputDirectory) {
            this.variantScope = variantScope;
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("copyOutputs");
        }

        @NonNull
        @Override
        public Class<CopyOutputs> getType() {
            return CopyOutputs.class;
        }

        @Override
        public void execute(@NonNull CopyOutputs task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.outputScope = variantScope.getOutputScope();
            task.fullApks = variantScope.getOutput(TaskOutputType.FULL_APK);
            Project project = variantScope.getGlobalScope().getProject();
            task.abiSplits =
                    variantScope.hasOutput(TaskOutputType.ABI_PACKAGED_SPLIT)
                            ? variantScope.getOutput(TaskOutputType.ABI_PACKAGED_SPLIT)
                            : project.files();
            task.resourcesSplits =
                    variantScope.hasOutput(TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT)
                            ? variantScope.getOutput(
                                    TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT)
                            : project.files();
            task.destinationDir = outputDirectory;
        }
    }
}
