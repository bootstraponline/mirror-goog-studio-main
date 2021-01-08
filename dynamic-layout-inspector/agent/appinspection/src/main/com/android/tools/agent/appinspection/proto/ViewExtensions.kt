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

package com.android.tools.agent.appinspection.proto

import android.content.res.Resources
import android.graphics.Point
import android.view.View
import android.view.ViewGroup
import com.android.tools.agent.appinspection.util.ThreadUtils
import android.view.WindowManager
import com.android.tools.agent.appinspection.framework.getChildren
import com.android.tools.agent.appinspection.framework.getTextValue
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Bounds
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Rect
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ViewNode

/**
 * Convert the target [View] into a proto [ViewNode].
 *
 * This method must be called on the main thread to avoid race conditions when querying the tree.
 */
fun View.toNode(stringTable: StringTable): ViewNode {
    ThreadUtils.assertOnMainThread()
    return toNodeImpl(stringTable, Point()).build()
}

private fun View.toNodeImpl(stringTable: StringTable, absOffset: Point): ViewNode.Builder {
    val view = this
    val viewClass = view::class.java
    val absPos = Point(absOffset.x + view.left, absOffset.y + view.top)

    return ViewNode.newBuilder().apply {
        id = uniqueDrawingId

        createResource(stringTable, view.id)?.let { resource = it }
        className = stringTable.put(viewClass.simpleName)
        viewClass.`package`?.name?.let { packageName = stringTable.put(it) }

        bounds = Bounds.newBuilder().apply {
            layout = Rect.newBuilder().apply {
                x = absPos.x
                y = absPos.y
                w = view.width
                h = view.height
            }.build()
            // TODO(b/17089580): Set render bounds
        }.build()

        createResource(stringTable, view.sourceLayoutResId)?.let { layoutResource = it }
        (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            layoutFlags = params.flags
        }

        view.getTextValue()?.let { text ->
            textValue = stringTable.put(text)
        }
        if (view is ViewGroup) {
            view.getChildren().forEach { child ->
                addChildren(child.toNodeImpl(stringTable, absPos))
            }
        }
    }
}

/**
 * Search this view for a resource with matching [resourceId] and, if found, return its
 * proto representation.
 */
fun View.createResource(stringTable: StringTable, resourceId: Int): Resource? {
    if (resourceId <= 0) return null
    val resources: Resources = resources ?: return null

    return try {
        return Resource.newBuilder().apply {
            type = stringTable.put(resources.getResourceTypeName(resourceId))
            namespace = stringTable.put(resources.getResourcePackageName(resourceId))
            name = stringTable.put(resources.getResourceEntryName(resourceId))
        }.build()
    } catch (ex: Resources.NotFoundException) {
        null
    }
}

