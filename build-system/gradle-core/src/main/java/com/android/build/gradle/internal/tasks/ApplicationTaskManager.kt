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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.AbstractAppTaskManager
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.dsl.AbstractPublishing
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureNamespacesTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadataWriterTask
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.BuildPrivacySandboxSdkApks
import com.android.build.gradle.tasks.sync.ApplicationVariantModelTask
import com.android.build.gradle.tasks.sync.AppIdListTask
import com.android.builder.core.ComponentType
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection

class ApplicationTaskManager(
    project: Project,
    private val variants: Collection<ComponentInfo<ApplicationVariantBuilder, ApplicationCreationConfig>>,
    testComponents: Collection<TestComponentCreationConfig>,
    testFixturesComponents: Collection<TestFixturesCreationConfig>,
    globalConfig: GlobalTaskCreationConfig,
    localConfig: TaskManagerConfig,
    extension: BaseExtension,
) : AbstractAppTaskManager<ApplicationVariantBuilder, ApplicationCreationConfig>(
    project,
    variants,
    testComponents,
    testFixturesComponents,
    globalConfig,
    localConfig,
    extension,
) {

    override fun createTopLevelTasks(componentType: ComponentType, variantModel: VariantModel) {
        super.createTopLevelTasks(componentType, variantModel)
        taskFactory.register(
            AppIdListTask.CreationAction(
                globalConfig,
                variants.associate {
                    it.variant.name to it.variant.applicationId
                }
            )
        )
    }

    override fun doCreateTasksForVariant(
        variantInfo: ComponentInfo<ApplicationVariantBuilder, ApplicationCreationConfig>
    ) {
        createCommonTasks(variantInfo)

        val variant = variantInfo.variant

        taskFactory.register(ApplicationVariantModelTask.CreationAction(variant))

        // Base feature specific tasks.
        taskFactory.register(FeatureSetMetadataWriterTask.CreationAction(variant))

        createValidateSigningTask(variant)
        // Add tasks to produce the signing config files.
        taskFactory.register(SigningConfigWriterTask.CreationAction(variant))
        taskFactory.register(SigningConfigVersionsWriterTask.CreationAction(variant))

        // Add a task to produce the app-metadata.properties file
        taskFactory.register(AppMetadataTask.CreationAction(variant))
            .dependsOn(variant.taskContainer.preBuildTask)

        if (globalConfig.assetPacks.isNotEmpty()) {
            createAssetPackTasks(variant)
        }

        taskFactory.register(MergeArtProfileTask.CreationAction(variant))
        taskFactory.register(CompileArtProfileTask.CreationAction(variant))

        if (variant.buildFeatures.dataBinding
                && globalConfig.hasDynamicFeatures) {
            // Create a task that will write the namespaces of all features into a file. This file's
            // path is passed into the Data Binding annotation processor which uses it to know about
            // all available features.
            //
            // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(ComponentCreationConfig)}
            taskFactory.register(
                DataBindingExportFeatureNamespacesTask.CreationAction(variant)
            )
        }

        createDynamicBundleTask(variantInfo)

        handleMicroApp(variant)

        val publishInfo = variant.publishInfo!!

        for (component in publishInfo.components) {
            val configType = if (component.type == AbstractPublishing.Type.APK) {
                PublishedConfigType.APK_PUBLICATION
            } else {
                PublishedConfigType.AAB_PUBLICATION
            }
            createSoftwareComponent(
                variant,
                component.componentName,
                configType
            )
        }
    }

    /** Configure variantData to generate embedded wear application.  */
    private fun handleMicroApp(appVariant: ApplicationCreationConfig) {
        val componentType = appVariant.componentType
        if (componentType.isBaseModule) {
            val unbundledWearApp: Boolean? = appVariant.isWearAppUnbundled
            if (unbundledWearApp != true && appVariant.embedsMicroApp) {
                val wearApp =
                        appVariant.variantDependencies.wearAppConfiguration
                        ?: error("Wear app with no wearApp configuration")
                if (!wearApp.allDependencies.isEmpty()) {
                    val setApkArtifact =
                        Action { container: AttributeContainer ->
                            container.attribute(
                                AndroidArtifacts.ARTIFACT_TYPE,
                                AndroidArtifacts.ArtifactType.APK.type
                            )
                        }
                    val files = wearApp.incoming
                        .artifactView { config: ArtifactView.ViewConfiguration ->
                            config.attributes(
                                setApkArtifact
                            )
                        }
                        .files
                    createGenerateMicroApkDataTask(appVariant, files)
                }
            } else {
                if (unbundledWearApp == true) {
                    createGenerateMicroApkDataTask(appVariant)
                }
            }
        }
    }

    /**
     * Creates the task that will handle micro apk.
     *
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled anymore,
     * but we still have an XML resource packaged, and a custom entry in the manifest. This is
     * triggered by passing a null [Configuration] object.
     *
     * @param appVariant the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     * if null this will trigger the unbundled mode.
     */
    private fun createGenerateMicroApkDataTask(
        appVariant: ApplicationCreationConfig,
        config: FileCollection? = null
    ) {
        val generateMicroApkTask =
            taskFactory.register(
                GenerateApkDataTask.CreationAction(appVariant, config)
            )
        // the merge res task will need to run after this one.
        appVariant.taskContainer.resourceGenTask.dependsOn(
            generateMicroApkTask
        )
    }

    private fun createAssetPackTasks(appVariant: ApplicationCreationConfig) {
        val assetPackFilesConfiguration =
            project.configurations.maybeCreate("assetPackFiles")
        val assetPackManifestConfiguration =
            project.configurations.maybeCreate("assetPackManifest")
        val assetPacks = globalConfig.assetPacks
        populateAssetPacksConfigurations(
            project,
            appVariant.services.issueReporter,
            assetPacks,
            assetPackFilesConfiguration,
            assetPackManifestConfiguration
        )

        if (assetPacks.isNotEmpty()) {
            val assetPackManifest =
                assetPackManifestConfiguration.incoming.artifacts
            val assetFiles = assetPackFilesConfiguration.incoming.files

            taskFactory.register(
                ProcessAssetPackManifestTask.CreationAction(
                        appVariant,
                    assetPackManifest
                )
            )
            taskFactory.register(
                LinkManifestForAssetPackTask.CreationAction(
                        appVariant
                )
            )
            taskFactory.register(
                AssetPackPreBundleTask.CreationAction(
                        appVariant,
                        assetFiles
                )
            )
        }
    }

    private fun createDynamicBundleTask(variantInfo: ComponentInfo<ApplicationVariantBuilder, ApplicationCreationConfig>) {
        val variant = variantInfo.variant

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (globalConfig.namespacedAndroidResources) {
            return
        }

        taskFactory.register(PerModuleBundleTask.CreationAction(variant))

        val debuggable = variantInfo.variantBuilder.debuggable
        val includeSdkInfoInApk = variantInfo.variantBuilder.dependenciesInfo.includedInApk
        val includeSdkInfoInBundle = variantInfo.variantBuilder.dependenciesInfo.includedInBundle
        if (!debuggable) {
            taskFactory.register(PerModuleReportDependenciesTask.CreationAction(variant))
        }
        if (variant.componentType.isBaseModule) {
            taskFactory.register(ParseIntegrityConfigTask.CreationAction(variant))
            taskFactory.register(PackageBundleTask.CreationAction(variant))
            if (!debuggable) {
                if (includeSdkInfoInBundle) {
                    taskFactory.register(BundleReportDependenciesTask.CreationAction(variant))
                }
                if (includeSdkInfoInApk && variant.services
                        .projectOptions[BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS]) {
                    taskFactory.register(SdkDependencyDataGeneratorTask.CreationAction(variant))
                }
            }
            taskFactory.register(FinalizeBundleTask.CreationAction(variant))
            if (variant.services.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT]) {
                taskFactory.register(
                        GeneratePrivacySandboxSdkRuntimeConfigFile.CreationAction(variant))
                variant.artifacts.appendTo(
                        MultipleArtifact.ALL_CLASSES_JARS,
                        InternalArtifactType.PRIVACY_SANDBOX_SDK_R_PACKAGE_JAR)
            }

            taskFactory.register(BundleIdeModelProducerTask.CreationAction(variant))
            taskFactory.register(
                ListingFileRedirectTask.CreationAction(
                    variant,
                    "Bundle",
                    InternalArtifactType.BUNDLE_IDE_MODEL,
                    InternalArtifactType.BUNDLE_IDE_REDIRECT_FILE
                )
            )
            taskFactory.register(BundleToApkTask.CreationAction(variant))
            taskFactory.register(BundleToStandaloneApkTask.CreationAction(variant))
            taskFactory.register(ExtractApksTask.CreationAction(variant))
            taskFactory.register(
                ListingFileRedirectTask.CreationAction(
                    variant,
                    "ApksFromBundle",
                    InternalArtifactType.APK_FROM_BUNDLE_IDE_MODEL,
                    InternalArtifactType.APK_FROM_BUNDLE_IDE_REDIRECT_FILE
                )
            )

            taskFactory.register(AnchorTaskNames.getExtractApksAnchorTaskName(variant)) {
                it.dependsOn(variant.artifacts.get(
                    InternalArtifactType.EXTRACTED_APKS
                ))
            }

            taskFactory.register(MergeNativeDebugMetadataTask.CreationAction(variant))
            variant.taskContainer.assembleTask.configure { task ->
                task.dependsOn(variant.artifacts.get(InternalArtifactType.MERGED_NATIVE_DEBUG_METADATA))
            }
        }
    }

    private fun createSoftwareComponent(
        appVariant: ApplicationCreationConfig,
        componentName: String,
        publication: PublishedConfigType
    ) {
        val component = localConfig.componentFactory.adhoc(componentName)
        val config = appVariant.variantDependencies.getElements(PublishedConfigSpec(publication, componentName, false))!!
        component.addVariantsFromConfiguration(config) { }
        project.components.add(component)
    }

    override fun createInstallTask(creationConfig: ApkCreationConfig) {
        if ( globalConfig.services.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT] && !creationConfig.componentType.isForTesting) {
            taskFactory.register(BuildPrivacySandboxSdkApks.CreationAction(creationConfig))
        }
        if (!globalConfig.hasDynamicFeatures ||
            creationConfig is AndroidTestCreationConfig
        ) {
            // no dynamic features means we can just use the standard install task
            super.createInstallTask(creationConfig)
        } else {
            // need to install via bundle
            taskFactory.register(InstallVariantViaBundleTask.CreationAction(creationConfig))
        }
    }
}
