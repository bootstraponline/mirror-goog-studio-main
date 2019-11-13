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

package com.android.tools.idea.wizard.template.impl.fullscreenActivity

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import fullscreenActivityRecipe
import java.io.File

val fullscreenActivityTemplate
  get() = template {
    revision = 1
    name = "Fullscreen Activity"
    description = "Creates a new activity that toggles the visibility of the system UI (status and navigation bars) and action bar upon user interaction."
    minApi = 14
    minBuildApi = 16

    category = Category.Activity
    formFactor = FormFactor.Mobile

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "FullscreenActivity"
      help = "The name of the activity class to create "
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_fullscreen"
      help = "The name of the layout to create for the activity "
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val activityTitle = stringParameter {
      name = "Title"
      default = "FullscreenActivity"
      help = "The name of the activity. "
      visible = { false }
      constraints = listOf(NONEMPTY)
      suggest = { activityClass.value }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher "
    }

    val packageName = stringParameter {
      name = "Package name"
      default = "com.mycompany.myapp "
      constraints = listOf(PACKAGE)
    }

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_fullscreen_activity.png") }

    recipe = { data: TemplateData ->
      fullscreenActivityRecipe(data as ModuleTemplateData, activityClass.value, isLauncher.value, layoutName.value, packageName.value)
    }
  }
