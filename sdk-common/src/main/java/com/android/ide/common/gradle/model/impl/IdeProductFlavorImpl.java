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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.ide.common.gradle.model.IdeApiVersion;
import com.android.ide.common.gradle.model.IdeProductFlavor;
import com.android.ide.common.gradle.model.IdeSigningConfig;
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions;
import com.android.ide.common.gradle.model.ModelCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Creates a deep copy of a {@link ProductFlavor}. */
public final class IdeProductFlavorImpl extends IdeBaseConfigImpl implements IdeProductFlavor {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 4L;

    @NonNull private final Map<String, String> myTestInstrumentationRunnerArguments;
    @NonNull private final Collection<String> myResourceConfigurations;
    @Nullable private final IdeVectorDrawablesOptions myVectorDrawables;
    @Nullable private final String myDimension;
    @Nullable private final String myApplicationId;
    @Nullable private final Integer myVersionCode;
    @Nullable private final String myVersionName;
    @Nullable private final IdeApiVersion myMinSdkVersion;
    @Nullable private final IdeApiVersion myTargetSdkVersion;
    @Nullable private final Integer myMaxSdkVersion;
    @Nullable private final String myTestApplicationId;
    @Nullable private final String myTestInstrumentationRunner;
    @Nullable private final Boolean myTestFunctionalTest;
    @Nullable private final Boolean myTestHandleProfiling;
    @Nullable private final IdeSigningConfig mySigningConfig;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeProductFlavorImpl() {
        super();

        myTestInstrumentationRunnerArguments = Collections.emptyMap();
        myResourceConfigurations = Collections.emptyList();
        myVectorDrawables = new IdeVectorDrawablesOptionsImpl();
        myDimension = null;
        myApplicationId = null;
        myVersionCode = null;
        myVersionName = null;
        myMinSdkVersion = null;
        myTargetSdkVersion = null;
        myMaxSdkVersion = null;
        myTestApplicationId = null;
        myTestInstrumentationRunner = null;
        myTestFunctionalTest = null;
        myTestHandleProfiling = null;
        mySigningConfig = null;

        myHashCode = 0;
    }

    public IdeProductFlavorImpl(@NonNull ProductFlavor flavor, @NonNull ModelCache modelCache) {
        super(flavor, modelCache);

        myTestInstrumentationRunnerArguments =
                ImmutableMap.copyOf(flavor.getTestInstrumentationRunnerArguments());
        myResourceConfigurations = ImmutableList.copyOf(flavor.getResourceConfigurations());
        myVectorDrawables = copyVectorDrawables(flavor, modelCache);
        myDimension = flavor.getDimension();
        myApplicationId = flavor.getApplicationId();
        myVersionCode = flavor.getVersionCode();
        myVersionName = flavor.getVersionName();
        myMinSdkVersion = copy(modelCache, flavor.getMinSdkVersion());
        myTargetSdkVersion = copy(modelCache, flavor.getTargetSdkVersion());
        myMaxSdkVersion = flavor.getMaxSdkVersion();
        myTestApplicationId = flavor.getTestApplicationId();
        myTestInstrumentationRunner = flavor.getTestInstrumentationRunner();
        myTestFunctionalTest = flavor.getTestFunctionalTest();
        myTestHandleProfiling = flavor.getTestHandleProfiling();
        mySigningConfig = copy(modelCache, flavor.getSigningConfig());

        myHashCode = calculateHashCode();
    }

    @Nullable
    private static IdeVectorDrawablesOptions copyVectorDrawables(
            @NonNull ProductFlavor flavor, @NonNull ModelCache modelCache) {
        VectorDrawablesOptions vectorDrawables;
        try {
            vectorDrawables = flavor.getVectorDrawables();
        } catch (UnsupportedOperationException e) {
            return null;
        }
        return modelCache.computeIfAbsent(
                vectorDrawables, options -> new IdeVectorDrawablesOptionsImpl(options));
    }

    @Nullable
    private static IdeApiVersionImpl copy(
            @NonNull ModelCache modelCache, @Nullable ApiVersion apiVersion) {
        if (apiVersion != null) {
            return modelCache.computeIfAbsent(
                    apiVersion, version -> new IdeApiVersionImpl(version));
        }
        return null;
    }

    @Nullable
    private static IdeSigningConfig copy(
            @NonNull ModelCache modelCache, @Nullable SigningConfig signingConfig) {
        if (signingConfig != null) {
            return modelCache.computeIfAbsent(
                    signingConfig, config -> new IdeSigningConfigImpl(config));
        }
        return null;
    }

    @Override
    @NonNull
    public Map<String, String> getTestInstrumentationRunnerArguments() {
        return myTestInstrumentationRunnerArguments;
    }

