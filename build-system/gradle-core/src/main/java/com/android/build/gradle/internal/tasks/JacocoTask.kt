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
 */
package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.scope.getRegularFiles
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.files.SerializableChange
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.util.EnumMap
import java.util.Objects
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class JacocoTask : NewIncrementalTask() {

    @get:Classpath
    abstract val jacocoAntTaskConfiguration: ConfigurableFileCollection

    @get:Nested
    abstract val jarsWithIdentity: JarsClasspathInputsWithIdentity

    @get:Classpath
    @get:Incremental
    abstract val classesDir: ConfigurableFileCollection

    @get:Input
    abstract val forceOutOfProcess: Property<Boolean>

    @get:OutputDirectory
    abstract val outputForDirs: DirectoryProperty

    @get:OutputDirectory
    abstract val outputForJars: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        processDirectories(inputChanges)
        processJars(inputChanges)
    }

    private fun processDirectories(inputChanges: InputChanges) {
        val changes = inputChanges.getFileChanges(classesDir).toSerializable()
        val filesToProcess: MutableSet<SerializableChange> = HashSet(changes.addedFiles)
        for (removedFile in changes.removedFiles) {
            removeFile(removedFile)
        }
        for (modifiedFile in changes.modifiedFiles) {
            removeFile(modifiedFile)
            filesToProcess.add(modifiedFile)
        }
        val toProcess: MutableMap<Action, MutableList<SerializableChange>> = EnumMap(
            Action::class.java
        )
        for (change in filesToProcess) {
            val action = calculateAction(change.normalizedPath)
            if (action == Action.IGNORE) {
                continue
            }
            val byAction = toProcess.getOrDefault(action, ArrayList())
            byAction.add(change)
            toProcess[action] = byAction
        }
        val workQueue = workQueue
        workQueue.submit(
            InstrumentDirAction::class.java
        ) { params: InstrumentDirAction.Parameters ->
            params.changesToProcess.set(toProcess)
            params.output.set(outputForDirs.asFile)
        }
    }

    private fun processJars(inputChanges: InputChanges) {
        val mappingState = jarsWithIdentity.getMappingState(inputChanges)
        if (mappingState.reprocessAll) {
            try {
                FileUtils.deleteDirectoryContents(outputForJars.get().asFile)
            } catch (ex: IOException) {
                throw UncheckedIOException(ex)
            }
        }
        for ((key, fileInfo) in mappingState.jarsInfo) {
            if (fileInfo.hasChanged) {
                val instrumentedJar = getCorrespondingInstrumentedJar(
                    outputForJars.get().asFile,
                    Objects.requireNonNull(fileInfo.identity)
                )
                try {
                    FileUtils.deleteIfExists(instrumentedJar)
                } catch (ex: IOException) {
                    throw UncheckedIOException(ex)
                }
                val workQueue = workQueue
                workQueue.submit(
                    InstrumentJarAction::class.java
                ) { params: InstrumentJarAction.Parameters ->
                    params.root.set(
                        key
                    )
                    params.output.set(instrumentedJar)
                }
            }
        }
    }

    private fun removeFile(fileToRemove: SerializableChange) {
        val action = calculateAction(fileToRemove.normalizedPath)
        if (action == Action.IGNORE) {
            return
        }
        val outputPath = outputForDirs
            .get()
            .asFile
            .toPath()
            .resolve(fileToRemove.normalizedPath)
        try {
            PathUtils.deleteRecursivelyIfExists(outputPath)
        } catch (ex: IOException) {
            throw UncheckedIOException(ex)
        }
    }

    private val workQueue: WorkQueue
        get() = if (forceOutOfProcess.get()) {
            workerExecutor
                .processIsolation { spec: ProcessWorkerSpec ->
                    spec.classpath.from(
                        jacocoAntTaskConfiguration
                    )
                }
        } else {
            workerExecutor
                .classLoaderIsolation { spec: ClassLoaderWorkerSpec ->
                    spec.classpath.from(
                        jacocoAntTaskConfiguration
                    )
                }
        }

    /** The possible actions which can happen to an input file  */
    enum class Action(vararg patterns: Pattern) {
        /** The file is just copied to the transform output.  */
        COPY(KOTLIN_MODULE_PATTERN),

        /** The file is ignored.  */
        IGNORE,

        /** The file is instrumented and added to the transform output.  */
        INSTRUMENT(CLASS_PATTERN);

        val patterns: ImmutableList<Pattern>

        /**
         * @param patterns Patterns are compared to files' relative paths to determine if they
         * undergo the corresponding action.
         */
        init {
            val builder = ImmutableList.Builder<Pattern>()
            for (pattern in patterns) {
                Preconditions.checkNotNull(pattern)
                builder.add(pattern)
            }
            this.patterns = builder.build()
        }
    }

    /**
     * This action is not a [ProfileAwareWorkAction] as it is submitted in isolation mode
     * which is not supported by [AnalyticsService].
     */
    abstract class InstrumentDirAction : WorkAction<InstrumentDirAction.Parameters> {
        abstract class Parameters : WorkParameters {
            abstract val changesToProcess: MapProperty<Action, MutableList<SerializableChange>>
            abstract val output: Property<File?>
        }

        override fun execute() {
            val inputs = parameters !!.changesToProcess.get()
            val outputDir = parameters !!.output.get()
            val instrumenter = Instrumenter(OfflineInstrumentationAccessGenerator())
            for (toInstrument in inputs.getOrDefault(Action.INSTRUMENT, ImmutableList.of())) {
                logger.info("Instrumenting file: " + toInstrument.file.absolutePath)
                try {
                    Files.asByteSource(toInstrument.file).openBufferedStream().use { inputStream ->
                        val instrumented =
                            instrumenter.instrument(inputStream, toInstrument.toString())
                        val outputFile = File(outputDir, toInstrument.normalizedPath)
                        Files.createParentDirs(outputFile)
                        Files.write(instrumented, outputFile)
                    }
                } catch (e: IOException) {
                    throw UncheckedIOException(
                        "Unable to instrument file with Jacoco: " + toInstrument.file, e
                    )
                }
            }
            for ((file, _, normalizedPath) in inputs.getOrDefault(
                Action.COPY,
                ImmutableList.of()
            )) {
                val outputFile = File(outputDir, normalizedPath)
                try {
                    Files.createParentDirs(outputFile)
                    Files.copy(file, outputFile)
                } catch (e: IOException) {
                    throw UncheckedIOException("Unable to copy file: $file", e)
                }
            }
        }

        companion object {
            private val logger = LoggerWrapper.getLogger(
                InstrumentDirAction::class.java
            )
        }
    }

    abstract class InstrumentJarAction : WorkAction<InstrumentJarAction.Parameters> {
        abstract class Parameters : WorkParameters {
            abstract val root: Property<File?>
            abstract val output: Property<File?>
        }

        override fun execute() {
            val inputJar = parameters.root.get()
            logger.info("Instrumenting jar: " + inputJar?.absolutePath)
            val instrumentedJar = parameters.output.get()
            val instrumenter = Instrumenter(OfflineInstrumentationAccessGenerator())
            try {
                ZipOutputStream(
                    BufferedOutputStream(FileOutputStream(instrumentedJar))
                ).use { outputZip ->
                    ZipFile(inputJar).use { zipFile ->
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val entryName = entry.name
                            val entryAction = calculateAction(entryName)
                            if (entryAction == Action.IGNORE) {
                                continue
                            }
                            val classInputStream = zipFile.getInputStream(entry)
                            var data: ByteArray?
                            data = if (entryAction == Action.INSTRUMENT) {
                                instrumenter.instrument(classInputStream, entryName)
                            } else { // just copy
                                ByteStreams.toByteArray(classInputStream)
                            }
                            val nextEntry = ZipEntry(entryName)
                            // Any negative time value sets ZipEntry's xdostime to DOSTIME_BEFORE_1980
                            // constant.
                            nextEntry.time = - 1L
                            outputZip.putNextEntry(nextEntry)
                            outputZip.write(data)
                            outputZip.closeEntry()
                        }
                    }
                }
            } catch (e: IOException) {
                throw UncheckedIOException(
                    "Unable to instrument file with Jacoco: $inputJar", e
                )
            }
        }

        companion object {
            private val logger = LoggerWrapper.getLogger(
                InstrumentJarAction::class.java
            )
        }
    }

    abstract class AbstractCreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<JacocoTask, ComponentCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("jacoco")
        override val type: Class<JacocoTask>
            get() = JacocoTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<JacocoTask>) {
            super.handleProvider(taskProvider)
            creationConfig
                .artifacts
                .setInitialProvider(taskProvider) { obj: JacocoTask -> obj.outputForDirs }
                .withName("out")
                .on(InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES)
            creationConfig
                .artifacts
                .setInitialProvider(taskProvider) { obj: JacocoTask -> obj.outputForJars }
                .withName("out")
                .on(InternalArtifactType.JACOCO_INSTRUMENTED_JARS)
        }

        override fun configure(task: JacocoTask) {
            super.configure(task)
            task.jacocoAntTaskConfiguration
                .from(
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                        task.project, getJacocoVersion(creationConfig)
                    )
                )
            task.forceOutOfProcess
                .set(
                    creationConfig
                        .services
                        .projectOptions[BooleanOption.FORCE_JACOCO_OUT_OF_PROCESS]
                )
        }
        }

    class CreationActionWithNoTransformAsmClasses(creationConfig: ComponentCreationConfig) :
        AbstractCreationAction(creationConfig) {

        override fun configure(task: JacocoTask) {
            super.configure(task)
            val projectClasses = creationConfig.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES)

            task.jarsWithIdentity
                .inputJars
                .from(projectClasses
                        .getRegularFiles(creationConfig.services.projectInfo.projectDirectory)
                )
            task.classesDir
                .from(projectClasses
                    .getDirectories(creationConfig.services.projectInfo.projectDirectory))
        }
    }

    class CreationActionWithTransformAsmClasses(creationConfig: ComponentCreationConfig) :
        AbstractCreationAction(creationConfig) {

        override fun configure(task: JacocoTask) {
            super.configure(task)
            task.jarsWithIdentity.inputJars.from(
                creationConfig.services.fileCollection(
                    creationConfig.artifacts.get(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_JARS)
                ).asFileTree
            )
            task.classesDir.from(
                creationConfig.services.fileCollection(
                    creationConfig.artifacts.get(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_CLASSES)
                ).asFileTree
            )
        }
    }

    companion object {
        /** Returns which Jacoco version to use.  */
        fun getJacocoVersion(creationConfig: ComponentCreationConfig): String {
            return creationConfig.global.testCoverage.jacocoVersion
        }

        private val CLASS_PATTERN = Pattern.compile(".*\\.class$")

        // META-INF/*.kotlin_module files need to be copied to output so they show up
        // in the intermediate classes jar.
        private val KOTLIN_MODULE_PATTERN = Pattern.compile("^META-INF/.*\\.kotlin_module$")
        fun calculateAction(inputRelativePath: String): Action {
            // Avoid instrumenting files from these directories as they can cause issues such as
            // recursive instrumentation.
            if (inputRelativePath.startsWith("org/jacoco") ||
                    inputRelativePath.startsWith("org/junit/runner/notification/RunListener")) {
                return Action.COPY
            }
            for (pattern in Action.COPY.patterns) {
                if (pattern.matcher(inputRelativePath).matches()) {
                    return Action.COPY
                }
            }
            for (pattern in Action.INSTRUMENT.patterns) {
                if (pattern.matcher(inputRelativePath).matches()) {
                    return Action.INSTRUMENT
                }
            }
            return Action.IGNORE
        }

        private fun getCorrespondingInstrumentedJar(
            outputFolder: File, identity: String
        ): File {
            return File(outputFolder, identity + SdkConstants.DOT_JAR)
        }
    }
}
