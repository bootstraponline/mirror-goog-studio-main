/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.android.build.gradle

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.transform.Transform
import com.android.build.api.variant.VariantFilter
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.ActionableVariantObjectOperationsExecutor
import com.android.build.gradle.internal.dsl.AdbOptions
import com.android.build.gradle.internal.dsl.BuildFeaturesImpl
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ComposeOptions
import com.android.build.gradle.internal.dsl.ComposeOptionsImpl
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.DexOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.dsl.ViewBindingOptionsImpl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.BuilderConstants
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SourceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import com.android.resources.Density
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.SourceSet
import java.io.File

/**
 * Base extension for all Android plugins.
 *
 * You don't use this extension directly. Instead, use one of the following:
 *
 * * [ApplicationExtension]: `android` extension for the `com.android.application` plugin
 *         used to create an Android app.
 * * [LibraryExtension]: `android` extension for the `com.android.library` plugin used to
 *         [create an Android library](https://developer.android.com/studio/projects/android-library.html)
 * * [TestExtension]: `android` extension for the `com.android.test` plugin used to create
 *         a separate android test project.
 * * [DynamicFeatureExtension]: `android` extension for the `com.android.feature` plugin
 *         used to create dynamic features.
 *
 * The following applies the Android plugin to an app project `build.gradle` file:
 *
 * ```
 * // Applies the application plugin and makes the 'android' block available to specify
 * // Android-specific build options.
 * apply plugin: 'com.android.application'
 * ```
 *
 * To learn more about creating and organizing Android projects, read
 * [Projects Overview](https://developer.android.com/studio/projects/index.html)
 */