    @Override
    @NonNull
    public Collection<String> getResourceConfigurations() {
        return myResourceConfigurations;
    }

    @Override
    @NonNull
    public IdeVectorDrawablesOptions getVectorDrawables() {
        if (myVectorDrawables != null) {
            return myVectorDrawables;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: ProductFlavor.getVectorDrawables");
    }

    @Override
    @Nullable
    public String getDimension() {
        return myDimension;
    }

    @Override
    @Nullable
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @Nullable
    public Integer getVersionCode() {
        return myVersionCode;
    }

    @Override
    @Nullable
    public String getVersionName() {
        return myVersionName;
    }

    @Override
    @Nullable
    public IdeApiVersion getMinSdkVersion() {
        return myMinSdkVersion;
    }

    @Override
    @Nullable
    public IdeApiVersion getTargetSdkVersion() {
        return myTargetSdkVersion;
    }

    @Override
    @Nullable
    public Integer getMaxSdkVersion() {
        return myMaxSdkVersion;
    }

    @Override
    @Nullable
    public String getTestApplicationId() {
        return myTestApplicationId;
    }

    @Override
    @Nullable
    public String getTestInstrumentationRunner() {
        return myTestInstrumentationRunner;
    }

    @Override
    @Nullable
    public Boolean getTestHandleProfiling() {
        return myTestHandleProfiling;
    }

    @Override
    @Nullable
    public Boolean getTestFunctionalTest() {
        return myTestFunctionalTest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeProductFlavorImpl)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeProductFlavorImpl flavor = (IdeProductFlavorImpl) o;
        return flavor.canEqual(this)
                && Objects.equals(
                        myTestInstrumentationRunnerArguments,
                        flavor.myTestInstrumentationRunnerArguments)
                && Objects.equals(myResourceConfigurations, flavor.myResourceConfigurations)
                && Objects.equals(myVectorDrawables, flavor.myVectorDrawables)
                && Objects.equals(myDimension, flavor.myDimension)
                && Objects.equals(myApplicationId, flavor.myApplicationId)
                && Objects.equals(myVersionCode, flavor.myVersionCode)
                && Objects.equals(myVersionName, flavor.myVersionName)
                && Objects.equals(myMinSdkVersion, flavor.myMinSdkVersion)
                && Objects.equals(myTargetSdkVersion, flavor.myTargetSdkVersion)
                && Objects.equals(myMaxSdkVersion, flavor.myMaxSdkVersion)
                && Objects.equals(myTestApplicationId, flavor.myTestApplicationId)
                && Objects.equals(myTestInstrumentationRunner, flavor.myTestInstrumentationRunner)
                && Objects.equals(myTestFunctionalTest, flavor.myTestFunctionalTest)
                && Objects.equals(myTestHandleProfiling, flavor.myTestHandleProfiling)
                && Objects.equals(mySigningConfig, flavor.mySigningConfig);
    }

    @Override
    public boolean canEqual(Object other) {
        return other instanceof IdeProductFlavorImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(
                super.calculateHashCode(),
                myTestInstrumentationRunnerArguments,
                myResourceConfigurations,
                myVectorDrawables,
                myDimension,
                myApplicationId,
                myVersionCode,
                myVersionName,
                myMinSdkVersion,
                myTargetSdkVersion,
                myMaxSdkVersion,
                myTestApplicationId,
                myTestInstrumentationRunner,
                myTestFunctionalTest,
                myTestHandleProfiling,
                mySigningConfig);
    }

    @Override
    public String toString() {
        return "IdeProductFlavor{"
                + super.toString()
                + ", myTestInstrumentationRunnerArguments="
                + myTestInstrumentationRunnerArguments
                + ", myResourceConfigurations="
                + myResourceConfigurations
                + ", myVectorDrawables="
                + myVectorDrawables
                + ", myDimension='"
                + myDimension
                + '\''
                + ", myApplicationId='"
                + myApplicationId
                + '\''
                + ", myVersionCode="
                + myVersionCode
                + ", myVersionName='"
                + myVersionName
                + '\''
                + ", myMinSdkVersion="
                + myMinSdkVersion
                + ", myTargetSdkVersion="
                + myTargetSdkVersion
                + ", myMaxSdkVersion="
                + myMaxSdkVersion
                + ", myTestApplicationId='"
                + myTestApplicationId
                + '\''
                + ", myTestInstrumentationRunner='"
                + myTestInstrumentationRunner
                + '\''
                + ", myTestFunctionalTest="
                + myTestFunctionalTest
                + ", myTestHandleProfiling="
                + myTestHandleProfiling
                + ", mySigningConfig="
                + mySigningConfig
                + "}";
    }
}
