/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BootClasspathBuilder
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

class BootClasspathConfigImpl(
    project: Project,
    private val services: BaseServices,
    private val versionedSdkLoaderService: VersionedSdkLoaderService,
    private val extension: CommonExtensionImpl<*,*,*,*>,
    private val forUnitTest: Boolean
): BootClasspathConfig {

    override val fullBootClasspath: FileCollection by lazy {
        project.files(fullBootClasspathProvider)
    }

    override val fullBootClasspathProvider: Provider<List<RegularFile>> by lazy {
        // create a property rather than a Provider so that we can turn on finalizedValueOnRead
        // to avoid recomputing this in all the places this is used.
        val property = project.objects.listProperty(RegularFile::class.java)
        val versionedSdkLoader = versionedSdkLoaderService.versionedSdkLoader

        property.set(
            BootClasspathBuilder.computeClasspath(
                project.layout,
                project.providers,
                project.objects,
                services.issueReporter,
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::targetBootClasspathProvider
                    ),
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::targetAndroidVersionProvider
                    ),
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::additionalLibrariesProvider
                    ),
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::optionalLibrariesProvider
                    ),
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::annotationsJarProvider
                    ),
                addAllOptionalLibraries = true,
                ImmutableList.of()
            )
        )

        // prevent further changes
        property.disallowChanges()
        // turn on memoization
        property.finalizeValueOnRead()
        // prevent too early reads
        if (!forUnitTest) {
            property.disallowUnsafeRead()
        }

        property
    }

    override val filteredBootClasspath: Provider<List<RegularFile>> by lazy {
        // create a property rather than a Provider so that we can turn on finalizeeValueOnRead
        // to avoid recomputing this in all the places this is used.
        val property = project.objects.listProperty(RegularFile::class.java)
        val versionedSdkLoader = versionedSdkLoaderService.versionedSdkLoader

        property.set(
            BootClasspathBuilder.computeClasspath(
                project.layout,
                project.providers,
                project.objects,
                services.issueReporter,
                versionedSdkLoader.flatMap(
                    SdkComponentsBuildService.VersionedSdkLoader::targetBootClasspathProvider
                ),
                versionedSdkLoader.flatMap(
                    SdkComponentsBuildService.VersionedSdkLoader::targetAndroidVersionProvider
                ),
                versionedSdkLoader.flatMap(
                    SdkComponentsBuildService.VersionedSdkLoader::additionalLibrariesProvider
                ),
                versionedSdkLoader.flatMap(
                    SdkComponentsBuildService.VersionedSdkLoader::optionalLibrariesProvider
                ),
                versionedSdkLoader.flatMap(
                    SdkComponentsBuildService.VersionedSdkLoader::annotationsJarProvider
                ),
                false,
                ImmutableList.copyOf(extension.libraryRequests)
            )
        )

        // prevent further changes
        property.disallowChanges()
        // turn on memoization
        property.finalizeValueOnRead()
        // This cannot be protected against unsafe reads until BaseExtension::bootClasspath
        // has been removed. Most users of that method will call it at configuration time which
        // resolves this collection. Uncomment next line once BaseExtension::bootClasspath is
        // removed.
        //if (!forUnitTest) {
        //    property.disallowUnsafeRead()
        //}

        property
    }

    override val bootClasspath: Provider<List<RegularFile>> by lazy {
        // create a property rather than a Provider so that we can turn on finalizedValueOnRead
        // to avoid recomputing this in all the places this is used.
        val property = project.objects.listProperty(RegularFile::class.java)
        val versionedSdkLoader = versionedSdkLoaderService.versionedSdkLoader

        property.addAll(filteredBootClasspath)
        if (extension.compileOptions.targetCompatibility.isJava8Compatible) {
            property.add(
                versionedSdkLoader
                    .flatMap(
                        SdkComponentsBuildService.VersionedSdkLoader::coreLambdaStubsProvider
                    )
            )
        }

        // prevent further changes
        property.disallowChanges()
        // turn on memoization
        property.finalizeValueOnRead()
        // This cannot be protected against unsafe reads until BaseExtension::bootClasspath
        // has been removed. Most users of that method will call it at configuration time which
        // resolves this collection. Uncomment next lines once BaseExtension::bootClasspath is
        // removed.
        //if (!forUnitTest) {
        //    property.disallowUnsafeRead()
        //}

        property
    }

    internal lateinit var androidJar: Configuration

    override val mockableJarArtifact: FileCollection by lazy {
        val attributes =
            Action { container: AttributeContainer ->
                container
                    .attribute(
                        AndroidArtifacts.ARTIFACT_TYPE,
                        AndroidArtifacts.TYPE_MOCKABLE_JAR
                    )
                    .attribute(
                        AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                        extension.testOptions.unitTests.isReturnDefaultValues
                    )
            }
        androidJar
            .incoming
            .artifactView { config -> config.attributes(attributes) }
            .artifacts
            .artifactFiles
    }
}
