/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import java.util.ArrayList

/** The `android` extension for base feature module (application plugin).  */
open class BaseAppModuleExtension(
    project: Project,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo
) : AppExtension(
    project,
    projectOptions,
    globalScope,
    buildTypes,
    productFlavors,
    signingConfigs,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    true
), ApplicationExtension {

    private val variantActionList = mutableListOf<Action<AppVariant>>()
    private val variantPropertiesActionList = mutableListOf<Action<AppVariantProperties>>()

    var dynamicFeatures: MutableSet<String> = mutableSetOf()

    val bundle: BundleOptions =
        project.objects.newInstance(
            BundleOptions::class.java,
            project.objects,
            extraModelInfo.deprecationReporter
        )

    fun bundle(action: Action<BundleOptions>) {
        action.execute(bundle)
    }

    /**
     * Registers an [Action] to be executed on each [Variant] of the project.
     *
     * @param action an [Action] taking a [Variant] as a parameter.
     */
    @Incubating
    override fun onVariants(action: Action<AppVariant>) {
        variantActionList.add(action)
        // TODO: b/142715610 Resolve when onVariants is called with variants already existing the
        // applicationVariantList.
    }

    /**
     * Registers an [Action] to be executed on each [VariantProperties] of the project.
     *
     * @param action an [Action] taking a [VariantProperties] as a parameter.
     */
    @Incubating
    override fun onVariantsProperties(action: Action<AppVariantProperties>) {
        variantPropertiesActionList.add(action)
        // TODO: b/142715610 Resolve when onVariants is called with variants already existing the
        // applicationVariantList.
     }

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        super.addVariant(variant, variantScope)
        // TODO: move these 2 calls from the addVariant method.
        val variantData = variantScope.variantData as ApplicationVariantData
        variantActionList.forEach { action -> action.execute(variantData.publicVariantApi as AppVariant) }
        variantPropertiesActionList.forEach { action ->
            action.execute(variantData.publicVariantPropertiesApi as AppVariantProperties)
        }
    }
}
