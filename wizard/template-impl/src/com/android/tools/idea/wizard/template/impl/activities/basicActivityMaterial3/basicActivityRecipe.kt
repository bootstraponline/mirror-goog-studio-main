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
package com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.res.layout.fragmentFirstLayout
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.res.layout.fragmentSecondLayout
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.res.layout.fragmentSimpleXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.res.navigation.navGraphXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.src.basicActivityKt
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.src.firstFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.basicActivityMaterial3.src.secondFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterial3Dependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateAppBar
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateMaterial3Themes
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleMenu
import com.android.tools.idea.wizard.template.layoutToFragment

fun RecipeExecutor.generateBasicActivity(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  contentLayoutName: String,
  packageName: PackageName,
  menuName: String,
  isLauncher: Boolean,
  firstFragmentLayoutName: String,
  secondFragmentLayoutName: String,
  navGraphName: String
) {
  val (projectData, srcOut, resOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  addAllKotlinDependencies(moduleData)
  addMaterial3Dependency()

  // Generate the themes for material3 to make sure the Activity created for a flavor has an
  // appropriate set of themes
  generateMaterial3Themes(moduleData.themesData.main.name, moduleData.resDir)
  generateManifest(
    moduleData = moduleData,
    activityClass = activityClass,
    packageName = packageName,
    isLauncher = isLauncher,
    hasNoActionBar = true,
    activityThemeName = moduleData.themesData.main.name,
    generateActivityTitle = true)

  generateAppBar(
    moduleData, activityClass, packageName, contentLayoutName, layoutName, useAndroidX = true, isMaterial3 = true
  )
  addViewBindingSupport(moduleData.viewBindingSupport, true)
  addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  // navHostFragmentId needs to be unique, thus appending contentLayoutName since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_fragment_${contentLayoutName}"
  save(
      fragmentSimpleXml(
        navGraphName = navGraphName,
        navHostFragmentId = navHostFragmentId,
        useAndroidX = useAndroidX
      ),
      moduleData.resDir.resolve("layout/$contentLayoutName.xml")
  )
  if (moduleData.isNewModule) {
    generateSimpleMenu(packageName, activityClass, moduleData.resDir, menuName)
  }

  val ktOrJavaExt = projectData.language.extension
  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")
  val generateKotlin = projectData.language == Language.Kotlin
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val simpleActivity = basicActivityKt(
      isNewProject = moduleData.isNewModule,
      applicationPackage = projectData.applicationPackage,
      packageName = packageName,
      useAndroidX = useAndroidX,
      activityClass = activityClass,
      layoutName = layoutName,
      menuName = menuName,
      navHostFragmentId = navHostFragmentId,
      isViewBindingSupported = isViewBindingSupported
  )

  save(simpleActivity, simpleActivityPath)

  val firstFragmentClass = layoutToFragment(firstFragmentLayoutName)
  val secondFragmentClass = layoutToFragment(secondFragmentLayoutName)
  val firstFragmentClassContent = firstFragmentKt(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      firstFragmentLayoutName = firstFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported
  )
  val secondFragmentClassContent = secondFragmentKt(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      secondFragmentLayoutName = secondFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported
  )
  val firstFragmentLayoutContent = fragmentFirstLayout(firstFragmentClass)
  val secondFragmentLayoutContent = fragmentSecondLayout(secondFragmentClass)
  save(firstFragmentClassContent, srcOut.resolve("$firstFragmentClass.$ktOrJavaExt"))
  save(secondFragmentClassContent, srcOut.resolve("$secondFragmentClass.$ktOrJavaExt"))
  save(firstFragmentLayoutContent, resOut.resolve("layout/$firstFragmentLayoutName.xml"))
  save(secondFragmentLayoutContent, resOut.resolve("layout/$secondFragmentLayoutName.xml"))

  val navGraphContent = navGraphXml(
    packageName = packageName,
    firstFragmentClass = firstFragmentClass,
    secondFragmentClass = secondFragmentClass,
    firstFragmentLayoutName = firstFragmentLayoutName,
    secondFragmentLayoutName = secondFragmentLayoutName,
    navGraphName = navGraphName
  )
  mergeXml(navGraphContent, resOut.resolve("navigation/${navGraphName}.xml"))
  mergeXml(stringsXml, resOut.resolve("values/strings.xml"))

  if (generateKotlin) {
    addDependency("android.arch.navigation:navigation-fragment-ktx:+")
    addDependency("android.arch.navigation:navigation-ui-ktx:+")
  }

  requireJavaVersion("1.8", true)
  open(simpleActivityPath)

  open(resOut.resolve("layout/$contentLayoutName"))
  open(srcOut.resolve("$activityClass.$ktOrJavaExt"))
}
