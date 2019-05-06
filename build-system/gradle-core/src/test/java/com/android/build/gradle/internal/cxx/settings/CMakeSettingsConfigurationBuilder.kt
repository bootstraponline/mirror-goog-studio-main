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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.CmakeProperty

/**
 * Builder class for [CMakeSettingsConfiguration].
 */
class CMakeSettingsConfigurationBuilder {
    var name : String = ""
    var description : String = ""
    var buildRoot : String = ""
    var generator : String = ""
    var configurationType : String = ""
    var installRoot : String = ""
    var cmakeExecutable : String = ""
    var cmakeToolchain : String = ""
    var cmakeCommandArgs : String = ""
    var buildCommandArgs : String = ""
    var ctestCommandArgs : String = ""
    var inheritedEnvironments = listOf<String>()
    val variables = mutableMapOf<String, String>()

    /**
     * Initialize this builder with the values from another [CMakeSettingsConfiguration]
     */
    fun initialize(settings : CMakeSettingsConfiguration) : CMakeSettingsConfigurationBuilder {
        name = settings.name
        description = settings.description
        buildRoot = settings.buildRoot
        generator = settings.generator
        configurationType = settings.configurationType
        installRoot = settings.installRoot
        inheritedEnvironments = settings.inheritEnvironments
        cmakeCommandArgs = settings.cmakeCommandArgs
        buildCommandArgs = settings.buildCommandArgs
        ctestCommandArgs = settings.ctestCommandArgs
        cmakeExecutable = settings.cmakeExecutable
        cmakeToolchain = settings.cmakeToolchain
        variables.putAll(settings.variables.map { Pair(it.name, it.value)})
        return this
    }

    /**
     * Add a variable to the map of variables for this builder.
     */
    fun putVariable(property : CmakeProperty, arg : Any) : CMakeSettingsConfigurationBuilder {
        variables[property.name] = arg.toString()
        return this
    }

    /**
     * Build an immutable [CMakeSettingsConfiguration] from the contents of this builder.
     */
    fun build() : CMakeSettingsConfiguration {
        return CMakeSettingsConfiguration(
            name = name,
            description = description,
            generator = generator,
            buildRoot =  buildRoot,
            installRoot =  installRoot,
            configurationType = configurationType,
            cmakeExecutable = cmakeExecutable,
            cmakeToolchain = cmakeToolchain,
            cmakeCommandArgs = cmakeCommandArgs,
            buildCommandArgs = buildCommandArgs,
            ctestCommandArgs = ctestCommandArgs,
            inheritEnvironments = inheritedEnvironments,
            variables = variables.map { (key, value) ->
                CMakeSettingsVariable(key, value)})
    }
}