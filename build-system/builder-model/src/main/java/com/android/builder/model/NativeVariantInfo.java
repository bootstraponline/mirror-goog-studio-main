/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;
import java.io.Serializable;
import java.util.List;

/**
 * Native build information about a specific variant. This is the fast-to-compute information that
 * is sent to Android Studio during partial sync.
 */
public interface NativeVariantInfo extends Serializable {
    /**
     * Names of ABIs built by this variant. Typical values are: x86, x86_64, armeabi-v7a, and
     * arm64-v8a Rarer values are: armeabi, mips, and mip64
     *
     * @return set of ABI names.
     */
    @NonNull
    List<String> getAbiNames();
}
