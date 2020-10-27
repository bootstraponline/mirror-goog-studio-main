/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake

import com.android.build.gradle.internal.cxx.build.CxxRegularBuilder
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue
import com.android.utils.NativeSourceFileExtensions
import com.android.utils.NdkUtils
import com.android.utils.cxx.CompileCommandsEncoder
import com.android.utils.cxx.stripArgsForIde
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.Files
import java.io.File
import java.util.*

/**
 * The purpose of this class is to take the raw output of an ndk-build -n call and to produce a
 * NativeBuildConfigValue instance to pass upstream through gradle.
 *
 * This involves several stages of processing:
 *
 * (1) CommandLineParser.parse accepts a single string which is the ndk-build -n output. It tokenizes
 * each command in the string according to shell parsing rules on Windows or bash (includes mac).
 * The result is a list of CommandLine.
 *
 * (2) CommandClassifier.classify accepts the output of (1). It looks at each command for something it
 * recognizes. This will typically be calls to clang, gcc or gcc-ar. Once a command is recognized,
 * its file inputs and outputs are recorded. The result is a list of Classification.
 *
 * (3) FlowAnalyzer.analyze accepts the output of (2). It traces the flow of inputs and outputs. This
 * flow tracing will involve intermediate steps through linking and possibly archiving (gcc-ar).
 * Files involved are typically .c, .cpp, .o, .a and .so. The result of this step is a map from
 * terminal outputs (.so) to original inputs (.c and .cpp).
 *
 * (4) NativeBuildConfigValueBuilder.build accepts the output of (3). It examines the terminal outputs
 * and input information to build up an instance of NativeBuildConfigValue.
 */
