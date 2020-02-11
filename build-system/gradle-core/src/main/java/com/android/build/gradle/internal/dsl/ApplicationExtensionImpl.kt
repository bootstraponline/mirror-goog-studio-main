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

import com.android.build.api.component.GenericFilteredComponentActionRegistrar
import com.android.build.api.component.impl.GenericFilteredComponentActionRegistrarImpl
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.coverage.JacocoOptions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
class ApplicationExtensionImpl(
    dslServices: DslServices,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
) :
    CommonExtensionImpl<
            AnnotationProcessorOptions,
            ApplicationBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            ApplicationVariant<ApplicationVariantProperties>,
            ApplicationVariantProperties>(
        dslServices,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),
    ApplicationExtension<
            AaptOptions,
            AbiSplitOptions,
            AdbOptions,
            AnnotationProcessorOptions,
            BuildType,
            CmakeOptions,
            CompileOptions,
            DataBindingOptions,
            DefaultConfig,
            DensitySplitOptions,
            ExternalNativeBuild,
            JacocoOptions,
            LintOptions,
            NdkBuildOptions,
            PackagingOptions,
            ProductFlavor,
            SigningConfig,
            Splits,
            TestOptions,
            TestOptions.UnitTestOptions> {

    override val buildFeatures: ApplicationBuildFeatures =
        dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java)

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericFilteredComponentActionRegistrar<ApplicationVariant<ApplicationVariantProperties>>
        get() = dslServices.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslServices,
            variantOperations,
            ApplicationVariant::class.java
        ) as GenericFilteredComponentActionRegistrar<ApplicationVariant<ApplicationVariantProperties>>
    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericFilteredComponentActionRegistrar<ApplicationVariantProperties>
        get() = dslServices.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslServices,
            variantPropertiesOperations,
            ApplicationVariantProperties::class.java
        ) as GenericFilteredComponentActionRegistrar<ApplicationVariantProperties>

    override val dependenciesInfo: DependenciesInfo =
      dslServices.newInstance(DependenciesInfoImpl::class.java)

    override fun dependenciesInfo(action: DependenciesInfo.() -> Unit) {
        action.invoke(dependenciesInfo)
    }

    fun dependenciesInfo(action: Action<DependenciesInfo>) {
        action.execute(dependenciesInfo)
    }
}
