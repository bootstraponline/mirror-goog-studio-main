/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.utils.FileUtils
import java.io.File

/**
 * Implementation of the sub project builder to generate/configure sub-projects.
 */
internal class SubProjectBuilderImpl(override val path: String) : SubProjectBuilder {

    override var group: String? = null
    override var version: String? = null
    override var useNewPluginsDsl: Boolean = false

    private var android: AndroidProjectBuilderImpl? = null
    private var androidComponents: AndroidComponentsBuilderImpl? = null
    private val files = mutableMapOf<String, SourceFile>()
    private val buildFileActions = mutableListOf<() -> String>()

    override val plugins = mutableListOf<PluginType>()
    internal val dependencies: DependenciesBuilderImpl = DependenciesBuilderImpl()

    private val wrappedLibraries = mutableListOf<Pair<String, ByteArray>>()

    override fun android(action: AndroidProjectBuilder.() -> Unit) {
        if (!plugins.containsAndroid()) {
            error("Cannot configure android for project '$path', so Android plugin applied")
        }

        if (android == null) {
            val pkgName = if (path == ":") {
                "pkg.name"
            } else {
                "pkg.name${path.replace(':', '.')}"
            }

            android = AndroidProjectBuilderImpl(this, pkgName)
        }

        android?.let { action(it) }
    }

    override fun androidComponents(action: AndroidComponentsBuilder.() -> Unit) {
        if (!plugins.containsAndroid()) {
            error("Cannot configure androidComponents for project '$path', so Android plugin applied")
        }

        if (androidComponents == null) {
            androidComponents = AndroidComponentsBuilderImpl()
        }

        androidComponents?.let { action(it) }
    }

    override fun addFile(relativePath: String, content: String) {
        files[relativePath] = SourceFile(relativePath, content)
    }

    override fun fileAt(relativePath: String) = files[relativePath]
        ?: error("Failed to find existing file with path '$relativePath' in project '$path'")

    override fun appendToBuildFile(action: () -> String) {
        buildFileActions.add(action)
    }

    override fun fileAtOrNull(relativePath: String) = files[relativePath]

    override fun dependencies(action: DependenciesBuilder.() -> Unit) {
        action(dependencies)
    }

    override fun wrap(library: ByteArray, fileName: String) {
        wrappedLibraries.add(fileName to library)
    }

    internal fun write(
        projectDir: File,
        buildFileType: BuildFileType,
        buildScriptContent: String?
    ) {
        android?.prepareForWriting()

        // write all the files
        for (sourceFile in files.values) {
            val file = File(projectDir, sourceFile.relativePath.replace('/', File.separatorChar))
            FileUtils.mkdirs(file.parentFile)
            file.writeText(sourceFile.content)
        }

        // generate the build file
        val sb = StringBuilder()
        buildScriptContent?.let { sb.append(it) }
        sb.append('\n')
        if (useNewPluginsDsl) {
            sb.append("plugins {\n")
            for (plugin in plugins) {
                sb.append("  id '${plugin.id}'\n")
            }
            sb.append("}\n")
        }

        group?.let {
            sb.append("group = \"$it\"\n")
        }
        version?.let {
            sb.append("version = \"$it\"\n")
        }

        if (!useNewPluginsDsl) {
            for (plugin in plugins) {
                sb.append("apply plugin: '${plugin.oldId}'\n")
            }
        }

        for ((fileName, libraryBinary) in wrappedLibraries) {
            File(projectDir, fileName).writeBytes(libraryBinary)
            sb.append("configurations.create(\"default\")\n")
            sb.append("artifacts.add(\"default\", file('$fileName'))\n")
        }

        android?.writeBuildFile(sb, plugins)
        androidComponents?.writeBuildFile(sb)

        dependencies.writeBuildFile(sb, projectDir)

        for (action in buildFileActions) {
            sb.append('\n').append(action())
        }

        File(projectDir, "build.gradle${buildFileType.extension}").writeText(sb.toString())
    }
}