class NativeBuildConfigValueBuilder internal constructor(
    private val androidMk: File,
    private val executionRootPath: File,
    fileConventions: OsFileConventions
) {
    private val toolChainToCCompiler: MutableMap<String, String> = HashMap()
    private val toolChainToCppCompiler: MutableMap<String, String> = HashMap()
    private val cFileExtensions: MutableSet<String> = HashSet()
    private val cppFileExtensions: MutableSet<String> = HashSet()
    private val outputs: MutableList<Output>
    private val fileConventions: OsFileConventions
    private var buildTargetsCommand: List<String>? = null

    /**
     * Skips processing compiler flags so that the generated [NativeBuildConfigValue] won't contain
     * any native compiler flags.
     */
    var skipProcessingCompilerFlags = false

    /**
     * Location of compile_commands.json.bin used with V2 native models. If non-null the file will
     * be generated as a side effect during [build].
     */
    var compileCommandsJsonBinFile: File? = null

    /**
     * Notes on implementation details: This is an implicit writer that generates the
     * compile_commands.json.bin. This is used by implementation of the [build] method. It's created
     * in [build] if [compileCommandsJsonBinFile] is set. If it's nonnull, callees of [build] will
     * output compile commands to it.
     */
    private var compileCommandsEncoder: CompileCommandsEncoder? = null

    /**
     * Constructs a NativeBuildConfigValueBuilder which can be used to build a [ ].
     *
     *
     * projectRootPath -- file path to the project that contains an ndk-build project (
     */
    constructor(androidMk: File, executionRootPath: File) : this(
        androidMk,
        executionRootPath,
        AbstractOsFileConventions.createForCurrentHost()
    )

    /** Set the commands and variantName for the NativeBuildConfigValue being built.  */
    fun setCommands(
        buildCommand: List<String>,
        cleanCommand: List<String>,
        variantName: String,
        dryRunOutput: String
    ): NativeBuildConfigValueBuilder {
        if (outputs.isNotEmpty()) {
            throw RuntimeException("setCommands should be called once")
        }
        val buildSteps = CommandClassifier.classify(dryRunOutput, fileConventions)
        val outputs = FlowAnalyzer.analyze(buildSteps)
        for ((key, value) in outputs.entries()) {
            this.outputs.add(Output(key, value, buildCommand, cleanCommand, variantName))
        }
        buildTargetsCommand =
            buildCommand + listOf(CxxRegularBuilder.BUILD_TARGETS_PLACEHOLDER)
        return this
    }

    /**
     * Skips processing compiler flags so that the generated [NativeBuildConfigValue] won't contain
     * any native compiler flags.
     */
    fun skipProcessingCompilerFlags() {
        skipProcessingCompilerFlags = true
    }

    /**
     * Builds the [NativeBuildConfigValue] from the given information.
     */
    fun build(): NativeBuildConfigValue {
        val compileCommandsJsonBinFile = this.compileCommandsJsonBinFile
        return if (compileCommandsJsonBinFile == null) {
            buildImpl()
        } else {
            CompileCommandsEncoder(compileCommandsJsonBinFile).use { encoder ->
                compileCommandsEncoder = encoder
                buildImpl()
            }
        }
    }

    private fun buildImpl(): NativeBuildConfigValue {
        findLibraryNames()
        findToolchainNames()
        findToolChainCompilers()
        val config = NativeBuildConfigValue()
        // Sort by library name so that output is stable
        outputs.sortBy { it.libraryName }
        config.cleanCommandsComponents = generateCleanCommands()
        config.buildTargetsCommandComponents = buildTargetsCommand
        config.buildFiles = Lists.newArrayList(androidMk)
        config.libraries = generateLibraries()
        config.toolchains = generateToolchains()
        config.cFileExtensions = generateExtensions(cFileExtensions)
        config.cppFileExtensions = generateExtensions(cppFileExtensions)
        return config
    }

    private fun findLibraryNames() {
        for (output in outputs) {
            // This pattern is for standard ndk-build and should give names like:
            //  mips64-test-libstl-release
            val parentFile = fileConventions.getFileParent(output.outputFileName)
            val abi = fileConventions.getFileName(parentFile)
            output.artifactName = NdkUtils.getTargetNameFromBuildOutputFileName(
                fileConventions.getFileName(output.outputFileName)
            )
            output.libraryName =
                String.format("%s-%s-%s", output.artifactName, output.variantName, abi)
        }
    }

    private fun findToolChainCompilers() {
        for (output in outputs) {
            val toolchain = output.toolchain
            val cCompilers: MutableSet<String> =
                HashSet()
            val cppCompilers: MutableSet<String> =
                HashSet()
            val compilerToWeirdExtensions: MutableMap<String, MutableSet<String>> =
                HashMap()
            for (command in output.commandInputs) {
                val compilerCommand = command.command.executable
                val extension =
                    Files.getFileExtension(command.onlyInput)
                when {
                    NativeSourceFileExtensions.C_FILE_EXTENSIONS.contains(extension) -> {
                        cFileExtensions.add(extension)
                        cCompilers.add(compilerCommand)
                    }
                    NativeSourceFileExtensions.CPP_FILE_EXTENSIONS.contains(extension) -> {
                        cppFileExtensions.add(extension)
                        cppCompilers.add(compilerCommand)
                    }
                    else -> {
                        // Unrecognized extensions are recorded and added to the relevant compiler
                        var extensions = compilerToWeirdExtensions[compilerCommand]
                        if (extensions == null) {
                            extensions = HashSet()
                            compilerToWeirdExtensions[compilerCommand] = extensions
                        }
                        extensions.add(extension)
                    }
                }
            }
            if (cCompilers.size > 1) {
                throw RuntimeException("Too many c compilers in toolchain.")
            }
            if (cppCompilers.size > 1) {
                throw RuntimeException("Too many cpp compilers in toolchain.")
            }
            val cCompiler: String? = cCompilers.firstOrNull()?.also {
                if (toolchain != null) {
                    toolChainToCCompiler[toolchain] = it
                }
            }

            val cppCompiler: String? = cppCompilers.firstOrNull()?.also {
                if (toolchain != null) {
                    toolChainToCppCompiler[toolchain] = it
                }
            }

            // Record the weird file extensions.
            for (compiler in compilerToWeirdExtensions.keys) {
                if (compiler == cCompiler) {
                    cFileExtensions.addAll(compilerToWeirdExtensions[compiler]!!)
                } else if (compiler == cppCompiler) {
                    cppFileExtensions.addAll(compilerToWeirdExtensions[compiler]!!)
                }
            }
        }
    }

    private fun findToolChainName(outputFileName: String): String =
        "toolchain-" + fileConventions.getFileName(fileConventions.getFileParent(outputFileName))

    private fun findToolchainNames() {
        for (output in outputs) {
            output.toolchain = findToolChainName(output.outputFileName)
        }
    }

    private fun generateCleanCommands(): List<List<String>> {
        val cleanCommands: MutableSet<List<String>> =
            Sets.newHashSet()
        for (output in outputs) {
            cleanCommands.add(output.cleanCommand)
        }
        return Lists.newArrayList(cleanCommands)
    }

    private fun generateLibraries(): Map<String?, NativeLibraryValue> {
        val librariesMap: MutableMap<String?, NativeLibraryValue> = HashMap()
        for (output in outputs) {
            val value = NativeLibraryValue()
            librariesMap[output.libraryName] = value
            value.buildCommandComponents = output.buildCommand + listOf(output.outputFileName)
            value.abi = fileConventions.getFileName(
                fileConventions.getFileParent(output.outputFileName)
            )
            value.artifactName = output.artifactName
            value.toolchain = output.toolchain
            value.output = fileConventions.toFile(output.outputFileName)
            compileCommandsEncoder?.let { encoder ->
                val workingDirPath = executionRootPath.absolutePath
                for (commandInput in output.commandInputs) {
                    val command = commandInput.command
                    encoder.writeCompileCommand(
                        fileConventions.toFile(commandInput.onlyInput),
                        File(command.executable),
                        stripArgsForIde(commandInput.onlyInput, command.escapedFlags),
                        File(workingDirPath)
                    )
                }
            }
            if (skipProcessingCompilerFlags) continue
            val nativeSourceFiles = ArrayList<NativeSourceFileValue>()
            value.files = nativeSourceFiles
            for (input in output.commandInputs) {
                val file = NativeSourceFileValue()
                nativeSourceFiles.add(file)
                file.src = fileConventions.toFile(input.onlyInput)
                if (!fileConventions.isPathAbsolute(input.onlyInput)) {
                    file.src = fileConventions.toFile(executionRootPath, input.onlyInput)
                }
                val rawFlagsLookUpMap = mutableMapOf<String, String>()
                for (i in 0 until input.command.rawFlags.size) {
                    rawFlagsLookUpMap[input.command.escapedFlags[i]] = input.command.rawFlags[i]
                }
                file.flags =
                    stripArgsForIde(
                        input.onlyInput,
                        input.command.escapedFlags
                    ).map { rawFlagsLookUpMap[it] }.joinToString(" ")
            }
        }
        return librariesMap
    }

    private fun generateToolchains(): Map<String?, NativeToolchainValue> {
        val toolchainSet = outputs.mapNotNull { output: Output -> output.toolchain }.toSet()
        val toolchains: MutableList<String> = ArrayList(toolchainSet)
        toolchains.sort()
        val toolchainsMap: MutableMap<String?, NativeToolchainValue> = HashMap()
        for (toolchain in toolchains) {
            val toolchainValue = NativeToolchainValue()
            toolchainsMap[toolchain] = toolchainValue
            if (toolChainToCCompiler.containsKey(toolchain)) {
                toolchainValue.cCompilerExecutable =
                    fileConventions.toFile(toolChainToCCompiler[toolchain]!!)
            }
            if (toolChainToCppCompiler.containsKey(toolchain)) {
                toolchainValue.cppCompilerExecutable =
                    fileConventions.toFile(toolChainToCppCompiler[toolchain]!!)
            }
        }
        return toolchainsMap
    }

    private class Output constructor(
        val outputFileName: String,
        val commandInputs: List<BuildStepInfo>,
        val buildCommand: List<String>,
        val cleanCommand: List<String>,
        val variantName: String
    ) {
        var artifactName: String? = null
        var libraryName: String? = null
        var toolchain: String? = null

    }

    companion object {
        private fun generateExtensions(extensionSet: Set<String>): Collection<String> {
            val extensionList: MutableList<String> = Lists.newArrayList(extensionSet)
            extensionList.sort()
            return extensionList
        }
    }

    init {
        outputs = ArrayList()
        this.fileConventions = fileConventions
    }
}
