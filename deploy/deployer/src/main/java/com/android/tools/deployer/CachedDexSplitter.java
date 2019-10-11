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
package com.android.tools.deployer;

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import java.util.Collection;
import java.util.function.Predicate;

public class CachedDexSplitter implements DexSplitter {

    private final ApkFileDatabase db;
    private final DexSplitter splitter;

    public CachedDexSplitter(ApkFileDatabase db, DexSplitter splitter) {
        this.db = db;
        this.splitter = splitter;
    }

    @Override
    public Collection<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode)
            throws DeployerException {
        Collection<DexClass> classes = db.getClasses(dex);
        if (classes.isEmpty() || keepCode != null) {
            classes = splitter.split(dex, keepCode);
        }
        return classes;
    }

    public void cache(Collection<DexClass> classes) {
        db.addClasses(classes);
    }
}
