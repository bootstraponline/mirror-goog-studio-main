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

package com.android.build.gradle.internal.fixtures

/**
 * Abstract class allowing to write test using a given/expect DSL
 */
abstract class AbstractGivenExpectTest<GivenT, ResultT> {

    private var givenAction: (() -> GivenT)? = null
    private var whenAction: ((GivenT) -> ResultT?)? = null

    enum class TestState {
        START,
        GIVEN,
        WHEN,
        DONE
    }

    private var state: TestState = TestState.START

    /**
     * Registers an action block returning the given state as a single object
     */
    open fun given(action: () -> GivenT) {
        checkState(TestState.START)
        givenAction = action
        state = TestState.GIVEN
    }

    /**
     * Registers an action block converting the [given] object to a result object.
     *
     * This is to be used in tests where the action needs to be custom. If the action
     * is the same for all the tests of the class, then [defaultWhen] can be overridden instead.
     */
    fun `when`(action: (GivenT) -> ResultT?) {
        checkState(TestState.GIVEN)
        whenAction = action
        state = TestState.WHEN
    }

    /**
     * Registers an action block return the expected result values. This also runs the test.
     */
    fun expect(expectedProvider: () -> ResultT?) {
        // run the states by running all the necessary actions.
        val given = givenAction?.invoke() ?: throw RuntimeException("No given data")

        val actual = whenAction?.invoke(given) ?: defaultWhen(given)

        compareResult(expectedProvider(), actual)

        state = TestState.DONE
    }

    /**
     * This compares the actual result vs the expected result.
     */
    abstract fun compareResult(expected: ResultT?, actual: ResultT?)

    /**
     * A default implementation for the given -> result action
     */
    open fun defaultWhen(given: GivenT): ResultT? {
        throw RuntimeException("Test is using default implementation of defaultWhen")
    }

    /**
     * checks the current state of the test.
     */
    protected fun checkState(expectedState: TestState) {
        if (state != expectedState) {
            throw RuntimeException("Expected State is not $expectedState, it is $state")
        }
    }
}