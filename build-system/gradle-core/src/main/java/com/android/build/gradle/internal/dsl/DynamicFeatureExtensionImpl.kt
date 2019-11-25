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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.NamedDomainObjectContainer

class DynamicFeatureExtensionImpl(
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
)  :
    CommonExtensionImpl<
            BuildType,
            ProductFlavor,
            SigningConfig,
            DynamicFeatureVariant,
            DynamicFeatureVariantProperties>(
        buildTypes,
        productFlavors,
        signingConfigs
    ),

    DynamicFeatureExtension<BuildType, ProductFlavor, SigningConfig>,
    ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<DynamicFeatureVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<DynamicFeatureVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<DynamicFeatureVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, DynamicFeatureVariant::class.java)
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<DynamicFeatureVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations, DynamicFeatureVariantProperties::class.java)
    }
}