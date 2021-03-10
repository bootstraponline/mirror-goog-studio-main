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
package com.android.build.gradle.internal.plugins;

import com.android.AndroidProjectTypes;
import com.android.annotations.NonNull;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.LibraryAndroidComponentsExtension;
import com.android.build.api.extension.impl.LibraryAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.impl.LibraryVariantBuilderImpl;
import com.android.build.api.variant.impl.LibraryVariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.LibraryExtensionImpl;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.LibraryVariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.v2.ide.ProjectType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'library' projects. */
public class LibraryPlugin
        extends BasePlugin<
                LibraryAndroidComponentsExtension, LibraryVariantBuilderImpl, LibraryVariantImpl> {

    @Inject
    public LibraryPlugin(
            ToolingModelBuilderRegistry registry,
            SoftwareComponentFactory componentFactory,
            BuildEventsListenerRegistry listenerRegistry) {
        super(registry, componentFactory, listenerRegistry);
    }

    @NonNull
    @Override
    protected BaseExtension createExtension(
            @NonNull DslServices dslServices,
            @NonNull GlobalScope globalScope,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        LibraryExtensionImpl libraryExtension =
                dslServices.newDecoratedInstance(
                        LibraryExtensionImpl.class, dslServices, dslContainers);
        if (projectServices.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            LibraryExtension android =
                    (LibraryExtension)
                            project.getExtensions()
                                    .create(
                                            com.android.build.api.dsl.LibraryExtension.class,
                                            "android",
                                            LibraryExtension.class,
                                            dslServices,
                                            globalScope,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            libraryExtension);
            project.getExtensions()
                    .add(LibraryExtension.class, "_internal_legacy_android_extension", android);
            return android;
        }

        return project.getExtensions()
                .create(
                        "android",
                        LibraryExtension.class,
                        dslServices,
                        globalScope,
                        buildOutputs,
                        dslContainers.getSourceSetManager(),
                        extraModelInfo,
                        libraryExtension);
    }

    @NonNull
    @Override
    protected LibraryAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<LibraryVariantBuilderImpl, LibraryVariantImpl>
                            variantApiOperationsRegistrar) {
        SdkComponents sdkComponents =
                dslServices.newInstance(
                        SdkComponentsImpl.class,
                        dslServices,
                        project.provider(getExtension()::getCompileSdkVersion),
                        project.provider(getExtension()::getBuildToolsRevision),
                        project.provider(getExtension()::getNdkVersion),
                        project.provider(getExtension()::getNdkPath));

        return project.getExtensions()
                .create(
                        LibraryAndroidComponentsExtension.class,
                        "androidComponents",
                        LibraryAndroidComponentsExtensionImpl.class,
                        dslServices,
                        sdkComponents,
                        variantApiOperationsRegistrar,
                        getExtension());
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new LibraryVariantFactory(projectServices, globalScope);
    }

    @Override
    protected int getProjectType() {
        return AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryTaskManager createTaskManager(
            @NonNull List<ComponentInfo<LibraryVariantBuilderImpl, LibraryVariantImpl>> variants,
            @NonNull List<TestComponentImpl> testComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo) {
        return new LibraryTaskManager(
                variants,
                testComponents,
                hasFlavors,
                projectOptions,
                globalScope,
                extension,
                projectInfo);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
    }

    @Override
    protected boolean isPackagePublished() {
        return true;
    }
}
