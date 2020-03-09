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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Represents a built artifact that is present in the file system.
 */
@Incubating
interface BuiltArtifact: VariantOutputConfiguration {

    /**
     * Returns a read-only version code.
     *
     * @return version code or null if the version code is unknown (not set in manifest nor DSL)
     */
    val versionCode: Int?

    /**
     * Returns a read-only version name.
     *
     * @return version name or null if the version name is unknown (not set in manifest nor DSL)
     */
    val versionName: String?

    /**
     * Absolute path to the built file
     *
     * @return the output file path.
     */
    val outputFile: String

    /**
     * [Map] of [String] for properties that are associated with the output. Such properties
     * can be consumed by downstream [org.gradle.api.Task] but represents an implicit contract
     * between the producer and consumer.
     *
     * TODO: once cleanup is finished, consider removing this facility.
     */
    val properties: Map<String, String>
}