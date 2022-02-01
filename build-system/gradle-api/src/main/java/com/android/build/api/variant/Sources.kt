/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Provides access to all source directories for a [Variant].
 *
 * since 7.2
 */
@Incubating
interface Sources {

    /**
     * Access to the Java source folders.
     */
    val java: SourceDirectories

    /**
     * Access ot the Kotlin source folders.
     */
    val kotlin: SourceDirectories

    /**
     * Access to the Android resources sources folders.
     */
    val res: SourceAndOverlayDirectories

    /**
     * Access to the Android assets sources folders.
     */
    val assets: SourceAndOverlayDirectories

    /**
     * Access to the JNI libraries folders
     */
    val jniLibs: SourceAndOverlayDirectories

    /**
     * Access to the shaders sources folders if [com.android.build.api.dsl.BuildFeatures.shaders]
     * is true otherwise null
     */
    val shaders: SourceAndOverlayDirectories?

    /**
     * Access to the machine learning models folders.
     */
    val mlModels: SourceAndOverlayDirectories

    /**
     * Access to the aidl sources folders if [com.android.build.api.dsl.BuildFeatures.aidl]
     * is true otherwise null
     */
    val aidl: SourceDirectories?

    /**
     * Access to the renderscript sources folders if
     * [com.android.build.api.dsl.BuildFeatures.renderScript] is true otherwise null.
     */
    @get:Deprecated("renderscript is deprecated and will be removed in a future release.")
    val renderscript: SourceDirectories?

    /**
     * Access (and potentially creates) a new [SourceDirectories] for a custom source type that can
     * be referenced by its [name].
     *
     * The first caller will create the new instance, other callers with the same [name] will get
     * the same instance returned. Any callers can obtain the final list of the folders registered
     * under this custom source type by calling [SourceDirectories.all].
     *
     * These sources directories are attached to the variant and will be visible to Android Studio.
     */
    fun getByName(name: String): SourceDirectories
}
