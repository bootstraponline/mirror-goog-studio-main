/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.FQCN_AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.FQCN_BUTTON
import com.android.SdkConstants.FQCN_CHECKED_TEXT_VIEW
import com.android.SdkConstants.FQCN_CHECK_BOX
import com.android.SdkConstants.FQCN_EDIT_TEXT
import com.android.SdkConstants.FQCN_IMAGE_BUTTON
import com.android.SdkConstants.FQCN_IMAGE_VIEW
import com.android.SdkConstants.FQCN_MULTI_AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.FQCN_RADIO_BUTTON
import com.android.SdkConstants.FQCN_RATING_BAR
import com.android.SdkConstants.FQCN_SEEK_BAR
import com.android.SdkConstants.FQCN_SPINNER
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UClass

/** Looks for subclasses of custom widgets in projects using app compat */
class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(CLASS_VIEW)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val project = context.mainProject
        if (project.dependsOn(APPCOMPAT_LIB_ARTIFACT) !== true) {
            return
        }

        val superClass = declaration.superClass ?: return
        if (!hasAppCompatDelegate(context, superClass)) {
            return
        }

        var locationNode: PsiElement = declaration
        val extendsList = declaration.extendsList
        if (extendsList != null) {
            val elements = extendsList.referenceElements
            if (elements.isNotEmpty()) {
                locationNode = elements[0]
            }
        }
        val location = context.getNameLocation(locationNode)
        val suggested = getAppCompatDelegate(superClass)
        val message = "This custom view should extend `$suggested` instead"
        val actionLabel = "Extend AppCompat widget instead"
        val fix = fix().name(actionLabel)
            .sharedName(actionLabel)
            .replace()
            .all()
            .with(suggested)
            .autoFix()
            .build()
        context.report(ISSUE, declaration, location, message, fix)
    }

    companion object {
        /** Copy/pasted item decorator code */
        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in
                `androidx.appcompat.widget.AppCompatTextView`.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE
            )
        )

        private fun getAppCompatDelegate(superClass: PsiClass): String {
            return "android.support.v7.widget.AppCompat" + superClass.name
        }

        private fun hasAppCompatDelegate(
            context: JavaContext,
            superClass: PsiClass?
        ): Boolean {
            superClass ?: return false

            val qualifiedName = superClass.qualifiedName
            if (qualifiedName == null || !qualifiedName.startsWith("android.widget.")) {
                return false
            }

            // Set of android.widget widgets that have appcompat replacements
            when (qualifiedName) {
                FQCN_AUTO_COMPLETE_TEXT_VIEW, FQCN_BUTTON, FQCN_CHECK_BOX,
                FQCN_CHECKED_TEXT_VIEW, FQCN_EDIT_TEXT, FQCN_IMAGE_BUTTON, FQCN_IMAGE_VIEW,
                FQCN_MULTI_AUTO_COMPLETE_TEXT_VIEW, FQCN_RADIO_BUTTON, FQCN_RATING_BAR,
                FQCN_SEEK_BAR, FQCN_SPINNER, FQCN_TEXT_VIEW -> return true
            }

            // Extending some other android.widget. Instead of hardcoding "no", look for
            // the expected app compat class in the current compilation context.
            return context.evaluator.findClass(getAppCompatDelegate(superClass)) != null
        }
    }
}
