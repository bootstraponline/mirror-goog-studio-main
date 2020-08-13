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

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.createEqualsVerifier;

import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.stubs.VariantStub;
import com.android.ide.common.repository.GradleVersion;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.android.ide.common.gradle.model.impl.IdeVariantImpl}. */
public class IdeVariantImplTest {
    private ModelCacheTesting myModelCache;
    private GradleVersion myGradleVersion;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
        myGradleVersion = GradleVersion.parse("3.2");
    }

    @Test
    public void constructor() throws Throwable {
        Variant original = new VariantStub();
        IdeVariant copy = myModelCache.variantFrom(original, myGradleVersion);
    }
}
