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

package com.android.tools.idea.wizard.template.impl

import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardTemplateProvider
import com.android.tools.idea.wizard.template.impl.basicActivity.basicActivityTemplate
import com.android.tools.idea.wizard.template.impl.emptyActivity.emptyActivityTemplate
import com.android.tools.idea.wizard.template.impl.loginActivity.loginActivityTemplate
import com.android.tools.idea.wizard.template.impl.tabbedActivity.tabbedActivityTemplate
import com.android.tools.idea.wizard.template.impl.viewModelActivity.viewModelActivityTemplate

/**
 * Implementation of the Android Wizard Template plugin extension point.
 */
class WizardTemplateProviderImpl : WizardTemplateProvider() {
  override fun getTemplates(): List<Template> = listOf(
    basicActivityTemplate, emptyActivityTemplate, tabbedActivityTemplate, viewModelActivityTemplate, loginActivityTemplate)
}
