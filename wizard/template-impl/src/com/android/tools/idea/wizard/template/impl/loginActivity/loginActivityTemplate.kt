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

package com.android.tools.idea.wizard.template.impl.loginActivity


import com.android.tools.idea.wizard.template.Category
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
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import loginActivityRecipe
import java.io.File

val loginActivityTemplate
  get() = template {
    revision = 6
    name = "Login Activity"
    description = "Creates a new login activity, allowing users to enter an email address and password to log in or to register with your application."
    minApi = 8
    minBuildApi = 14

    category = Category.Activity
    formFactor = FormFactor.Mobile

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "LoginActivity"
      help = "The name of the activity class to create "
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_login"
      help = "The name of the layout to create for the activity "
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val activityTitle = stringParameter {
      name = "Title"
      default = "Sign in"
      help = "The name of the activity. "
      visible = { false }
      constraints = listOf(NONEMPTY)
      suggest = { activityClass.value }
    }

    val packageName = stringParameter {
      name = "Package name"
      default = "com.mycompany.myapp "
      constraints = listOf(PACKAGE)
    }


    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_login_activity.png") }

    recipe = { data: TemplateData ->
      loginActivityRecipe(data as ModuleTemplateData, activityClass.value, layoutName.value, packageName.value)
    }

  }
