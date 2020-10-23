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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun fragmentItemListTwopaneXml(
  collectionName: String,
  collection_name: String,
  detailNameLayout: String,
  itemListContentLayout: String,
  objectKindPlural: String,
  packageName: String,
  useAndroidX: Boolean
) = """
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_marginLeft="16dp"
    android:layout_marginRight="16dp"
    android:baselineAligned="false"
    android:divider="?android:attr/dividerHorizontal"
    android:orientation="horizontal"
    android:showDividers="middle"
    tools:context="${packageName}.${collectionName}Activity">

    <!--
    This layout is a two-pane layout for the ${objectKindPlural} master/detail flow.
    -->

    <${getMaterialComponentName("android.support.v7.widget.RecyclerView",
                                useAndroidX)} xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:id="@+id/${collection_name}"
        android:name="${packageName}.${collectionName}Fragment"
        android:layout_width="@dimen/item_width"
        android:layout_height="match_parent"
        android:layout_marginLeft="16dp"
        android:layout_marginRight="16dp"
        app:layoutManager="LinearLayoutManager"
        tools:context="${packageName}.${collectionName}Activity"
        tools:listitem="@layout/${itemListContentLayout}" />

    <FrameLayout
        android:id="@+id/${detailNameLayout}_container"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="3" />

</LinearLayout>
"""