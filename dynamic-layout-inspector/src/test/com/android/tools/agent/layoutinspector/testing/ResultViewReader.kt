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

package com.android.tools.agent.layoutinspector.testing

/**
 * Reader for a CSV with the expected result from ComposeTree.
 *
 * Use this class in tests to load the expected view tree produced by ComposeTree.
 * The resulting nodes is placed in the [roots] list.
 * The input format is the same as the output generated by ComposeTree while displaying a composable
 * in the layout inspector. The coordinates generated are relative to the parent. Set [viewLeft]
 * and [viewTop] to the coordinates of the view that contains the composables.
 */
class ResultViewReader : CsvReader(10) {
    private val views = mutableListOf<ComposeViewResult>()
    val roots = mutableListOf<ComposeViewResult>()
    var viewLeft = 0
    var viewTop = 0

    override fun resetState() {
        views.clear()
        roots.clear()
    }

    override fun storeState() {
    }

    override fun parseColumns(columns: List<String>) {
        val indent = parseIndent(columns[0])
        val parent = findParent(indent)
        val view = ComposeViewResult(
          csvLineNumber = lineNumber,
          className = parseQuotedString(columns[1]),
          drawId = parseInt(columns[2]).toLong(),
          fileName = parseQuotedString(columns[3]),
          lineNumber = parseInt(columns[4]),
          invocation = parseQuotedString(columns[5]),
          left = parseInt(columns[6]),
          top = parseInt(columns[7]),
          right = parseInt(columns[8]),
          bottom = parseInt(columns[9])
        )
        parent?.children?.add(view)
        views.add(view)
        if (parent == null) {
            roots.add(view)
        }
    }

    private fun findParent(indent: Int): ComposeViewResult? {
        if (indent > views.size) {
            error("Cannot parse line $lineNumber: The indent \"$indent\" is a jump up")
        }
        if (indent < views.size) {
            views.subList(indent, views.size).clear()
        }
        return views.lastOrNull()
    }
}