// All the public methods are meant to be exposed in the DSL. We can't use lambdas in this class
// (yet), because the DSL reference generator doesn't understand them.
abstract class BaseExtension protected constructor(
    protected val project: Project,
    projectOptions: ProjectOptions,
    protected val globalScope: GlobalScope,
    override val buildTypes: NamedDomainObjectContainer<BuildType>,
    override val productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    override val signingConfigs: NamedDomainObjectContainer<SigningConfig>,
    /** All build outputs for all variants, can be used by users to customize a build output. */
    override val buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    private val sourceSetManager: SourceSetManager,
    private val extraModelInfo: ExtraModelInfo,
    private val isBaseModule: Boolean
) : AndroidConfig, ActionableVariantObjectOperationsExecutor {

    private val _transforms: MutableList<Transform> = mutableListOf()
    /** Secondary dependencies for the custom transform. */
    private val _transformDependencies: MutableList<List<Any>> = mutableListOf()

    private val objectFactory = project.objects

    override val defaultConfig: DefaultConfig =
        objectFactory.newInstance(
            DefaultConfig::class.java,
            BuilderConstants.MAIN,
            project,
            globalScope.dslScope
        )
    override val aaptOptions: AaptOptions =
        objectFactory.newInstance(
            AaptOptions::class.java,
            projectOptions.get(BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT)
        )
    override val lintOptions: LintOptions =
        objectFactory.newInstance(LintOptions::class.java)
    override val externalNativeBuild: ExternalNativeBuild =
        objectFactory.newInstance(ExternalNativeBuild::class.java, objectFactory, project)
    override val dexOptions: DexOptions =
        objectFactory.newInstance(DexOptions::class.java, extraModelInfo.deprecationReporter)
    override val testOptions: TestOptions =
        objectFactory.newInstance(TestOptions::class.java, objectFactory)
    override val compileOptions: CompileOptions =
        objectFactory.newInstance(CompileOptions::class.java)
    override val packagingOptions: PackagingOptions =
        objectFactory.newInstance(PackagingOptions::class.java)
    override val jacoco: JacocoOptions =
        objectFactory.newInstance(JacocoOptions::class.java)
    override val splits: Splits =
        objectFactory.newInstance(Splits::class.java, objectFactory)
    override val adbOptions: AdbOptions =
        objectFactory.newInstance(AdbOptions::class.java)

    private val deviceProviderList: MutableList<DeviceProvider> = Lists.newArrayList()
    private val testServerList: MutableList<TestServer> = Lists.newArrayList()
    private val transformList: MutableList<Transform> = Lists.newArrayList()


    val buildFeatures: BuildFeatures =
        objectFactory.newInstance(BuildFeaturesImpl::class.java)

    @Incubating
    @get:Incubating
    val composeOptions: ComposeOptions =
        objectFactory.newInstance(ComposeOptionsImpl::class.java)

    override val dataBinding: DataBindingOptions =
        objectFactory.newInstance(
            DataBindingOptions::class.java,
            buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    val viewBinding: ViewBindingOptions =
        objectFactory.newInstance(
            ViewBindingOptionsImpl::class.java,
            buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    private var target: String? = null

    final override var buildToolsRevision: Revision =
        ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION
        private set

    override val libraryRequests: MutableCollection<LibraryRequest> = mutableListOf()

    override val flavorDimensionList: MutableList<String> = mutableListOf()

    private var _resourcePrefix: String? = null

    override val resourcePrefix: String?
        get() = _resourcePrefix

    override var defaultPublishConfig: String = "release"

    override var variantFilter: Action<VariantFilter>? = null

    protected val logger: Logger = Logging.getLogger(this::class.java)

    private var isWritable = true

    override var ndkVersion: String? = null

    init {
        // Create the "special" configuration for test buddy APKs. It will be resolved by the test
        // running task, so that we can install all the found APKs before running tests.
        createAndroidTestUtilConfiguration()

        sourceSetManager.setUpSourceSet(defaultConfig.name)
        setDefaultConfigValues()
    }

    private fun setDefaultConfigValues() {
        val densities = Density.getRecommendedValuesForDevice()
        val strings = Sets.newHashSetWithExpectedSize<String>(densities.size)
        for (density in densities) {
            strings.add(density.resourceValue)
        }
        defaultConfig.vectorDrawables.setGeneratedDensities(strings)
        defaultConfig.vectorDrawables.useSupportLibrary = false
    }

    /**
     * Disallow further modification on the extension.
     */
    fun disableWrite() {
        isWritable = false
    }

    protected fun checkWritability() {
        if (!isWritable) {
            throw GradleException(
                "Android tasks have already been created.\n" +
                        "This happens when calling android.applicationVariants,\n" +
                        "android.libraryVariants or android.testVariants.\n" +
                        "Once these methods are called, it is not possible to\n" +
                        "continue configuring the model."
            )
        }
    }

    private fun createAndroidTestUtilConfiguration() {
        val name = SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION
        logger.info("Creating configuration {}", name)
        val configuration = project.configurations.maybeCreate(name)
        configuration.isVisible = false
        configuration.description = "Additional APKs used during instrumentation testing."
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
    }

    override var compileSdkVersion: String?
        get() = target
        set(value) {
            checkWritability()
            target = value
        }

    open fun compileSdkVersion(version: String) {
        this.compileSdkVersion = version
    }

    open fun compileSdkVersion(apiLevel: Int) {
        compileSdkVersion("android-$apiLevel")
    }

    fun setCompileSdkVersion(apiLevel: Int) {
        compileSdkVersion(apiLevel)
    }

    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies](https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html).
     *
     * @param name the name of the library.
     */
    fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies]("https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](new dependency configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html)
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     *     the library is not required.
     */
    fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    open fun buildToolsVersion(version: String) {
        buildToolsVersion = version
    }

    /** {@inheritDoc} */
    override var buildToolsVersion: String
        get() = buildToolsRevision.toString()
        set(version) {
            checkWritability()
            //The underlying Revision class has the maven artifact semantic,
            // so 20 is not the same as 20.0. For the build tools revision this
            // is not the desired behavior, so normalize e.g. to 20.0.0.
            buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO)
        }

    /**
     * Encapsulates all build type configurations for this project.
     *
     * For more information about the properties you can configure in this block, see [BuildType]
     */
    fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        checkWritability()
        action.execute(buildTypes)
    }

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * For more information about the properties you can configure in this block,
     * see [ProductFlavor]
     */
    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>) {
        checkWritability()
        action.execute(productFlavors)
    }

    /**
     * Encapsulates signing configurations that you can apply to
     * [BuildType] and [ProductFlavor] configurations.
     *
     * For more information about the properties you can configure in this block,
     * see [SigningConfig].
     */
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        checkWritability()
        action.execute(signingConfigs)
    }

    /**
     * Specifies the names of product flavor dimensions for this project.
     *
     * When configuring product flavors with Android plugin 3.0.0 and higher, you must specify at
     * least one flavor dimension, using the <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[])">
     * `flavorDimensions`</a> property, and then assign each flavor to a dimension.
     * Otherwise, you will get the following build error:
     *
     * ```
     * Error:All flavors must now belong to a named flavor dimension.
     * The flavor 'flavor_name' is not assigned to a flavor dimension.
     * ```
     *
     * By default, when you specify only one dimension, all flavors you configure automatically
     * belong to that dimension. If you specify more than one dimension, you need to manually assign
     * each flavor to a dimension, as shown in the sample below.
     *
     * Flavor dimensions allow you to create groups of product flavors that you can combine with
     * flavors from other flavor dimensions. For example, you can have one dimension that includes a
     * 'free' and 'paid' version of your app, and another dimension for flavors that support
     * different API levels, such as 'minApi21' and 'minApi24'. The Android plugin can then combine
     * flavors from these dimensions—including their settings, code, and resources—to create
     * variants such as 'debugFreeMinApi21' and 'releasePaidMinApi24', and so on. The sample below
     * shows you how to specify flavor dimensions and add product flavors to them.
     *
     * ```
     * android {
     *     ...
     *     // Specifies the flavor dimensions you want to use. The order in which you
     *     // list each dimension determines its priority, from highest to lowest,
     *     // when Gradle merges variant sources and configurations. You must assign
     *     // each product flavor you configure to one of the flavor dimensions.
     *     flavorDimensions 'api', 'version'
     *
     *     productFlavors {
     *       demo {
     *         // Assigns this product flavor to the 'version' flavor dimension.
     *         dimension 'version'
     *         ...
     *     }
     *
     *       full {
     *         dimension 'version'
     *         ...
     *       }
     *
     *       minApi24 {
     *         // Assigns this flavor to the 'api' dimension.
     *         dimension 'api'
     *         minSdkVersion '24'
     *         versionNameSuffix "-minApi24"
     *         ...
     *       }
     *
     *       minApi21 {
     *         dimension "api"
     *         minSdkVersion '21'
     *         versionNameSuffix "-minApi21"
     *         ...
     *       }
     *    }
     * }
     * ```
     *
     * To learn more, read <a
     * href="https://developer.android.com/studio/build/build-variants.html#flavor-dimensions">
     * Combine multiple flavors</a>.
     */
    fun flavorDimensions(vararg dimensions: String) {
        checkWritability()
        flavorDimensionList.clear()
        flavorDimensionList.addAll(dimensions)
    }

    /**
     * Encapsulates source set configurations for all variants.
     *
     * Note that the Android plugin uses its own implementation of source sets. For more
     * information about the properties you can configure in this block, see [AndroidSourceSet].
     */
    fun sourceSets(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>) {
        checkWritability()
        sourceSetManager.executeAction(action)
    }

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = sourceSetManager.sourceSetsContainer

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [ProductFlavor].
     */
    fun defaultConfig(action: Action<DefaultConfig>) {
        checkWritability()
        action.execute(defaultConfig)
    }

    /**
     * Specifies options for the Android Asset Packaging Tool (AAPT).
     *
     * For more information about the properties you can configure in this block, see [AaptOptions].
     */
    fun aaptOptions(action: Action<AaptOptions>) {
        checkWritability()
        action.execute(aaptOptions)
    }

    /**
     * Specifies options for the DEX tool, such as enabling library pre-dexing.
     *
     * For more information about the properties you can configure in this block, see [DexOptions].
     */
    fun dexOptions(action: Action<DexOptions>) {
        checkWritability()
        action.execute(dexOptions)
    }

    /**
     * Specifies options for the lint tool.
     *
     * For more information about the properties you can configure in this block, see [LintOptions].
     */
    fun lintOptions(action: Action<LintOptions>) {
        checkWritability()
        action.execute(lintOptions)
    }

    /**
     * Configures external native build using [CMake](https://cmake.org/) or
     * [ndk-build](https://developer.android.com/ndk/guides/build.html).
     *
     * For more information about the properties you can configure in this block,
     * see [ExternalNativeBuild].
     */
    fun externalNativeBuild(action: Action<ExternalNativeBuild>) {
        checkWritability()
        action.execute(externalNativeBuild)
    }

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * For more information about the properties you can configure in this block, see [TestOptions].
     */
    fun testOptions(action: Action<TestOptions>) {
        checkWritability()
        action.execute(testOptions)
    }

    /**
     * Specifies Java compiler options, such as the language level of the Java source code and
     * generated bytecode.
     *
     * For more information about the properties you can configure in this block, see [com.android.build.gradle.internal.CompileOptions].
     */
    fun compileOptions(action: Action<CompileOptions>) {
        checkWritability()
        action.execute(compileOptions)
    }

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * For more information about the properties you can configure in this block, see [PackagingOptions].
     */
    fun packagingOptions(action: Action<PackagingOptions>) {
        checkWritability()
        action.execute(packagingOptions)
    }

    /**
     * Configure JaCoCo version that is used for offline instrumentation and coverage report.
     *
     * To specify the version of JaCoCo you want to use, add the following to `build.gradle
     * ` file:
     *
     * ```
     * android {
     *     jacoco {
     *         version "&ltjacoco-version&gt"
     *     }
     * }
     * ```
     */
    fun jacoco(action: Action<JacocoOptions>) {
        checkWritability()
        action.execute(jacoco)
    }

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    fun adbOptions(action: Action<AdbOptions>) {
        checkWritability()
        action.execute(adbOptions)
    }

    /**
     * Specifies configurations for
     * [building multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html)
     * or APK splits.
     *
     * For more information about the properties you can configure in this block, see [Splits].
     */
    fun splits(action: Action<Splits>) {
        checkWritability()
        action.execute(splits)
    }

    /**
     * Specifies options for the
     * [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
     *
     * For more information about the properties you can configure in this block, see [DataBindingOptions]
     */
    fun dataBinding(action: Action<DataBindingOptions>) {
        checkWritability()
        action.execute(dataBinding)
    }

    /**
     * Specifies options for the View Binding lLibrary.
     *
     * For more information about the properties you can configure in this block, see [ViewBindingOptions].
     */
    fun viewBinding(action: Action<ViewBindingOptions>) {
        checkWritability()
        action.execute(viewBinding)
    }

    fun deviceProvider(deviceProvider: DeviceProvider) {
        checkWritability()
        deviceProviderList.add(deviceProvider)
    }

    override val deviceProviders: List<DeviceProvider>
        get() = deviceProviderList

    fun testServer(testServer: TestServer) {
        checkWritability()
        testServerList.add(testServer)
    }

    override val testServers: List<TestServer>
        get() = testServerList

    fun registerTransform(transform: Transform, vararg dependencies: Any) {
        _transforms.add(transform)
        _transformDependencies.add(listOf(dependencies))
    }

    override val transforms: List<Transform>
        get() = ImmutableList.copyOf(_transforms)

    override val transformsDependencies: List<List<Any>>
        get() = ImmutableList.copyOf(_transformDependencies)

    open fun defaultPublishConfig(value: String) {
        defaultPublishConfig = value
    }

    fun setPublishNonDefault(publishNonDefault: Boolean) {
        logger.warn("publishNonDefault is deprecated and has no effect anymore. All variants are now published.")
    }

    open fun variantFilter(variantFilter: Action<VariantFilter>) {
        this.variantFilter = variantFilter
    }

    fun resourcePrefix(prefix: String) {
        _resourcePrefix = prefix
    }

    abstract fun addVariant(variant: BaseVariant, variantScope: VariantScope)

    fun registerArtifactType(name: String, isTest: Boolean, artifactType: Int) {
        extraModelInfo.registerArtifactType(name, isTest, artifactType)
    }

    fun registerBuildTypeSourceProvider(
        name: String,
        buildType: BuildType,
        sourceProvider: SourceProvider
    ) {
        extraModelInfo.registerBuildTypeSourceProvider(name, buildType, sourceProvider)
    }

    fun registerProductFlavorSourceProvider(
        name: String,
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider
    ) {
        extraModelInfo.registerProductFlavorSourceProvider(name, productFlavor, sourceProvider)
    }

    fun registerJavaArtifact(
        name: String,
        variant: BaseVariant,
        assembleTaskName: String,
        javaCompileTaskName: String,
        generatedSourceFolders: MutableCollection<File>,
        ideSetupTaskNames: Iterable<String>,
        configuration: Configuration,
        classesFolder: File,
        javaResourceFolder: File,
        sourceProvider: SourceProvider
    ) {
        extraModelInfo.registerJavaArtifact(
            name, variant, assembleTaskName,
            javaCompileTaskName, generatedSourceFolders, ideSetupTaskNames,
            configuration, classesFolder, javaResourceFolder, sourceProvider
        )
    }

    fun registerMultiFlavorSourceProvider(
        name: String,
        flavorName: String,
        sourceProvider: SourceProvider
    ) {
        extraModelInfo.registerMultiFlavorSourceProvider(name, flavorName, sourceProvider)
    }

    @NonNull
    fun wrapJavaSourceSet(sourceSet: SourceSet): SourceProvider {
        return SourceSetSourceProviderWrapper(sourceSet)
    }

    /**
     * Returns the path to the Android SDK that Gradle uses for this project.
     *
     * To learn more about downloading and installing the Android SDK, read
     * [Update Your Tools with the SDK Manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     */
    @NonNull
    fun getSdkDirectory(): File {
        return globalScope.sdkComponents.getSdkDirectory()
    }

    /**
     * Returns the path to the [Android NDK](https://developer.android.com/ndk/index.html) that Gradle uses for this project.
     *
     * You can install the Android NDK by either
     * [using the SDK manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     * or downloading
     * [the standalone NDK package](https://developer.android.com/ndk/downloads/index.html).
     */
    fun getNdkDirectory(): File {
        // do not call this method from within the plugin code as it forces part of SDK initialization.
        return globalScope.sdkComponents.ndkFolderProvider.get()
    }

    // do not call this method from within the plugin code as it forces SDK initialization.
    override val bootClasspath: List<File>
        get() = try {
            ArrayList(globalScope.bootClasspath.files)
        } catch (e: IllegalStateException) {
            listOf()
        }

    /**
     * Returns a path to the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html)
     * executable from the Android SDK.
     */
    fun getAdbExecutable(): File {
        return globalScope.sdkComponents.adbExecutableProvider.get()
    }

    /** This property is deprecated. Instead, use [getAdbExecutable]. */
    @Deprecated("This property is deprecated", ReplaceWith("getAdbExecutable"))
    fun getAdbExe(): File {
        return getAdbExecutable()
    }

    fun getDefaultProguardFile(name: String): File {
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            extraModelInfo
                .syncIssueHandler
                .reportError(
                    EvalIssueReporter.Type.GENERIC, ProguardFiles.UNKNOWN_FILENAME_MESSAGE
                )
        }
        return ProguardFiles.getDefaultProguardFile(name, project)
    }

    // ---------------
    // TEMP for compatibility

    /** {@inheritDoc} */
    override var generatePureSplits: Boolean
        get() = false
        set(_) = logger.warn(
            "generatePureSplits is deprecated and has no effect anymore. Use bundletool to generate configuration splits."
        )

    override val aidlPackageWhiteList: MutableCollection<String>?
        get() = throw GradleException("aidlPackageWhiteList is not supported.")

    // For compatibility with FeatureExtension.
    override val baseFeature: Boolean
        get() = isBaseModule

    fun buildFeatures(action: Action<BuildFeatures>) {
        action.execute(buildFeatures)
    }

    @Incubating
    fun composeOptions(action: Action<ComposeOptions>) {
        action.execute(composeOptions)
    }
}
