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

fun activityItemListAppBarXml(
  collectionName: String,
  itemListLayout: String,
  packageName: String,
  themeNameAppBarOverlay: String,
  themeNamePopupOverlay: String,
  useAndroidX: Boolean,
  useMaterial2: Boolean
) = """
<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.design.widget.CoordinatorLayout",
                            useAndroidX)} xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    tools:context="${packageName}.${collectionName}Activity">

    <${getMaterialComponentName("android.support.design.widget.AppBarLayout", useMaterial2)}
        android:id="@+id/app_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:theme="@style/${themeNameAppBarOverlay}">

        <${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)}
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:popupTheme="@style/${themeNamePopupOverlay}" />

    </${getMaterialComponentName("android.support.design.widget.AppBarLayout", useMaterial2)}>

    <FrameLayout
        android:id="@+id/frameLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <include layout="@layout/${itemListLayout}" />
    </FrameLayout>

    <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useMaterial2)}
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="@dimen/fab_margin"
        app:srcCompat="@android:drawable/ic_dialog_email" />


</${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}>
"""