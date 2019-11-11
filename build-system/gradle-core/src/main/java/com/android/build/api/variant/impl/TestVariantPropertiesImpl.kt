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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.Operations
import com.android.build.api.variant.TestVariantProperties
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.model.ObjectFactory

internal class TestVariantPropertiesImpl(private val objects: ObjectFactory,
    private val variantScope: VariantScope,
    private val variantConfiguration: com.android.build.gradle.internal.core.VariantConfiguration<*, *, *>,
    override val operations: Operations,
    publicVariantConfiguration: com.android.build.api.variant.VariantConfiguration):
    VariantPropertiesImpl(objects, variantScope, variantConfiguration, operations, publicVariantConfiguration), TestVariantProperties{
}