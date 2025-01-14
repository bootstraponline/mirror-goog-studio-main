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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.analytics.AnalyticsEnabledDynamicFeatureVariant
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.features.DexingCreationConfigImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.component.impl.getAndroidResources
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Component
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.DynamicFeatureVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class DynamicFeatureVariantImpl @Inject constructor(
    override val variantBuilder: DynamicFeatureVariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: DynamicFeatureVariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    transformManager: TransformManager,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    globalTaskCreationConfig: GlobalTaskCreationConfig,
) : VariantImpl<DynamicFeatureVariantDslInfo>(
    variantBuilder,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    transformManager,
    internalServices,
    taskCreationServices,
    globalTaskCreationConfig
), DynamicFeatureVariant, DynamicFeatureCreationConfig, HasAndroidTest, HasTestFixtures {

    init {
        // TODO: Should be removed once we stop implementing all build type interfaces in one class
        if (dslInfo.isMultiDexSetFromDsl) {
            services.issueReporter
                .reportWarning(
                    IssueReporter.Type.GENERIC,
                    "Native multidex is always used for dynamic features. Please " +
                            "remove 'multiDexEnabled true|false' from your " +
                            "build.gradle file."
                )
        }
    }

    /*
     * Providers of data coming from the base modules. These are loaded just once and finalized.
     */
    private val baseModuleMetadata: Provider<ModuleMetadata> = instantiateBaseModuleMetadata(variantDependencies)
    private val featureSetMetadata: Provider<FeatureSetMetadata>  = instantiateFeatureSetMetadata(variantDependencies)

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, baseModuleMetadata.map { it.applicationId })

    override val androidResources: AndroidResources by lazy {
        getAndroidResources()
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdkVersion.apiLevel
        )
    }

    override var androidTest: AndroidTestImpl? = null

    override var testFixtures: TestFixturesImpl? = null

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val dexingCreationConfig: DexingCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        DexingCreationConfigImpl(
            this,
            dslInfo.dexingDslInfo,
            internalServices
        )
    }

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    override val testOnlyApk: Boolean
        get() = isTestApk()

    override val baseModuleDebuggable: Provider<Boolean> = internalServices.providerOf(
        Boolean::class.java,
        baseModuleMetadata.map { it.debuggable })

    override val featureName: Provider<String> = run {
        val projectPath = internalServices.projectInfo.path
        internalServices.providerOf(String::class.java, featureSetMetadata.map {
            it.getFeatureNameFor(projectPath)
                ?: throw RuntimeException("Failed to find feature name for $projectPath in ${it.sourceFile}")
        })
    }

    /**
     * resource offset for resource compilation of a feature.
     * This is computed by the base module and consumed by the features. */
    override val resOffset: Provider<Int> = run {
        val projectPath = internalServices.projectInfo.path
        internalServices.providerOf(Int::class.java, featureSetMetadata.map {
            it.getResOffsetFor(projectPath)
                ?: throw RuntimeException("Failed to find resource offset for $projectPath in ${it.sourceFile}")
        })
    }

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override val shouldPackageProfilerDependencies: Boolean = false

    override val advancedProfilingTransforms: List<String>
        get() {
            return services.projectOptions[StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS]?.split(
                ","
            ) ?: emptyList()
        }

    override val signingConfigImpl: SigningConfigImpl? = null

    override val useJacocoTransformInstrumentation: Boolean
        get() = isAndroidTestCoverageEnabled

    override val packageJacocoRuntime: Boolean
        get() = false

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private fun instantiateBaseModuleMetadata(
        variantDependencies: VariantDependencies
    ): Provider<ModuleMetadata> {
        val artifact = variantDependencies
            .getArtifactFileCollection(
                ConsumedConfigType.COMPILE_CLASSPATH,
                ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
            )

        // Have to wrap the return of artifact.elements.map because we cannot call
        // finalizeValueOnRead directly on Provider
        return internalServices.providerOf(
            ModuleMetadata::class.java,
            artifact.elements.map { ModuleMetadata.load(it.single().asFile) })
    }


    private fun instantiateFeatureSetMetadata(
        variantDependencies: VariantDependencies
    ): Provider<FeatureSetMetadata> {
        val artifact = variantDependencies.getArtifactFileCollection(
            ConsumedConfigType.COMPILE_CLASSPATH,
            ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.FEATURE_SET_METADATA
        )

        // Have to wrap the return of artifact.elements.map because we cannot call
        // finalizeValueOnRead directly on Provider
        return internalServices.providerOf(
            FeatureSetMetadata::class.java,
            artifact.elements.map { FeatureSetMetadata.load(it.single().asFile) })
    }

    // version name is coming from the base module via a published artifact, and therefore
    // is ready only
    // The public API does not expose this so this is ok, but it's safer to make it read-only
    // directly to catch potential errors.
    // The old API has a check for this type of plugins to avoid calling set() on it.
    override fun createVersionNameProperty(): Property<String?> =
        internalServices.nullablePropertyOf(
            String::class.java,
            baseModuleMetadata.map { it.versionName },
        ).also {
            it.disallowChanges()
            it.finalizeValueOnRead()
        }

    // version code is coming from the base module via a published artifact, and therefore
    // is ready only
    // The public API does not expose this so this is ok, but it's safer to make it read-only
    // directly to catch potential errors.
    // The old API has a check for this type of plugins to avoid calling set() on it.
    override fun createVersionCodeProperty() : Property<Int?> =
        internalServices.nullablePropertyOf(
            Int::class.java,
            baseModuleMetadata.map { it.versionCode?.toInt() },
        ).also {
            it.disallowChanges()
            it.finalizeValueOnRead()
        }

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledDynamicFeatureVariant::class.java,
                this,
                stats
            ) as T
        }

    override val optimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            null,
            null,
            internalServices,
            baseModuleMetadata
        )
    }
}
