/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.api.dsl.Ndk;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * DSL object for per-variant NDK settings, such as the ABI filter.
 *
 * @see Ndk for the public interface.
 */
public class NdkOptions implements CoreNdkOptions, Serializable, Ndk {
    private static final long serialVersionUID = 1L;

    private String moduleName;
    private String cFlags;
    private List<String> ldLibs;
    private Set<String> abiFilters;
    private String stl;
    private Integer jobs;

    public NdkOptions() {
    }

    public void _initWith(@NonNull CoreNdkOptions ndkConfig) {
        moduleName = ndkConfig.getModuleName();
        cFlags = ndkConfig.getcFlags();
        setLdLibs(ndkConfig.getLdLibs());
        setAbiFilters(ndkConfig.getAbiFilters());
    }

    @Override
    @Input @Optional
    public String getModuleName() {
        return moduleName;
    }

    @Override
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    @Input @Optional
    public String getcFlags() {
        return cFlags;
    }

    public void setcFlags(String cFlags) {
        this.cFlags = cFlags;
    }

    @Override
    public void setCFlags(@Nullable String cFlags) {
        this.cFlags = cFlags;
    }

    @Nullable
    @Override
    public String getCFlags() {
        return cFlags;
    }

    @Override
    @Input @Optional
    public List<String> getLdLibs() {
        return ldLibs;
    }

    @NonNull
    public NdkOptions ldLibs(String lib) {
        if (ldLibs == null) {
            ldLibs = Lists.newArrayList();
        }
        ldLibs.add(lib);
        return this;
    }

    @NonNull
    public NdkOptions ldLibs(String... libs) {
        if (ldLibs == null) {
            ldLibs = Lists.newArrayListWithCapacity(libs.length);
        }
        Collections.addAll(ldLibs, libs);
        return this;
    }

    @NonNull
    public NdkOptions setLdLibs(Collection<String> libs) {
        if (libs != null) {
            if (ldLibs == null) {
                ldLibs = Lists.newArrayListWithCapacity(libs.size());
            } else {
                ldLibs.clear();
            }
            ldLibs.addAll(libs);
        } else {
            ldLibs = null;
        }
        return this;
    }

    @Override
    @Input
    @Optional
    public Set<String> getAbiFilters() {
        return abiFilters;
    }


    @NonNull
    public NdkOptions abiFilter(String filter) {
        if (abiFilters == null) {
            abiFilters = Sets.newHashSetWithExpectedSize(2);
        }
        abiFilters.add(filter);
        return this;
    }

    @NonNull
    public NdkOptions abiFilters(String... filters) {
        if (abiFilters == null) {
            abiFilters = Sets.newHashSetWithExpectedSize(2);
        }
        Collections.addAll(abiFilters, filters);
        return this;
    }

    @NonNull
    public NdkOptions setAbiFilters(Collection<String> filters) {
        if (filters != null) {
            if (abiFilters == null) {
                abiFilters = Sets.newHashSetWithExpectedSize(filters.size());
            } else {
                abiFilters.clear();
            }

            abiFilters.addAll(filters);
        } else {
            abiFilters = null;
        }
        return this;
    }

    @Override
    public void setAbiFilters(@Nullable Set<String> abiFilters) {
        setAbiFilters((Collection<String>) abiFilters);
    }

    @Override
    @Nullable
    public String getStl() {
        return stl;
    }

    @Override
    public void setStl(String stl) {
        this.stl = stl;
    }

    @Nullable
    @Override
    public Integer getJobs() {
        return jobs;
    }

    @Override
    public void setJobs(Integer jobs) {
        this.jobs = jobs;
    }
}
