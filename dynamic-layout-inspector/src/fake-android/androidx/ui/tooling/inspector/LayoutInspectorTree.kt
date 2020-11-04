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

package androidx.ui.tooling.inspector

import android.view.View

const val TREE_ENTRY = 9929

/**
 * During testing this is used instead of the version in androidx-ui-tooling, since that library
 * is not available during tests.
 */
class LayoutInspectorTree {

    /**
     * Returns the hierarchy of [InspectorNode]s from the Compose call stack.
     *
     * This is not the real implementation. Here we simply return the tree stored
     * by the test in the tag: [TREE_ENTRY].
     */
    @Suppress("UNCHECKED_CAST", "unused")  // called by reflection
    fun convert(view: View): List<InspectorNode> =
        view.getTag(TREE_ENTRY) as? List<InspectorNode> ?: emptyList()

    /**
     * Returns the [NodeParameter]s of an [InspectorNode]s from the Compose call stack.
     *
     * This is not the real implementation. Here we simply return the [NodeParameter] stored
     * by the test in [RawParameter.value].
     */
    @Suppress("UNCHECKED_CAST", "unused")  // called by reflection
    fun convertParameters(node: InspectorNode): List<NodeParameter> =
        node.parameters.map { it.value as NodeParameter }

    /**
     * Reset the generated ids.
     *
     * This is not the real implementation. This is a noop.
     */
    @Suppress("unused")  // called by reflection
    fun resetGeneratedId() {}
}
