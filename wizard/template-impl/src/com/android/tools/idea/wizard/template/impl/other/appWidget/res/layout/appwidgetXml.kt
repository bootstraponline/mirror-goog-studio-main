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

package com.android.tools.idea.wizard.template.impl.other.appWidget.res.layout

import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.values.getAppWidgetThemeOverlay

fun appwidgetXml(themesData: ThemesData) = """
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:theme="@style/${getAppWidgetThemeOverlay(themesData.overlay.name)}"
    android:background="?attr/appWidgetBackgroundColor"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="@dimen/widget_margin" >

    <TextView
        android:id="@+id/appwidget_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerHorizontal="true"
        android:layout_centerVertical="true"
        android:background="?attr/appWidgetBackgroundColor"
        android:text="@string/appwidget_text"
        android:textColor="?attr/appWidgetTextColor"
        android:textSize="24sp"
        android:textStyle="bold|italic"
        android:layout_margin="8dp"
        android:contentDescription="@string/appwidget_text" />
</RelativeLayout>"""