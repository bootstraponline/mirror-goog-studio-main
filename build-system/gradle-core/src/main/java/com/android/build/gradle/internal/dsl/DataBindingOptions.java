/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.api.dsl.BuildFeatures;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.options.BooleanOption;
import java.util.function.Supplier;
import javax.inject.Inject;

/** DSL object for configuring databinding options. */
public class DataBindingOptions
        implements com.android.builder.model.DataBindingOptions,
                com.android.build.api.dsl.DataBinding {
    @NonNull private final Supplier<BuildFeatures> featuresProvider;
    @NonNull private final DslScope dslScope;
    private String version;
    private boolean addDefaultAdapters = true;
    private boolean enabledForTests = false;

    @Inject
    public DataBindingOptions(
            @NonNull Supplier<BuildFeatures> featuresProvider, @NonNull DslScope dslScope) {
        this.featuresProvider = featuresProvider;
        this.dslScope = dslScope;
    }

    /**
     * The version of data binding to use.
     */
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }


    /** Whether to enable data binding. */
    @Override
    @Deprecated
    public boolean isEnabled() {
        dslScope.getDeprecationReporter()
                .reportDeprecatedUsage(
                        "android.buildFeatures.dataBinding",
                        "android.dataBinding.enabled",
                        DeprecationReporter.DeprecationTarget.VERSION_5_0);
        Boolean bool = featuresProvider.get().getDataBinding();
        if (bool != null) {
            return bool;
        }
        return dslScope.getProjectOptions().get(BooleanOption.BUILD_FEATURE_DATABINDING);
    }

    @Override
    @Deprecated
    public void setEnabled(boolean enabled) {
        dslScope.getDeprecationReporter()
                .reportDeprecatedUsage(
                        "android.buildFeatures.dataBinding",
                        "android.dataBinding.enabled",
                        DeprecationReporter.DeprecationTarget.VERSION_5_0);

        featuresProvider.get().setDataBinding(enabled);
    }

    /** Whether to add the default data binding adapters. */
    @Override
    public boolean getAddDefaultAdapters() {
        return addDefaultAdapters;
    }

    @Override
    public void setAddDefaultAdapters(boolean addDefaultAdapters) {
        this.addDefaultAdapters = addDefaultAdapters;
    }

    /**
     * Whether to run data binding code generation for test projects
     */
    @Override
    public boolean isEnabledForTests() {
        return enabledForTests;
    }

    @Override
    public void setEnabledForTests(boolean enabledForTests) {
        this.enabledForTests = enabledForTests;
    }
}
