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
package app;

import androidx.compose.runtime.internal.LiveLiteralInfo;

public final class LiveLiteralOffsetLookupKt {

    /**
     * This function minicks what the compose compiler generates for each Live Literal Variable.
     *
     * <p>The function returns the default value of such variable. More importantly, the annotated
     * LiveLiteralInfo contains a offset number that the device will be used to look up the
     * variable's name.
     *
     * <p>For more information, refer to documentation of LiveLiteralInfo in JetPack Compose.
     */
    @LiveLiteralInfo(key = "Int_func_foo_bar_LiveLiteral_variable", offset = 159)
    private static int Int_func_foo_bar_LiveLiteral_variable() {
        return 1;
    }
}
