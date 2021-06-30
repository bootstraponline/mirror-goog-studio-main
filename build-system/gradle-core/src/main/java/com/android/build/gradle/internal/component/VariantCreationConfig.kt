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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResValue
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

interface VariantCreationConfig: ComponentCreationConfig {

    val buildConfigFields: MapProperty<String, BuildConfigField<out java.io.Serializable>>

    val resValues: MapProperty<ResValue.Key, ResValue>

    val packaging: Packaging

    override val needsMainDexListForBundle: Boolean

    /**
     * Returns the minimum SDK version for which we want to deploy this variant on.
     * In most cases this will be equal the minSdkVersion, but when the IDE is deploying to a
     * device running a higher API level than the minSdkVersion this will have that value and
     * can be used to enable some optimizations to build the APK faster.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    // TODO: move to ConsumableCreationConfig.
    val targetDeployApi: AndroidVersion

    val maxSdkVersion: Int?

    val isMultiDexEnabled: Boolean

    val isCoreLibraryDesugaringEnabled: Boolean

    val proguardFiles: ListProperty<RegularFile>

    val experimentalProperties: MapProperty<String, Any>
}
