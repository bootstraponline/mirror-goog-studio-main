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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * A list of build features that can be disabled or enabled in an Android Library project.
 */
@Incubating
interface LibraryBuildFeatures : BuildFeatures {
    /**
     * Flag to disable Android resource processing.
     *
     * Setting the value to 'null' resets to the default value.
     * Default value is 'true'.
     *
     * You can override the default for this for all projects in your build by adding the line
     *     `android.defaults.buildfeatures.androidresources=false`
     * in the gradle.properties file at the root project of your build.
     *
     * More information about this feature at: TBD
     */
    var androidResources: Boolean?
}