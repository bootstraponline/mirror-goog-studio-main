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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FN_INTERMEDIATE_FULL_JAR
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption.USE_ZIPFLINGER_FOR_JAR_MERGING
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.util.function.Predicate

/** Task to merge the res/classes intermediate jars from a library into a single one  */
@CacheableTask
abstract class ZipMergingTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryInputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val javaResInputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @VisibleForTesting
    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        internal set

    public override fun doTaskAction() {
        val destinationFile = outputFile.get().asFile
        FileUtils.cleanOutputDir(destinationFile.parentFile)
        val usedNamesPredicate = object:Predicate<String> {
            val usedNames = mutableSetOf<String>()

            override fun test(t: String): Boolean {
                return usedNames.add(t)
            }
        }

        JarCreatorFactory.make(
            destinationFile.toPath(),
            usedNamesPredicate,
            jarCreatorType
        ).use {
            val lib = libraryInputFile.get().asFile
            if (lib.exists()) {
                it.addJar(lib.toPath())
            }
            if (javaResInputFile.isPresent) {
                it.addJar(javaResInputFile.get().asFile.toPath())
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ZipMergingTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("createFullJar")
        override val type: Class<ZipMergingTask>
            get() = ZipMergingTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ZipMergingTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.FULL_JAR,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                ZipMergingTask::outputFile,
                FN_INTERMEDIATE_FULL_JAR
                )
        }

        override fun configure(task: ZipMergingTask) {
            super.configure(task)

            val buildArtifacts = variantScope.artifacts
            buildArtifacts.setTaskInputToFinalProduct(InternalArtifactType.RUNTIME_LIBRARY_CLASSES, task.libraryInputFile)
            buildArtifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LIBRARY_JAVA_RES,
                task.javaResInputFile
            )
            task.jarCreatorType =
                if (variantScope.globalScope.projectOptions.get(USE_ZIPFLINGER_FOR_JAR_MERGING)) {
                    JarCreatorType.JAR_FLINGER
                } else {
                    JarCreatorType.JAR_MERGER
                }
        }
    }
}
