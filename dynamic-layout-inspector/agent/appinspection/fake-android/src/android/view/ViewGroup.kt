/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view

import android.content.Context

class ViewGroup(context: Context) : View(context) {
    open class LayoutParams {
        companion object {

            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }

        @JvmField var width = 0
        @JvmField var height = 0
    }

    private val children = mutableListOf<View>()

    val childCount get() = children.size
    fun getChildAt(i: Int) = children[i]
    fun addView(view: View) {
        children.add(view)
    }
}
