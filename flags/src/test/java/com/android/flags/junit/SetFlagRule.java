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
package com.android.flags.junit;

import com.android.flags.Flag;

/**
 * Sets a flag to the given value before running a test and restores it to the original value
 * after the test.
 *
 * <pre>
 *   public class MyTest {
 *    {@literal @}Rule
 *     public TestRule myFlagRule = new SetFlagRule&lt;&gt;(StudioFlags.MY_FLAG, true);
 *   }
 * </pre>
 *
 * <p>See also: {@link RestoreFlagRule}
 */
public class SetFlagRule<T> extends RestoreFlagRule<T> {
    private final Flag<T> myFlag;
    private final T myValue;

    public SetFlagRule(Flag<T> flag, T value) {
        super(flag);
        myFlag = flag;
        myValue = value;
    }

    @Override
    protected void before() {
        myFlag.override(myValue);
    }
}
