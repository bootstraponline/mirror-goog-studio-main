/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.impl.OperationsImpl
import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantTypeImpl
import com.android.sdklib.AndroidVersion
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Tests for the [CompatibleScreensManifest] class  */
class CompatibleScreensManifestTest {

    @get:Rule var projectFolder = TemporaryFolder()
    @get:Rule var temporaryFolder = TemporaryFolder()

    @Mock internal lateinit var scope: VariantScope
    @Mock internal lateinit var globalScope: GlobalScope
    @Mock private lateinit var variantDslInfo: VariantDslInfo
    @Suppress("DEPRECATION")
    @Mock private lateinit var operations: OperationsImpl
    @Mock private lateinit var taskContainer: MutableTaskContainer
    @Mock private lateinit var variantData: BaseVariantData
    @Mock private lateinit var variantProperties: ApplicationVariantPropertiesImpl

    private lateinit var task: CompatibleScreensManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = projectFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", CompatibleScreensManifest::class.java)

        val services = createTaskCreationServices()

        MockitoAnnotations.initMocks(this)
        `when`(variantProperties.name).thenReturn("fullVariantName")
        `when`(variantProperties.baseName).thenReturn("baseName")
        `when`(variantProperties.variantDslInfo).thenReturn(variantDslInfo)
        `when`(variantProperties.globalScope).thenReturn(globalScope)
        `when`(variantProperties.operations).thenReturn(operations)
        `when`(variantProperties.taskContainer).thenReturn(taskContainer)
        `when`(variantProperties.variantScope).thenReturn(scope)
        `when`(variantProperties.variantType).thenReturn(VariantTypeImpl.BASE_APK)
        `when`(variantProperties.variantData).thenReturn(variantData)
        `when`(variantProperties.services).thenReturn(services)
        `when`<AndroidVersion>(variantProperties.minSdkVersion).thenReturn(AndroidVersion(21))


        `when`(taskContainer.preBuildTask).thenReturn(project.tasks.register("preBuildTask"))
        task.outputFolder.set(temporaryFolder.root)
        `when`(variantDslInfo.variantType).thenReturn(VariantTypeImpl.BASE_APK)
        `when`(variantDslInfo.componentIdentity).thenReturn(
            ComponentIdentityImpl(
                "fullVariantName",
                "flavorName",
                "debug"
            )
        )
        `when`(globalScope.projectOptions).thenReturn(
            ProjectOptions(
                ImmutableMap.of<String, Any>(
                    BooleanOption.ENABLE_GRADLE_WORKERS.propertyName,
                    false
                )
            )
        )
        val applicationId = project.objects.property(String::class.java)
        applicationId.set("com.foo")
        `when`(variantProperties.applicationId).thenReturn(applicationId)
    }

    @Test
    fun testConfigAction() {

        val configAction = CompatibleScreensManifest.CreationAction(
                variantProperties, setOf("xxhpi", "xxxhdpi")
        )
        val variantOutputList = VariantOutputList(
            listOf(
                VariantOutputImpl(
                    FakeGradleProperty(value = 0),
                    FakeGradleProperty(value =""),
                    FakeGradleProperty(value =true),
                    Mockito.mock(VariantOutputConfigurationImpl::class.java),
                    "base_name",
                    "main_full_name",
                    FakeGradleProperty(value = "output_file_name"))))
        `when`(variantProperties.outputs).thenReturn(variantOutputList)

        configAction.configure(task)

        assertThat(task.variantName).isEqualTo("fullVariantName")
        assertThat(task.name).isEqualTo("test")
        assertThat(task.minSdkVersion.get()).isEqualTo("21")
        assertThat(task.screenSizes).containsExactly("xxhpi", "xxxhdpi")
        assertThat(task.outputFolder.get().asFile).isEqualTo(temporaryFolder.root)
        assertThat(task.applicationId.get()).isEqualTo("com.foo")
        assertThat(task.variantType.get()).isEqualTo(VariantTypeImpl.BASE_APK.toString())
    }

    @Test
    fun testNoSplit() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
            FakeGradleProperty("version_name"),
            FakeGradleProperty(true),
            VariantOutputConfigurationImpl(false, listOf()),
                "base_name",
                "main_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set("22" )
        task.screenSizes = ImmutableSet.of("mdpi", "xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())

        task.taskAction()
        val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(
            temporaryFolder.root)
        assertThat(buildElements?.elements).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithMinSdkVersion() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",

                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set("22")
        task.screenSizes = ImmutableSet.of("xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"22\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithoutMinSdkVersion() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",
                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set(task.project.provider { null })
        task.screenSizes = ImmutableSet.of("xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(
                    Files.readAllLines(
                            findManifest(temporaryFolder.root, "xhdpi").toPath()
                    )
            )
        assertThat(xml).doesNotContain("<uses-sdk")
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleSplitsWithMinSdkVersion() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",
                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xxhdpi"))),
                "base_name",

                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))


        task.variantName = "variant"
        task.minSdkVersion.set("23")
        task.screenSizes = ImmutableSet.of("xhdpi", "xxhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())

        task.taskAction()

        var xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )

        xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xxhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains("<screen android:screenSize=\"xxhdpi\" android:screenDensity=\"480\" />")
    }

    companion object {
        private fun findManifest(taskOutputDir: File, splitName: String): File {
            val splitDir = File(taskOutputDir, splitName)
            assertThat(splitDir.exists()).isTrue()
            val manifestFile = File(splitDir, SdkConstants.ANDROID_MANIFEST_XML)
            assertThat(manifestFile.exists()).isTrue()
            return manifestFile
        }
    }
}
