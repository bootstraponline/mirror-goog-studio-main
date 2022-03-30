/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.component.legacy

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.ArtifactCollection

interface OldVariantApiLegacySupport {
    val buildTypeObj: BuildType
    val productFlavorList: List<ProductFlavor>
    val mergedFlavor: MergedFlavor
    val javaCompileOptions: JavaCompileOptions
    fun getJavaClasspathArtifacts(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): ArtifactCollection
}
