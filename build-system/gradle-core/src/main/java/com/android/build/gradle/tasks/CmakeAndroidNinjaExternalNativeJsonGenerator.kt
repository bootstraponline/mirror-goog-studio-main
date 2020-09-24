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

import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.convertCmakeCommandLineArgumentsToStringList
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStderrFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStdoutFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.settings.getFinalCmakeCommandLineArguments
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_FEATURE_NOT_SUPPORTED_FOR_VERSION
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_VERSION_IS_UNSUPPORTED
import com.android.utils.tokenizeCommandLineToEscaped
import com.google.gson.GsonBuilder
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * This strategy uses the older custom CMake (version 3.6) that directly generates the JSON file as
 * part of project configuration.
 */
internal class CmakeAndroidNinjaExternalNativeJsonGenerator(
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>,
    variantBuilder: GradleBuildVariant.Builder
) : ExternalNativeJsonGenerator(variant, abis, variantBuilder) {
    init {
        variantBuilder.nativeBuildSystemType = CMAKE
        cmakeMakefileChecks(variant)
    }

    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        if(abi.getBuildCommandArguments().isNotEmpty()){
            warnln(
                CMAKE_FEATURE_NOT_SUPPORTED_FOR_VERSION,
                "buildCommandArgs from CMakeSettings.json is not supported for CMake version 3.6 and below."
            )
        }
        val logPrefix = "${variant.variantName}|${abi.abi.tag} :"
        createProcessOutputJunction(
            abi.metadataGenerationCommandFile,
            abi.metadataGenerationStdoutFile,
            abi.metadataGenerationStderrFile,
            getProcessBuilder(abi),
            logPrefix)
            .logStderrToLifecycle()
            .logStdoutToInfo()
            .execute(ops::exec)

        postProcessForkCmakeOutput(abi)
    }

    fun postProcessForkCmakeOutput(abiConfig: CxxAbiModel) {
        // Process the android_gradle_build.json generated by our fork of CMake and swap the
        // buildTargetsCommand and cleanCommands with the parsed version.
        val gson = GsonBuilder()
            .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()
        abiConfig.jsonFile.takeIf { it.isFile }?.reader(StandardCharsets.UTF_8)?.use {
            val nativeBuildConfigValue = gson.fromJson(it, NativeBuildConfigValue::class.java)
            nativeBuildConfigValue.cleanCommandsComponents =
                nativeBuildConfigValue.cleanCommands?.map { it.tokenizeCommandLineToEscaped() }
            nativeBuildConfigValue.buildTargetsCommandComponents =
                nativeBuildConfigValue.buildTargetsCommand?.tokenizeCommandLineToEscaped()
            nativeBuildConfigValue.cleanCommands = null
            nativeBuildConfigValue.buildTargetsCommand = null
            nativeBuildConfigValue.libraries =
                nativeBuildConfigValue.libraries?.mapValues { (_, library) ->
                    library.buildCommandComponents =
                        library.buildCommand?.tokenizeCommandLineToEscaped()
                    library.buildCommand = null
                    library
                }
            nativeBuildConfigValue
        }?.also { nativeBuildConfigValue ->
            abiConfig.jsonFile.delete()
            abiConfig.jsonFile.writer(StandardCharsets.UTF_8).use { writer ->
                gson.toJson(nativeBuildConfigValue, writer)
            }
        }
    }

    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()

        builder.setExecutable(variant.module.cmake!!.cmakeExe)
        val arguments = mutableListOf<CommandLineArgument>()
        arguments.addAll(abi.getFinalCmakeCommandLineArguments())
        builder.addArgs(arguments.convertCmakeCommandLineArgumentsToStringList())
        return builder
    }

    override fun checkPrefabConfig() {
        errorln(
            CMAKE_VERSION_IS_UNSUPPORTED,
            "Prefab cannot be used with CMake 3.6. Use CMake 3.7 or newer."
        )
    }
}
