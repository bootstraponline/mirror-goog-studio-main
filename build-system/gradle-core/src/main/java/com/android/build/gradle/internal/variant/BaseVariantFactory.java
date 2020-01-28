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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME;

import com.android.annotations.NonNull;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantApiScope;
import com.android.build.gradle.internal.scope.VariantPropertiesApiScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import org.gradle.api.Project;

/** Common superclass for all {@link VariantFactory} implementations. */
public abstract class BaseVariantFactory<
                VariantT extends VariantImpl<VariantPropertiesT>,
                VariantPropertiesT extends VariantPropertiesImpl>
        implements VariantFactory<VariantT, VariantPropertiesT> {

    @NonNull protected final VariantApiScope variantApiScope;
    @NonNull protected final VariantPropertiesApiScope variantPropertiesApiScope;
    @NonNull protected final GlobalScope globalScope;

    public BaseVariantFactory(
            @NonNull VariantApiScope variantApiScope,
            @NonNull VariantPropertiesApiScope variantPropertiesApiScope,
            @NonNull GlobalScope globalScope) {
        this.variantApiScope = variantApiScope;
        this.variantPropertiesApiScope = variantPropertiesApiScope;
        this.globalScope = globalScope;
    }

    @Override
    @NonNull
    public VariantApiScope getVariantApiScope() {
        return variantApiScope;
    }

    @Override
    @NonNull
    public VariantPropertiesApiScope getVariantPropertiesApiScope() {
        return variantPropertiesApiScope;
    }

    @NonNull
    @Override
    public UnitTestImpl createUnitTestObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo) {
        return globalScope
                .getDslScope()
                .getObjectFactory()
                .newInstance(
                        UnitTestImpl.class, variantDslInfo, componentIdentity, variantApiScope);
    }

    @NonNull
    @Override
    public AndroidTestImpl createAndroidTestObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo) {
        return globalScope
                .getDslScope()
                .getObjectFactory()
                .newInstance(
                        AndroidTestImpl.class, variantDslInfo, componentIdentity, variantApiScope);
    }

    @NonNull
    @Override
    public UnitTestPropertiesImpl createUnitTestProperties(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantPropertiesImpl testedVariantProperties,
            @NonNull TransformManager transformManager) {
        UnitTestPropertiesImpl unitTestProperties =
                globalScope
                        .getDslScope()
                        .getObjectFactory()
                        .newInstance(
                                UnitTestPropertiesImpl.class,
                                componentIdentity,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                testedVariantProperties,
                                transformManager,
                                variantPropertiesApiScope,
                                globalScope);

        unitTestProperties.addVariantOutput(variantData.getOutputFactory().addMainApk());

        return unitTestProperties;
    }

    @NonNull
    @Override
    public AndroidTestPropertiesImpl createAndroidTestProperties(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantPropertiesImpl testedVariantProperties,
            @NonNull TransformManager transformManager) {
        AndroidTestPropertiesImpl androidTestProperties =
                globalScope
                        .getDslScope()
                        .getObjectFactory()
                        .newInstance(
                                AndroidTestPropertiesImpl.class,
                                componentIdentity,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                testedVariantProperties,
                                transformManager,
                                variantPropertiesApiScope,
                                globalScope);

        androidTestProperties.addVariantOutput(variantData.getOutputFactory().addMainApk());

        return androidTestProperties;
    }

    @Override
    public void preVariantWork(Project project) {
        if (project.getPluginManager().hasPlugin(ANDROID_APT_PLUGIN_NAME)) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            Type.INCOMPATIBLE_PLUGIN,
                            "android-apt plugin is incompatible with the Android Gradle plugin.  "
                                    + "Please use 'annotationProcessor' configuration "
                                    + "instead.",
                            "android-apt");
        }
    }

    @Override
    public void validateModel(@NonNull VariantInputModel model) {
        validateBuildConfig(model);
        validateResValues(model);
    }

    void validateBuildConfig(@NonNull VariantInputModel model) {
        if (!globalScope.getBuildFeatures().getBuildConfig()) {
            IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();

            if (!model.getDefaultConfig().getProductFlavor().getBuildConfigFields().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom BuildConfig fields, but the feature is disabled.");
            }

            for (BuildTypeData buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }

    void validateResValues(@NonNull VariantInputModel model) {
        if (!globalScope.getBuildFeatures().getResValues()) {
            IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();

            if (!model.getDefaultConfig().getProductFlavor().getResValues().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom resource values, but the feature is disabled.");
            }

            for (BuildTypeData buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom resource values, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom resource values, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }

}
