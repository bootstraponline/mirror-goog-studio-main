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
package com.android.tools.idea.wizard.template.impl.activities.common.navigation

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.res.layout.fragmentFirstXml
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui.firstFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui.firstFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui.viewModelJava
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui.viewModelKt
import com.android.tools.idea.wizard.template.underscoreToCamelCase
import java.io.File

fun RecipeExecutor.saveFragmentAndViewModel(
  resOut: File,
  srcOut: File,
  language: Language,
  packageName: String,
  fragmentPrefix: String,
  useAndroidX: Boolean = true
) {
  val firstFragmentClass = "${underscoreToCamelCase(fragmentPrefix)}Fragment"
  val secondFragmentPrefix = "${fragmentPrefix}_second"
  // TODO(qumeric): check if it is correct?
  val secondFragmentClass = "${underscoreToCamelCase(secondFragmentPrefix)}Fragment"

  val viewModelClass = "${underscoreToCamelCase(fragmentPrefix)}ViewModel"

  val generateKotlin = language == Language.Kotlin

  save(
    fragmentFirstXml(packageName, fragmentPrefix, firstFragmentClass, useAndroidX),
    resOut.resolve("layout/fragment_${fragmentPrefix}.xml")
  )
  save(
    if (generateKotlin)
      firstFragmentKt(packageName, firstFragmentClass, fragmentPrefix, viewModelClass, useAndroidX)
    else
      firstFragmentJava(packageName, firstFragmentClass, fragmentPrefix, viewModelClass, useAndroidX),
    srcOut.resolve("ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${language.extension}")
  )
  save(
    if (generateKotlin)
      viewModelKt(packageName, fragmentPrefix, viewModelClass, useAndroidX)
    else
      viewModelJava(packageName, fragmentPrefix, viewModelClass, useAndroidX),
    srcOut.resolve("ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${language.extension}")
  )
}

fun RecipeExecutor.navigationDependencies(
  generateKotlin: Boolean,
  useAndroidX: Boolean,
  buildApi: Int
) {
  addDependency("android.arch.navigation:navigation-fragment:+")
  addDependency("android.arch.navigation:navigation-ui:+")
  addDependency("android.arch.lifecycle:extensions:+")
  if (generateKotlin) {
    addDependency("android.arch.navigation:navigation-fragment-ktx:+")
    addDependency("android.arch.navigation:navigation-ui-ktx:+")
  }
  /*
  navigation-ui depends on the stable version of design library.
  This is to remove the lint warning for the generated project may not use the same version of the support library.
  */
  if (!useAndroidX) {
    addDependency("com.android.support:design:${buildApi}.+")
  }
}
