/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.view.inspector;

import java.util.HashMap;
import java.util.Map;

/** Replacement class for testing. */
public class StaticInspectionCompanionProvider implements InspectionCompanionProvider {
    private static final Map<Class<?>, InspectionCompanion<?>> ourProviders = new HashMap<>();

    public StaticInspectionCompanionProvider() {}

    @Override
    public <T> InspectionCompanion<T> provide(Class<T> cls) {
        //noinspection unchecked
        return (InspectionCompanion<T>) ourProviders.get(cls);
    }

    // For tests
    public static <T> void register(Class<T> cls, InspectionCompanion<T> provider) {
        ourProviders.put(cls, provider);
    }

    public static void cleanUp() {
        ourProviders.clear();
    }
}
