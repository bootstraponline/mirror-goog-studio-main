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

package com.android.build.gradle.internal.plugins

import com.android.SdkConstants
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ExecutionProfile
import com.android.build.api.dsl.SettingsExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.build.gradle.internal.ApiObjectFactory
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.ClasspathVerifier.checkClasspathSanity
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkLocator.sdkTestDirectory
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.DEFAULT_EXECUTION_PROFILE
import com.android.build.gradle.internal.core.ExecutionProfileOptions
import com.android.build.gradle.internal.core.SettingsOptions
import com.android.build.gradle.internal.core.ToolExecutionOptions
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.crash.runAction
import com.android.build.gradle.internal.dependency.CONFIG_NAME_ANDROID_JDK_IMAGE
import com.android.build.gradle.internal.dependency.JacocoInstrumentationService
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.IncompatibleProjectOptionsReporter
import com.android.build.gradle.internal.getManagedDeviceAvdFolder
import com.android.build.gradle.internal.getSdkDir
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.ide.v2.NativeModelBuilder
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfigImpl
import com.android.build.gradle.internal.utils.enforceMinimumVersionsOfPlugins
import com.android.build.gradle.internal.utils.getKotlinPluginVersion
import com.android.build.gradle.internal.utils.syncAgpAndKgpSources
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter.Type
import com.android.builder.model.v2.ide.ProjectType
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions.checkState
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Base class for all Android plugins */
@Suppress("UnstableApiUsage")
abstract class BasePlugin<
                BuildFeaturesT: BuildFeatures,
                BuildTypeT: com.android.build.api.dsl.BuildType,
                DefaultConfigT: com.android.build.api.dsl.DefaultConfig,
                ProductFlavorT: com.android.build.api.dsl.ProductFlavor,
                AndroidT: CommonExtension<
                        BuildFeaturesT,
                        BuildTypeT,
                        DefaultConfigT,
                        ProductFlavorT>,
                AndroidComponentsT:
                        AndroidComponentsExtension<
                                in AndroidT,
                                in VariantBuilderT,
                                in VariantT>,
                VariantBuilderT: VariantBuilder,
                VariantDslInfoT: VariantDslInfo,
                CreationConfigT: VariantCreationConfig,
                VariantT: Variant>(
    val registry: ToolingModelBuilderRegistry,
    val componentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    init {
        checkClasspathSanity()
    }

    protected class ExtensionData<
            BuildFeaturesT: BuildFeatures,
            BuildTypeT: com.android.build.api.dsl.BuildType,
            DefaultConfigT: com.android.build.api.dsl.DefaultConfig,
            ProductFlavorT: com.android.build.api.dsl.ProductFlavor,
            AndroidT: CommonExtension<
                    out BuildFeaturesT,
                    out BuildTypeT,
                    out DefaultConfigT,
                    out ProductFlavorT>>(
        val oldExtension: BaseExtension,
        val newExtension: AndroidT,
        val bootClasspathConfig: BootClasspathConfigImpl,
    )

    @Suppress("DEPRECATION")
    private val buildOutputs by lazy {
        withProject("buildOutputs") {
            it.container(
                com.android.build.gradle.api.BaseVariantOutput::class.java
            )
        }
    }

    private val extensionData by lazy {
        createExtension(
            dslServices,
            variantInputModel,
            buildOutputs,
            extraModelInfo,
            versionedSdkLoaderService
        )
    }

    // TODO: BaseExtension should be changed into AndroidT
    @Deprecated("use newExtension")
    val extension: BaseExtension by lazy { extensionData.oldExtension }
    private val newExtension: AndroidT by lazy { extensionData.newExtension }

    private val variantApiOperations by lazy {
        VariantApiOperationsRegistrar<AndroidT, VariantBuilderT, VariantT>(
            extensionData.newExtension
        )
    }

    private val globalConfig by lazy {
        withProject("globalConfig") { project ->
            @Suppress("DEPRECATION")
            GlobalTaskCreationConfigImpl(
                project,
                extension,
                (newExtension as CommonExtensionImpl<*, *, *, *>),
                dslServices,
                versionedSdkLoaderService,
                bootClasspathConfig,
                createCustomLintPublishConfig(project),
                createCustomLintChecksConfig(project),
                createAndroidJarConfig(project),
                createSettingsOptions()
            )
        }
    }


    @get:VisibleForTesting
    val variantManager: VariantManager<AndroidT, VariantBuilderT, VariantDslInfoT, CreationConfigT> by lazy {
        withProject("variantManager") { project ->
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            VariantManager(
                project,
                dslServices,
                extension,
                newExtension,
                variantApiOperations as VariantApiOperationsRegistrar<AndroidT, VariantBuilder, Variant>,
                variantFactory,
                variantInputModel,
                globalConfig,
                projectServices
            )
        }
    }


    @get:VisibleForTesting
    val variantInputModel: LegacyVariantInputManager by lazy {
        withProject("LegacyVariantInputManager") { project ->
            LegacyVariantInputManager(
            dslServices,
            variantFactory.componentType,
            SourceSetManager(
                project,
                isPackagePublished(),
                dslServices,
                DelayedActionsExecutor()
            ))
        }
    }

    private val sdkComponentsBuildService by lazy {
        withProject("sdkComponentsBuildService") { project ->
            SdkComponentsBuildService.RegistrationAction(project, projectServices.projectOptions)
                .execute()
        }
    }

    protected val dslServices: DslServicesImpl by lazy {
        DslServicesImpl(
            projectServices,
            sdkComponentsBuildService
        ) {
            versionedSdkLoaderService
        }
    }

    private val taskManagerConfig: TaskManagerConfig by lazy {
        TaskManagerConfigImpl(dslServices, componentFactory)
    }

    protected val versionedSdkLoaderService: VersionedSdkLoaderService by lazy {
        withProject("versionedSdkLoaderService") { project ->
                VersionedSdkLoaderService(
                    dslServices,
                    project,
                    {
                        @Suppress("DEPRECATION")
                        extension.compileSdkVersion
                    },
                    {
                        @Suppress("DEPRECATION")
                        extension.buildToolsRevision
                    },
                )
            }
        }

    private val bootClasspathConfig: BootClasspathConfigImpl by lazy {
        extensionData.bootClasspathConfig
    }

    private val variantFactory: VariantFactory<VariantBuilderT, VariantDslInfoT, CreationConfigT> by lazy {
        createVariantFactory()
    }

    protected val extraModelInfo: ExtraModelInfo = ExtraModelInfo()

    private val hasCreatedTasks = AtomicBoolean(false)

    protected abstract fun createExtension(
        dslServices: DslServices,
        dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        @Suppress("DEPRECATION")
        buildOutputs: NamedDomainObjectContainer<com.android.build.gradle.api.BaseVariantOutput>,
        extraModelInfo: ExtraModelInfo,
        versionedSdkLoaderService: VersionedSdkLoaderService
    ): ExtensionData<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidT>

    protected abstract fun createComponentExtension(
        dslServices: DslServices,
        variantApiOperationsRegistrar: VariantApiOperationsRegistrar<AndroidT, VariantBuilderT, VariantT>,
        bootClasspathConfig: BootClasspathConfig
    ): AndroidComponentsT

    abstract override fun getAnalyticsPluginType(): GradleBuildProject.PluginType

    protected abstract fun createVariantFactory(): VariantFactory<VariantBuilderT, VariantDslInfoT, CreationConfigT>

    protected abstract fun createTaskManager(
        project: Project,
        variants: Collection<ComponentInfo<VariantBuilderT, CreationConfigT>>,
        testComponents: Collection<TestComponentCreationConfig>,
        testFixturesComponents: Collection<TestFixturesCreationConfig>,
        globalTaskCreationConfig: GlobalTaskCreationConfig,
        localConfig: TaskManagerConfig,
        extension: BaseExtension,
    ): TaskManager<VariantBuilderT, CreationConfigT>

    protected abstract fun getProjectType(): Int

    /** The project type of the IDE model v2. */
    protected abstract fun getProjectTypeV2(): ProjectType

    override fun apply(project: Project) {
        runAction {
            basePluginApply(project)
            pluginSpecificApply(project)
            project.pluginManager.apply(AndroidBasePlugin::class.java)
        }
    }

    protected abstract fun pluginSpecificApply(project: Project)

    override fun configureProject(project: Project) {
        val gradle = project.gradle

        val stringCachingService: Provider<StringCachingBuildService> =
            StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()

        LibraryDependencyCacheBuildService.RegistrationAction(
                project, mavenCoordinatesCacheBuildService
        ).execute()

        GlobalSyncService.RegistrationAction(project, mavenCoordinatesCacheBuildService)
            .execute()

        val projectOptions = projectServices.projectOptions
        val issueReporter = projectServices.issueReporter

        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        val locationsProvider = getBuildService(
            project.gradle.sharedServices,
            AndroidLocationsBuildService::class.java,
        )

        AvdComponentsBuildService.RegistrationAction(
            project,
            projectOptions,
            getManagedDeviceAvdFolder(
                project.objects,
                project.providers,
                locationsProvider.get()
            ),
            sdkComponentsBuildService,
            project.providers.provider {
                @Suppress("DEPRECATION")
                extension.compileSdkVersion
            },
            project.providers.provider {
                @Suppress("DEPRECATION")
                extension.buildToolsRevision
            },
        ).execute()

        SymbolTableBuildService.RegistrationAction(project).execute()
        ClassesHierarchyBuildService.RegistrationAction(project).execute()
        LintFixBuildService.RegistrationAction(project).execute()
        LintClassLoaderBuildService.RegistrationAction(project).execute()
        JacocoInstrumentationService.RegistrationAction(project).execute()

        projectOptions
            .allOptions
            .forEach(projectServices.deprecationReporter::reportOptionIssuesIfAny)
        IncompatibleProjectOptionsReporter.check(projectOptions, issueReporter)

        // Enforce minimum versions of certain plugins
        enforceMinimumVersionsOfPlugins(project, issueReporter)

        // Apply the Java plugin
        project.plugins.apply(JavaBasePlugin::class.java)

        project.tasks
            .named("assemble")
            .configure { task ->
                task.description = "Assembles all variants of all applications and secondary packages."
            }

        // As soon as project is evaluated we can clear the shared state for deprecation reporting.
        gradle.projectsEvaluated { DeprecationReporterImpl.clean() }

        createAndroidJdkImageConfiguration(project)
    }

    /** Creates the androidJdkImage configuration */
    private fun createAndroidJdkImageConfiguration(project: Project) {
        val config = project.configurations.create(CONFIG_NAME_ANDROID_JDK_IMAGE)
        config.isVisible = false
        config.isCanBeConsumed = false
        config.description = "Configuration providing JDK image for compiling Java 9+ sources"

        project.dependencies
            .add(
                CONFIG_NAME_ANDROID_JDK_IMAGE,
                project.files(
                    versionedSdkLoaderService
                        .versionedSdkLoader
                        .flatMap { it.coreForSystemModulesProvider }
                )
            )
    }

    companion object {
        fun createCustomLintChecksConfig(project: Project): Configuration {
            val lintChecks = project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_LINTCHECKS)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to apply external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        private fun createCustomLintPublishConfig(project: Project): Configuration {
            val lintChecks = project.configurations
                .maybeCreate(VariantDependencies.CONFIG_NAME_LINTPUBLISH)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to publish external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        private fun createAndroidJarConfig(project: Project): Configuration  {
            val androidJarConfig: Configuration = project.configurations
                .maybeCreate(VariantDependencies.CONFIG_NAME_ANDROID_APIS)
            androidJarConfig.description = "Configuration providing various types of Android JAR file"
            androidJarConfig.isCanBeConsumed = false
            return androidJarConfig
        }
    }

    override fun configureExtension(project: Project) {
        // Create components extension
        createComponentExtension(
            dslServices,
            variantApiOperations,
            bootClasspathConfig
        )
        project.extensions.add("buildOutputs", buildOutputs)
        registerModels(
            project,
            registry,
            variantInputModel,
            extensionData,
            extraModelInfo,
            globalConfig)

        // create default Objects, signingConfig first as it's used by the BuildTypes.
        variantFactory.createDefaultComponents(variantInputModel)
        createAndroidTestUtilConfiguration(project)
    }

    protected open fun registerModels(
        project: Project,
        registry: ToolingModelBuilderRegistry,
        variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        extensionData: ExtensionData<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidT>,
        extraModelInfo: ExtraModelInfo,
        globalConfig: GlobalTaskCreationConfig
    ) {
        // Register a builder for the custom tooling model
        val variantModel: VariantModel = createVariantModel(globalConfig)
        registerModelBuilder(project, registry, variantModel, extensionData.oldExtension, extraModelInfo)
        registry.register(
            com.android.build.gradle.internal.ide.v2.ModelBuilder(
                project, variantModel, extensionData.newExtension
            )
        )

        // Register a builder for the native tooling model
        val nativeModelBuilderV2 = NativeModelBuilder(
            project,
            projectServices.issueReporter,
            projectServices.projectOptions,
            variantModel
        )
        registry.register(nativeModelBuilderV2)
    }

    private fun createVariantModel(globalConfig: GlobalTaskCreationConfig): VariantModel  {
        return VariantModelImpl(
            variantInputModel as VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
            {
                @Suppress("DEPRECATION")
                extension.testBuildType
            },
            { variantManager.mainComponents.map { it.variant } },
            { variantManager.testComponents },
            { variantManager.buildFeatureValues },
            getProjectType(),
            getProjectTypeV2(),
            globalConfig)
    }

    /** Registers a builder for the custom tooling model.  */
    protected open fun registerModelBuilder(
        project: Project,
        registry: ToolingModelBuilderRegistry,
        variantModel: VariantModel,
        extension: BaseExtension,
        extraModelInfo: ExtraModelInfo
    ) {
        registry.register(
            ModelBuilder(
                project, variantModel, extension, extraModelInfo
            )
        )
    }

    override fun createTasks(project: Project) {
        configuratorService.recordBlock(
            ExecutionType.TASK_MANAGER_CREATE_TASKS,
            project.path,
            null
        ) {
            @Suppress("DEPRECATION")
            TaskManager.createTasksBeforeEvaluate(
                project,
                variantFactory.componentType,
                extension.sourceSets,
                variantManager.globalTaskCreationConfig
            )
        }

        project.afterEvaluate(
            afterEvaluate {
                variantInputModel.sourceSetManager.runBuildableArtifactsActions()

                configuratorService.recordBlock(
                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                    project.path,
                    null
                ) {
                    createAndroidTasks(project)
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    @VisibleForTesting
    fun createAndroidTasks(project: Project) {
        val globalConfig = variantManager.globalTaskCreationConfig
        if (hasCreatedTasks.get()) {
            return
        }
        hasCreatedTasks.set(true)
        variantManager.variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        if (extension.compileSdkVersion == null) {
            if (SyncOptions.getModelQueryMode(projectServices.projectOptions)
                == SyncOptions.EvaluationMode.IDE
            ) {
                val newCompileSdkVersion: String = findHighestSdkInstalled()
                    ?: ("android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
                extension.compileSdkVersion = newCompileSdkVersion
            }
            dslServices
                .issueReporter
                .reportError(
                    Type.COMPILE_SDK_VERSION_NOT_SET,
                    "compileSdkVersion is not specified. Please add it to build.gradle"
                )
        }

        // Make sure unit tests set the required fields.
        checkState(extension.compileSdkVersion != null, "compileSdkVersion is not specified.")

        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin::class.java)) {
            throw BadPluginException(
                "The 'java' plugin has been applied, but it is not compatible with the Android plugins."
            )
        }
        if (project.plugins.hasPlugin("me.tatarka.retrolambda")) {
            val warningMsg =
                """One of the plugins you are using supports Java 8 language features. To try the support built into the Android plugin, remove the following from your build.gradle:
    apply plugin: 'me.tatarka.retrolambda'
To learn more, go to https://d.android.com/r/tools/java-8-support-message.html
"""
            dslServices.issueReporter.reportWarning(Type.GENERIC, warningMsg)
        }
        project.repositories
            .forEach(
                Consumer { artifactRepository: ArtifactRepository ->
                    if (artifactRepository is FlatDirectoryArtifactRepository) {
                        val warningMsg = String.format(
                            "Using %s should be avoided because it doesn't support any meta-data formats.",
                            artifactRepository.getName()
                        )
                        dslServices
                            .issueReporter
                            .reportWarning(Type.GENERIC, warningMsg)
                    }
                })

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if ((!project.state.executed || project.state.failure != null)
            && sdkTestDirectory == null
        ) {
            return
        }
        variantInputModel.lock()
        extension.disableWrite()

        @Suppress("DEPRECATION")
        syncAgpAndKgpSources(project, extension.sourceSets)

        val projectBuilder = configuratorService.getProjectBuilder(
            project.path
        )
        if (projectBuilder != null) {
            projectBuilder
                .setCompileSdk(extension.compileSdkVersion)
                .setBuildToolsVersion(extension.buildToolsRevision.toString()).splits =
                AnalyticsUtil.toProto(extension.splits)
            getKotlinPluginVersion(project)?.let {
                projectBuilder.kotlinPluginVersion = it
            }
        }
        AnalyticsUtil.recordFirebasePerformancePluginVersion(project)

        // create the build feature object that will be re-used everywhere
        val buildFeatureValues = variantFactory.createBuildFeatureValues(
            extension.buildFeatures, projectServices.projectOptions
        )

        // create all registered custom source sets from the user on each AndroidSourceSet
        variantManager
            .variantApiOperationsRegistrar
            .onEachSourceSetExtensions { name: String ->
                extension
                    .sourceSets
                    .forEach(
                        Consumer { androidSourceSet: com.android.build.gradle.api.AndroidSourceSet? ->
                            if (androidSourceSet is DefaultAndroidSourceSet) {
                                androidSourceSet.extras.create(name)
                            }
                        })
            }
        variantManager.createVariants(buildFeatureValues)
        val variants = variantManager.mainComponents
        val taskManager = createTaskManager(
            project,
            variants,
            variantManager.testComponents,
            variantManager.testFixturesComponents,
            globalConfig,
            taskManagerConfig,
            extension
        )
        taskManager.createTasks(variantFactory.componentType, createVariantModel(globalConfig))
        DependencyConfigurator(
            project,
            projectServices
        )
            .configureDependencySubstitutions()
            .configureDependencyChecks()
            .configureGeneralTransforms(globalConfig.namespacedAndroidResources)
            .configureVariantTransforms(variants, variantManager.nestedComponents, globalConfig)
            .configureAttributeMatchingStrategies(variantInputModel)
            .configureJacocoTransforms()
            .configureCalculateStackFramesTransforms(globalConfig)
            .configurePrivacySandboxSdkConsumerTransforms(
                    globalConfig.compileSdkHashString, globalConfig.buildToolsRevision, globalConfig)

        // Run the old Variant API, after the variants and tasks have been created.
        @Suppress("DEPRECATION")
        val apiObjectFactory = ApiObjectFactory(extension, variantFactory, dslServices)
        for (variant in variants) {
            apiObjectFactory.create(variant.variant)
        }

        // lock the Properties of the variant API after the old API because
        // of the versionCode/versionName properties that are shared between the old and new APIs.
        variantManager.lockVariantProperties()

        // Make sure no SourceSets were added through the DSL without being properly configured
        variantInputModel.sourceSetManager.checkForUnconfiguredSourceSets()

        // configure compose related tasks.
        taskManager.createPostApiTasks()

        // now publish all variant artifacts for non test variants since
        // tests don't publish anything.
        for (component in variants) {
            component.variant.publishBuildArtifacts()
        }

        // now publish all testFixtures components artifacts.
        for (testFixturesComponent in variantManager.testFixturesComponents) {
            testFixturesComponent.publishBuildArtifacts()
        }
        checkSplitConfiguration()
        variantManager.setHasCreatedTasks(true)
        variantManager.finalizeAllVariants()
    }

    private fun findHighestSdkInstalled(): String? {
        var highestSdk: String? = null
        val folder = withProject("findHighestSdkInstalled") { project ->
            File(getSdkDir(project.rootDir, syncIssueReporter), "platforms")
        }
        val listOfFiles = folder.listFiles()
        if (listOfFiles != null) {
            Arrays.sort(listOfFiles, Comparator.comparing { obj: File -> obj.name }
                .reversed())
            for (file in listOfFiles) {
                if (AndroidTargetHash.getPlatformVersion(file.name) != null) {
                    highestSdk = file.name
                    break
                }
            }
        }
        return highestSdk
    }

    private fun checkSplitConfiguration() {
        val configApkUrl = "https://d.android.com/topic/instant-apps/guides/config-splits.html"
        @Suppress("DEPRECATION")
        val generatePureSplits = extension.generatePureSplits
        @Suppress("DEPRECATION")
        val splits = extension.splits

        // The Play Store doesn't allow Pure splits
        if (generatePureSplits) {
            dslServices
                .issueReporter
                .reportWarning(
                    Type.GENERIC,
                    "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                            + configApkUrl
                )
        }
        if (!generatePureSplits && splits.language.isEnable) {
            dslServices
                .issueReporter
                .reportWarning(
                    Type.GENERIC,
                    "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                            + configApkUrl
                )
        }
    }

    /**
     * If overridden in a subclass to return "true," the package Configuration will be named
     * "publish" instead of "apk"
     */
    protected open fun isPackagePublished(): Boolean {
        return false
    }

    private val settingsExtension: SettingsExtension? by lazy(LazyThreadSafetyMode.NONE) {
        // Query for the settings extension via extra properties.
        // This is deposited here by the SettingsPlugin
        val properties = project?.extensions?.extraProperties
        if (properties == null) {
            null
        } else if (properties.has("_android_settings")) {
            properties.get("_android_settings") as? SettingsExtension
        } else {
            null
        }
    }

    // Initialize the android extension with values from the android settings extension
    protected fun initExtensionFromSettings(extension: AndroidT) {
        settingsExtension?.let {
            extension.doInitExtensionFromSettings(it)
        }
    }

    protected open fun AndroidT.doInitExtensionFromSettings(settings: SettingsExtension) {
        settings.compileSdk?.let { compileSdk ->
            this.compileSdk = compileSdk

            settings.compileSdkExtension?.let { compileSdkExtension ->
                this.compileSdkExtension = compileSdkExtension
            }
        }

        settings.compileSdkPreview?.let { compileSdkPreview ->
            this.compileSdkPreview = compileSdkPreview
        }

        settings.minSdk?.let { minSdk ->
            this.defaultConfig.minSdk = minSdk
        }

        settings.minSdkPreview?.let { minSdkPreview ->
            this.defaultConfig.minSdkPreview = minSdkPreview
        }

        settings.ndkVersion?.let { ndkVersion ->
            this.ndkVersion = ndkVersion
        }

        settings.ndkPath?.let { ndkPath ->
            this.ndkPath = ndkPath
        }

        settings.buildToolsVersion?.let { buildToolsVersion ->
            this.buildToolsVersion = buildToolsVersion
        }
    }

    // Create settings options, to be used in the global config,
    // with values from the android settings extension
    private fun createSettingsOptions(): SettingsOptions {
        // resolve settings extension
        val actualSettingsExtension = settingsExtension ?: run {
            dslServices.logger.info("Using default execution profile")
            return SettingsOptions(DEFAULT_EXECUTION_PROFILE)
        }

        // Map the profiles to make it easier to look them up
        val executionProfiles = actualSettingsExtension.execution.profiles.associate { profile ->
            profile.name to profile
        }

        val buildProfileOptions = { profile: ExecutionProfile ->
            ExecutionProfileOptions(
                name = profile.name,
                r8Options = profile.r8.let { r8 ->
                    ToolExecutionOptions(
                        jvmArgs = r8.jvmOptions,
                        runInSeparateProcess = r8.runInSeparateProcess
                    )
                }
            )
        }

        // If the string option is set use that one instead
        val actualProfileName =
            dslServices.projectOptions[StringOption.EXECUTION_PROFILE_SELECTION] ?:
            actualSettingsExtension.execution.defaultProfile
        // Find the selected (or the only) profile
        val executionProfile =
            if (actualProfileName == null) {
                if (executionProfiles.isEmpty()) { // No profiles declared, and none selected, return default
                    dslServices.logger.info("Using default execution profile")
                    DEFAULT_EXECUTION_PROFILE
                } else if (executionProfiles.size == 1) { // if there is exactly one profile use that
                    dslServices.logger.info("Using only execution profile '${executionProfiles.keys.first()}'")
                    buildProfileOptions(executionProfiles.values.first())
                } else { // no profile selected
                    dslServices.issueReporter.reportError(Type.GENERIC, "Found ${executionProfiles.size} execution profiles ${executionProfiles.keys}, but no profile was selected.\n")
                    null
                }
            } else {
                if (!executionProfiles.containsKey(actualProfileName)) { // invalid profile selected
                    dslServices.issueReporter.reportError(Type.GENERIC,"Selected profile '$actualProfileName' does not exist")
                    null
                } else {
                    if (actualProfileName == dslServices.projectOptions[StringOption.EXECUTION_PROFILE_SELECTION]) {
                        dslServices.logger.info("Using execution profile from android.settings.executionProfile '$actualProfileName'")
                    } else {
                        dslServices.logger.info("Using execution profile from dsl '$actualProfileName'")
                    }

                    buildProfileOptions(executionProfiles[actualProfileName]!!)
                }
            }

        return SettingsOptions(executionProfile = executionProfile)
    }

    // Create the "special" configuration for test buddy APKs. It will be resolved by the test
    // running task, so that we can install all the found APKs before running tests.
    private fun createAndroidTestUtilConfiguration(project: Project) {
        project.logger
            .debug(
                "Creating configuration "
                        + SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION
            )
        val configuration = project.configurations
            .maybeCreate(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
        configuration.isVisible = false
        configuration.description = "Additional APKs used during instrumentation testing."
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
    }
}
