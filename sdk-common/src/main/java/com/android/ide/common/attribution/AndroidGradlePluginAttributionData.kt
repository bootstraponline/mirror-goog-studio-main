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

package com.android.ide.common.attribution

import com.android.SdkConstants
import com.android.utils.FileUtils
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Serializable

data class AndroidGradlePluginAttributionData(

    /**
     * A map that maps a task name to its class name
     * ex: mergeDevelopmentDebugResources -> com.android.build.gradle.tasks.MergeResources
     */
    @Deprecated("Use taskNameToTaskInfoMap")
    val taskNameToClassNameMap: Map<String, String> = emptyMap(),

    /**
     * Contains registered tasks that are not cacheable.
     */
    val noncacheableTasks: Set<String> = emptySet(),

    /**
     * Contains a list of tasks sharing the same outputs.
     * The key of the map represents the absolute path to the file or the directory output and the
     * key contains a list of tasks declaring this file or directory as their output.
     */
    val tasksSharingOutput: Map<String, List<String>> = emptyMap(),

    /**
     * Contains garbage collection data for the last build.
     * The key of the map represents the name of the garbage collector, while the value represents
     * the time spent collecting in milliseconds.
     */
    val garbageCollectionData: Map<String, Long> = emptyMap(),
    /**
     * Contains the ids of the plugins defined in the buildSrc.
     */
    val buildSrcPlugins: Set<String> = emptySet(),

    /**
     * Contains information about java used to run this build.
     */
    val javaInfo: JavaInfo = JavaInfo(),

    /**
     * Buildscript classpath dependencies list.
     * Dependency is encoded by a string in "group:name:version" format.
     */
    val buildscriptDependenciesInfo: Set<String> = emptySet(),

    /**
     * Contains information about this build.
     */
    val buildInfo: BuildInfo? = null,

    /**
     * A map of task names to its class name, and primary and secondary task categories.
     * Only sends task categories if flag BUILD_ANALYZER_TASK_LABELS is enabled.
     */
    val taskNameToTaskInfoMap: Map<String, TaskInfo> = emptyMap(),

    /**
     * List of warnings / information in Build Analyzer.
     */
    val buildAnalyzerTaskCategoryIssues: List<BuildAnalyzerTaskCategoryIssue> = emptyList()

) : Serializable {

    /**
     * Information about java used to run this build.
     * Default values are empty, e.g. when no value found.
     */
    data class JavaInfo(
        val version: String = "",
        val vendor: String = "",
        val home: String = "",
        val vmArguments: List<String> = emptyList()
    ) : Serializable

    data class BuildInfo(
        val agpVersion: String?,
        val configurationCacheIsOn: Boolean?
    ) : Serializable

    data class TaskInfo(
        val className: String,
        val taskCategoryInfo: TaskCategoryInfo
    ) : Serializable

    data class TaskCategoryInfo(
        val primaryTaskCategory: TaskCategory,
        val secondaryTaskCategories: List<TaskCategory> = emptyList()
    ) : Serializable

    companion object {

        fun getAttributionFile(outputDir: File) = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )

        fun load(outputDir: File): AndroidGradlePluginAttributionData? {
            try {
                BufferedReader(FileReader(getAttributionFile(outputDir))).use {
                    return AttributionDataAdapter.fromJson(it)
                }
            } catch (e: Exception) {
                return null
            }
        }
    }

    object AttributionDataAdapter : TypeAdapter<AndroidGradlePluginAttributionData>() {

        private fun <A> JsonWriter.writeList(name: String, list: Collection<A>, valueWriter: (value: A) -> Unit) {
            name(name).beginArray()
            list.forEach { valueWriter(it) }
            endArray()
        }

        private fun <A> JsonReader.readList(valueReader: () -> A): List<A> {
            val list = arrayListOf<A>()
            beginArray()
            while (hasNext()) {
                list.add(valueReader())
            }
            endArray()
            return list
        }

        private fun JsonWriter.writeTaskToClassEntry(entry: Map.Entry<String, String>) {
            beginObject()
            name("taskName").value(entry.key)
            name("className").value(entry.value)
            endObject()
        }

        private fun JsonReader.readTaskToClassEntry(): Pair<String, String> {
            beginObject()
            var taskName: String? = null
            var className: String? = null
            while (hasNext()) {
                when (nextName()) {
                    "taskName" -> taskName = nextString()
                    "className" -> className = nextString()
                }
            }
            endObject()
            return taskName!! to className!!
        }

        private fun JsonWriter.writeTasksSharingOutputEntry(entry: Map.Entry<String, List<String>>) {
            beginObject()
            name("filePath").value(entry.key)
            name("tasksList").beginArray()
            entry.value.forEach { taskName ->
                value(taskName)
            }
            endArray()
            endObject()
        }

        private fun JsonReader.readTasksSharingOutputEntry(): Pair<String, List<String>> {
            beginObject()
            var filePath: String? = null
            val tasksList = ArrayList<String>()
            while (hasNext()) {
                when (nextName()) {
                    "filePath" -> filePath = nextString()
                    "tasksList" -> {
                        beginArray()
                        while (hasNext()) {
                            tasksList.add(nextString())
                        }
                        endArray()
                    }
                }
            }
            endObject()
            return filePath!! to tasksList
        }

        private fun JsonWriter.writeGCEntry(entry: Map.Entry<String, Long>) {
            beginObject()
            name("gcName").value(entry.key)
            name("duration").value(entry.value)
            endObject()
        }

        private fun JsonReader.readGCEntry(): Pair<String, Long> {
            beginObject()
            var gcName: String? = null
            var duration: Long? = null
            while (hasNext()) {
                when (nextName()) {
                    "gcName" -> gcName = nextString()
                    "duration" -> duration = nextLong()
                }
            }
            endObject()
            return gcName!! to duration!!
        }

        private fun JsonWriter.writeJavaInfo(javaInfo: JavaInfo) {
            name("javaInfo").beginObject()
            name("javaVersion").value(javaInfo.version)
            name("javaVendor").value(javaInfo.vendor)
            name("javaHome").value(javaInfo.home)
            name("vmArguments").beginArray()
            javaInfo.vmArguments.forEach { value(it) }
            endArray()
            endObject()
        }

        private fun JsonReader.readJavaInfo(): JavaInfo {
            beginObject()
            var version = ""
            var vendor = ""
            var home = ""
            val vmArguments = ArrayList<String>()
            while (hasNext()) {
                when (nextName()) {
                    "javaVersion" -> version = nextString()
                    "javaVendor" -> vendor = nextString()
                    "javaHome" -> home = nextString()
                    "vmArguments" -> {
                        beginArray()
                        while (hasNext()) {
                            vmArguments.add(nextString())
                        }
                        endArray()
                    }
                }
            }
            endObject()
            return JavaInfo(version, vendor, home, vmArguments)
        }

        private fun JsonWriter.writeBuildInfo(info: BuildInfo?) {
            if (info == null) return
            name("buildInfo").beginObject()
            info.agpVersion?.let { name("agpVersion").value(it) }
            info.configurationCacheIsOn?.let{ name("configurationCacheIsOn").value(it) }
            endObject()
        }

        private fun JsonReader.readBuildInfo(): BuildInfo {
            beginObject()
            var agpVersion: String? = null
            var configurationCacheIsOn: Boolean? = null
            while (hasNext()) {
                when (nextName()) {
                    "agpVersion" -> agpVersion = nextString()
                    "configurationCacheIsOn" -> configurationCacheIsOn = nextBoolean()
                }
            }
            endObject()
            return BuildInfo(agpVersion, configurationCacheIsOn)
        }

        private fun JsonWriter.writeTaskToTaskInfoEntry(taskName:String, taskInfo: TaskInfo) {
            beginObject()
            name("taskName").value(taskName)
            taskInfo.className.let { name("className").value(it) }
            taskInfo.taskCategoryInfo.let {
                name("primaryTaskCategory").value(it.primaryTaskCategory.toString())
                name("secondaryTaskCategories").beginArray()
                it.secondaryTaskCategories.forEach { category ->
                    value(category.toString())
                }
                endArray()
            }
            endObject()
        }

        private fun JsonReader.readTaskToTaskInfoEntry(): Pair<String, TaskInfo> {
            beginObject()
            var taskName: String? = null
            var className: String? = null
            var primaryTaskCategory: TaskCategory? = null
            val secondaryTaskCategories = mutableListOf<TaskCategory>()
            while (hasNext()) {
                when (nextName()) {
                    "taskName" -> taskName = nextString()
                    "className" -> className = nextString()
                    "primaryTaskCategory" -> primaryTaskCategory = TaskCategory.valueOf(nextString())
                    "secondaryTaskCategories" -> {
                        beginArray()
                        while(hasNext()) {
                            secondaryTaskCategories.add(TaskCategory.valueOf(nextString()))
                        }
                        endArray()
                    }
                }
            }
            endObject()
            val taskCategoryInfo = TaskCategoryInfo(
                    primaryTaskCategory = primaryTaskCategory!!,
                    secondaryTaskCategories = secondaryTaskCategories
            )
            return taskName!! to TaskInfo(className!!, taskCategoryInfo)
        }

        private fun JsonWriter.writeBuildAnalyzerTaskCategoryIssues(buildAnalyzerTaskCategoryIssues: List<BuildAnalyzerTaskCategoryIssue>) {
            name("buildAnalyzerTaskCategoryIssues").beginArray()
            buildAnalyzerTaskCategoryIssues.forEach {
                value(it.toString())
            }
            endArray()
        }

        private fun JsonReader.readBuildAnalyzerTaskCategoryIssues(): List<BuildAnalyzerTaskCategoryIssue> {
            val buildAnalyzerTaskCategoryIssues = ArrayList<BuildAnalyzerTaskCategoryIssue>()
            beginArray()
            while (hasNext()) {
                buildAnalyzerTaskCategoryIssues.add(BuildAnalyzerTaskCategoryIssue.valueOf(nextString()))
            }
            endArray()
            return buildAnalyzerTaskCategoryIssues
        }

        override fun write(writer: JsonWriter, data: AndroidGradlePluginAttributionData) {
            writer.beginObject()

            writer.writeList("taskNameToClassNameMap", data.taskNameToClassNameMap.entries) {
                writer.writeTaskToClassEntry(it)
            }

            writer.writeList("tasksSharingOutput", data.tasksSharingOutput.entries) {
                writer.writeTasksSharingOutputEntry(it)
            }

            writer.writeList("garbageCollectionData", data.garbageCollectionData.entries) {
                writer.writeGCEntry(it)
            }

            writer.writeList("buildSrcPlugins", data.buildSrcPlugins) { plugin ->
                writer.value(plugin)
            }

            writer.writeJavaInfo(data.javaInfo)

            writer.writeList("buildscriptDependencies", data.buildscriptDependenciesInfo) {
                writer.value(it)
            }

            writer.writeBuildInfo(data.buildInfo)

            writer.writeList("taskNameToTaskInfoMap", data.taskNameToTaskInfoMap.entries) {
                writer.writeTaskToTaskInfoEntry(it.key, it.value)
            }

            writer.writeBuildAnalyzerTaskCategoryIssues(data.buildAnalyzerTaskCategoryIssues)

            writer.endObject()
        }

        override fun read(reader: JsonReader): AndroidGradlePluginAttributionData {
            val taskNameToClassNameMap = HashMap<String, String>()
            val tasksSharingOutput = HashMap<String, List<String>>()
            val garbageCollectionData = HashMap<String, Long>()
            val buildSrcPlugins = HashSet<String>()
            var javaInfo = JavaInfo()
            val buildscriptDependenciesInfo = HashSet<String>()
            var buildInfo: BuildInfo? = null
            val taskNameToTaskInfoMap = HashMap<String, TaskInfo>()
            val buildAnalyzerTaskCategoryIssues = ArrayList<BuildAnalyzerTaskCategoryIssue>()

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "taskNameToClassNameMap" -> taskNameToClassNameMap.putAll(
                            reader.readList { reader.readTaskToClassEntry() }
                    )

                    "tasksSharingOutput" -> tasksSharingOutput.putAll(
                            reader.readList { reader.readTasksSharingOutputEntry() }
                    )

                    "garbageCollectionData" -> garbageCollectionData.putAll(
                            reader.readList { reader.readGCEntry() }
                    )

                    "buildSrcPlugins" -> buildSrcPlugins.addAll(reader.readList { reader.nextString() })

                    "javaInfo" -> javaInfo = reader.readJavaInfo()

                    "buildscriptDependencies" -> buildscriptDependenciesInfo.addAll(
                            reader.readList { reader.nextString() }
                    )

                    "buildInfo" -> buildInfo = reader.readBuildInfo()

                    "taskNameToTaskInfoMap" -> taskNameToTaskInfoMap.putAll(
                            reader.readList { reader.readTaskToTaskInfoEntry() }
                    )

                    "buildAnalyzerTaskCategoryIssues" -> buildAnalyzerTaskCategoryIssues.addAll(reader.readBuildAnalyzerTaskCategoryIssues())

                    else -> {
                        reader.skipValue()
                    }
                }
            }

            reader.endObject()

            return AndroidGradlePluginAttributionData(
                taskNameToClassNameMap = taskNameToClassNameMap,
                tasksSharingOutput = tasksSharingOutput,
                garbageCollectionData = garbageCollectionData,
                buildSrcPlugins = buildSrcPlugins,
                javaInfo = javaInfo,
                buildscriptDependenciesInfo = buildscriptDependenciesInfo,
                buildInfo = buildInfo,
                taskNameToTaskInfoMap = taskNameToTaskInfoMap,
                buildAnalyzerTaskCategoryIssues = buildAnalyzerTaskCategoryIssues
            )
        }
    }
}
