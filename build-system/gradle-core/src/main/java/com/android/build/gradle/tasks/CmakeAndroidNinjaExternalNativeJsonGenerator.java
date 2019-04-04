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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.cxx.process.ProcessOutputJunctionKt.createProcessOutputJunction;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * This strategy uses the older custom CMake (version 3.6) that directly generates the JSON file as
 * part of project configuration.
 */
class CmakeAndroidNinjaExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {
    // Constructor
    CmakeAndroidNinjaExternalNativeJsonGenerator(
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull Set<String> configurationFailures,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull GradleBuildVariant.Builder stats) {
        super(variant, abis, configurationFailures, androidBuilder, stats);
    }

    @NonNull
    @Override
    List<String> getCacheArguments(@NonNull CxxAbiModel abi) {
        List<String> cacheArguments = getCommonCacheArguments(abi);
        cacheArguments.add(
                String.format(
                        "-DCMAKE_TOOLCHAIN_FILE=%s",
                        cmake.getCmakeToolchainFile().getAbsolutePath()));
        cacheArguments.add(
                String.format("-DCMAKE_MAKE_PROGRAM=%s", cmake.getNinjaExe().getAbsolutePath()));
        cacheArguments.add("-GAndroid Gradle - Ninja");
        return cacheArguments;
    }

    @NonNull
    @Override
    public String executeProcessAndGetOutput(@NonNull CxxAbiModel abi)
            throws ProcessException, IOException {
        String logPrefix = variant.getVariantName() + "|" + abi.getAbi().getTag() + " :";
        return createProcessOutputJunction(
                        abi.getCxxBuildFolder(),
                        "android_gradle_generate_cmake_ninja_json_" + abi.getAbi().getTag(),
                        getProcessBuilder(abi),
                        androidBuilder,
                        logPrefix)
                .logStderrToInfo()
                .logStdoutToInfo()
                .executeAndReturnStdoutString();
    }
}
