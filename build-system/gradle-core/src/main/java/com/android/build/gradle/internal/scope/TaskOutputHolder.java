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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import java.io.File;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * a class that can hold task outputs from the build.
 */
public interface TaskOutputHolder {

    /**
     * Represents a type of output.
     */
    interface OutputType { }

    /**
     * A type of output generated by a task.
     */
    enum TaskOutputType implements OutputType {
        JAVAC,
        JAVA_RES,
        MERGED_RES,
        MERGED_ASSETS,
        PACKAGED_RES,

        APK_METADATA,

        MOCKABLE_JAR
    }

    /**
     * a Type of output that serves as an anchor for multiple tasks.
     *
     * This is used when a single task consumes outputs (of the same type) coming from different
     * tasks, especially if the number of tasks generating this is be dynamic (either because
     * some tasks are optional based on some parameters or if the API allows for user-added
     * tasks generating the same content.)
     *
     * This allows the consuming task to simply consume a single file collection rather than
     * have to deal with all the different tasks generating the content.
     */
    enum AnchorOutputType implements OutputType {
        GENERATED_RES, GENERATED_SRC
    }

    /**
     * Returns a {@link FileCollection} that contains the requested output type.
     *
     * The collection can be used both as a collection of files and as task dependency to ensure
     * the producers of the files will run before the task consuming them.
     *
     * @param outputType the type of output
     * @return a FileCollection.
     */
    @NonNull
    FileCollection getOutputs(@NonNull OutputType outputType);

    /**
     * Tests whether or not the scope contains an output {@link FileCollection} of the requested
     * output type.
     *
     * @param outputType the type of output
     * @return true if the scope contains such an output, false otherwise.
     */
    boolean hasOutput(@NonNull OutputType outputType);

    /**
     * Adds a new Task output.
     *
     * <p>To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param file the output file
     * @param taskName the name of the task that generates the output file.
     * @return the {@link ConfigurableFileCollection} that contains both the file and the task
     *     dependency.
     */
    ConfigurableFileCollection addTaskOutput(
            @NonNull TaskOutputType outputType, @NonNull File file, @NonNull String taskName);

    /**
     * Adds a new Task output.
     *
     * To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param fileCollection a collection containing file and dependency information.
     */
    void addTaskOutput(
            @NonNull TaskOutputType outputType,
            @NonNull FileCollection fileCollection);

    /**
     * Creates a new anchor output.
     *
     * Once this anchor is created, tasks can add their output (and dependency information) to it.
     *
     * @param outputType the type of the output
     * @return the FileCollection that was created.
     */
    @NonNull
    FileCollection createAnchorOutput(@NonNull AnchorOutputType outputType);

    /**
     * Adds a new output to an anchor output.
     *
     * To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param file the output file
     * @param taskName the name of the task that generates the output file.
     */
    void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull File file,
            @NonNull String taskName);

    /**
     * Adds a new output to an anchor output.
     *
     * To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param fileCollection a collection containing file and dependency information.
     */
    void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull FileCollection fileCollection);

}
