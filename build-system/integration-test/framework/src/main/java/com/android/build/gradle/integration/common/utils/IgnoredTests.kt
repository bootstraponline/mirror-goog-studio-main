/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils

/**
 * Tracks @ignored annotated tests single place.
 * Annotations do not allow compile time arguments, so a companion object of const strings
 * is used to keep track of related disabled tests.
*/
class IgnoredTests {

    companion object {
        // b/236828934: Fused Libraries do not yet support external dependencies."
        const val BUG_23682893 = "236828934"
        // b/236828934: Custom signing not working with model v2"
        const val BUG_243127865 = "243127865"
    }
}

