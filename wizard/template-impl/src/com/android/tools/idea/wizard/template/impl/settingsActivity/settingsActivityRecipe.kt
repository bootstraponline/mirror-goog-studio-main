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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.common.generateManifest
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.layout.settingsActivityXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.values.arraysXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.xml.headerPreferencesXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.xml.messagesPreferencesXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.xml.rootPreferencesXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.res.xml.syncPreferencesXml
import com.android.tools.idea.wizard.template.impl.settingsActivity.src.app_package.multipleScreenSettingsActivityJava
import com.android.tools.idea.wizard.template.impl.settingsActivity.src.app_package.multipleScreenSettingsActivityKt
import com.android.tools.idea.wizard.template.impl.settingsActivity.src.app_package.singleScreenSettingsActivityJava
import com.android.tools.idea.wizard.template.impl.settingsActivity.src.app_package.singleScreenSettingsActivityKt
import java.io.File

fun RecipeExecutor.settingsActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  multipleScreens: Boolean,
  packageName: String) {

  val (projectData, srcOut, resOut, _) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  val simpleName = activityToLayout(activityClass)
  addDependency("androidx.preference:preference:1.1+")

  generateManifest(
    moduleData, activityClass, activityClass, packageName, isLauncher = moduleData.isNew, hasNoActionBar = false,
    requireTheme = true, generateActivityTitle = true, useMaterial2 = useMaterial2
  )

  mergeXml(stringsXml(activityTitle, simpleName), resOut.resolve("values/strings.xml"))
  mergeXml(arraysXml(), resOut.resolve("values/arrays.xml"))
  mergeXml(settingsActivityXml(), resOut.resolve("layout/settings_activity.xml"))

  if (multipleScreens) {
    copy(File("messages.xml"), resOut.resolve("drawable/messages.xml"))
    copy(File("sync.xml"), resOut.resolve("drawable/sync.xml"))

    mergeXml(headerPreferencesXml(activityClass, packageName), resOut.resolve("xml/header_preferences.xml"))
    mergeXml(messagesPreferencesXml(), resOut.resolve("xml/messages_preferences.xml"))
    mergeXml(syncPreferencesXml(), resOut.resolve("xml/sync_preferences.xml"))
    val multipleScreenSettingsActivity = when (projectData.language) {
      Language.Java -> multipleScreenSettingsActivityJava(activityClass, packageName, simpleName)
      Language.Kotlin -> multipleScreenSettingsActivityKt(activityClass, packageName, simpleName)
    }
    save(multipleScreenSettingsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  }
  else {
    mergeXml(rootPreferencesXml(), resOut.resolve("xml/root_preferences.xml"))
    val singleScreenSettingsActivity = when (projectData.language) {
      Language.Java -> singleScreenSettingsActivityJava(activityClass, packageName)
      Language.Kotlin -> singleScreenSettingsActivityKt(activityClass, packageName)
    }
    save(singleScreenSettingsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  }
  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
