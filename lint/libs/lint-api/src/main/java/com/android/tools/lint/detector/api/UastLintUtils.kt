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

package com.android.tools.lint.detector.api

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.detector.api.ConstantEvaluator.LastAssignmentFinder.LastAssignmentValueUnknown
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.UastVisitor

class UastLintUtils {
    companion object {
        @JvmStatic
        fun UElement.tryResolveUDeclaration(): UDeclaration? {
            return (this as? UResolvable)?.resolve().toUElementOfType()
        }

        /** Returns the containing file for the given element. */
        @JvmStatic
        fun getContainingFile(
            context: JavaContext,
            element: PsiElement?
        ): PsiFile? {
            if (element == null) {
                return null
            }

            val containingFile = element.containingFile
            return if (containingFile != context.psiFile) {
                getContainingFile(element)
            } else containingFile
        }

        /** Returns the containing file for the given element. */
        @JvmStatic
        fun getPsiFile(file: UFile?): PsiFile? {
            return file?.let { getContainingFile(it.psi) }
        }

        /** Returns the containing file for the given element. */
        @JvmStatic
        fun getContainingFile(element: PsiElement?): PsiFile? {
            if (element == null) {
                return null
            }

            val containingFile = element as? PsiFile ?: element.containingFile ?: return null

            // In Kotlin files identifiers are sometimes using LightElements that are hosted in
            // a placeholder file, these do not have the right PsiFile as containing elements
            if (containingFile is FakeFileForLightClass) {
                return containingFile.ktFile
            }
            return containingFile
        }

        @JvmStatic
        fun getQualifiedName(element: PsiElement): String? = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod ->
                element.containingClass?.let { getQualifiedName(it) }
                    ?.let { "$it.${element.name}" }
            is PsiField ->
                element.containingClass?.let { getQualifiedName(it) }
                    ?.let { "$it.${element.name}" }
            else -> null
        }

        @JvmStatic
        fun getClassName(type: PsiClassType): String {
            val psiClass = type.resolve()
            return psiClass?.let { getClassName(it) } ?: type.className
        }

        @JvmStatic
        fun getClassName(psiClass: PsiClass): String {
            val stringBuilder = StringBuilder()
            stringBuilder.append(psiClass.name)

            var currPsiClass = psiClass.containingClass
            while (currPsiClass != null) {
                stringBuilder.insert(0, currPsiClass.name + ".")
                currPsiClass = currPsiClass.containingClass
            }
            return stringBuilder.toString()
        }

        @JvmStatic
        fun findLastAssignment(
            variable: PsiVariable,
            call: UElement
        ): UExpression? {
            var currVariable = variable
            var lastAssignment: UElement? = null

            if (currVariable is UVariable) {
                currVariable = currVariable.psi
            }

            if (!currVariable.hasModifierProperty(PsiModifier.FINAL) && (currVariable is PsiLocalVariable || currVariable is PsiParameter)) {
                val containingFunction = call.getContainingUMethod()
                if (containingFunction != null) {
                    val finder = ConstantEvaluator.LastAssignmentFinder(
                        currVariable, call, null, -1
                    )
                    containingFunction.accept(finder)
                    lastAssignment = finder.lastAssignment
                }
            } else {
                lastAssignment = UastFacade.getInitializerBody(currVariable)
            }

            return if (lastAssignment is UExpression) lastAssignment
            else null
        }

        @JvmStatic
        fun getReferenceName(expression: UReferenceExpression): String? {
            if (expression is USimpleNameReferenceExpression) {
                return expression.identifier
            } else if (expression is UQualifiedReferenceExpression) {
                val selector = expression.selector
                if (selector is USimpleNameReferenceExpression) {
                    return selector.identifier
                }
            }

            return null
        }

        @JvmStatic
        fun findLastValue(
            variable: PsiVariable,
            call: UElement,
            evaluator: ConstantEvaluator
        ): Any? {
            var value: Any? = null

            if (!variable.hasModifierProperty(PsiModifier.FINAL) && (variable is PsiLocalVariable || variable is PsiParameter)) {
                val containingFunction = call.getContainingUMethod()
                if (containingFunction != null) {
                    val body = containingFunction.uastBody
                    if (body != null) {
                        val finder = ConstantEvaluator.LastAssignmentFinder(
                            variable, call, evaluator, 1
                        )
                        body.accept(finder)
                        value = finder.currentValue

                        if (value == null && finder.lastAssignment != null) {
                            // Special return value: variable was assigned, but we don't know
                            // the value
                            return LastAssignmentValueUnknown
                        }
                    }
                }
            } else {
                val initializer = UastFacade.getInitializerBody(variable)
                if (initializer != null) {
                    value = initializer.evaluate()
                }
            }

            return value
        }

        @JvmStatic
        fun toAndroidReferenceViaResolve(element: UElement): ResourceReference? {
            return ResourceReference.get(element)
        }

        @JvmStatic
        fun areIdentifiersEqual(first: UExpression, second: UExpression): Boolean {
            val firstIdentifier = getIdentifier(first)
            val secondIdentifier = getIdentifier(second)
            return (
                firstIdentifier != null &&
                    secondIdentifier != null &&
                    firstIdentifier == secondIdentifier
                )
        }

        @JvmStatic
        fun getIdentifier(expression: UExpression): String? =
            when (expression) {
                is ULiteralExpression -> expression.asRenderString()
                is UQualifiedReferenceExpression -> {
                    val receiverIdentifier = getIdentifier(expression.receiver)
                    val selectorIdentifier = getIdentifier(expression.selector)
                    if (receiverIdentifier == null || selectorIdentifier == null) {
                        null
                    } else "$receiverIdentifier.$selectorIdentifier"
                }
                else -> null
            }

        @JvmStatic
        fun isNumber(argument: UElement): Boolean =
            when (argument) {
                is ULiteralExpression -> argument.value is Number
                is UPrefixExpression -> isNumber(argument.operand)
                else -> false
            }

        @JvmStatic
        fun isZero(argument: UElement): Boolean {
            if (argument is ULiteralExpression) {
                val value = argument.value
                return value is Number && value.toInt() == 0
            }
            return false
        }

        @JvmStatic
        fun isMinusOne(argument: UElement): Boolean {
            return if (argument is UUnaryExpression) {
                val operand = argument.operand
                if (operand is ULiteralExpression && argument.operator === UastPrefixOperator.UNARY_MINUS) {
                    val value = operand.value
                    value is Number && value.toInt() == 1
                } else {
                    false
                }
            } else {
                false
            }
        }

        @JvmStatic
        fun getAnnotationValue(annotation: UAnnotation): UExpression? {
            return annotation.findDeclaredAttributeValue(ATTR_VALUE)
        }

        @JvmStatic
        fun getLongAttribute(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Long
        ): Long {
            return getAnnotationLongValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getDoubleAttribute(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Double
        ): Double {
            return getAnnotationDoubleValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getBoolean(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Boolean
        ): Boolean {
            return getAnnotationBooleanValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getAnnotationBooleanValue(
            annotation: UAnnotation?,
            name: String
        ): Boolean? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationBooleanValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationBooleanValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Boolean
        ): Boolean {
            val value = getAnnotationBooleanValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationLongValue(
            annotation: UAnnotation?,
            name: String
        ): Long? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationLongValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationLongValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Long
        ): Long {
            val value = getAnnotationLongValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationDoubleValue(
            annotation: UAnnotation?,
            name: String
        ): Double? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationDoubleValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationDoubleValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Double
        ): Double {
            val value = getAnnotationDoubleValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationStringValue(
            annotation: UAnnotation?,
            name: String
        ): String? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationStringValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationStringValues(
            annotation: UAnnotation?,
            name: String
        ): Array<String>? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationStringValues(annotation, name)
        }

        @JvmStatic
        fun containsAnnotation(list: List<UAnnotation>, annotation: UAnnotation): Boolean =
            list.stream().anyMatch { e -> e === annotation }

        @JvmStatic
        fun containsAnnotation(
            list: List<UAnnotation>,
            qualifiedName: String
        ): Boolean = list.stream().anyMatch { e -> e.qualifiedName == qualifiedName }
    }
}

fun PsiParameter.isReceiver(): Boolean {
    // It's tempting to just check
    //    this is KtUltraLightReceiverParameter
    // here, but besides that being an internal class, that approach
    // would only work for source references; for compiled Kotlin
    // code (including the runtime library) these will just be
    // ClsParameterImpl, so we have to resort to name patterns anyway.

    // Fully qualified names here because we can't suppress usage in import list
    val name = name
    return name.startsWith("\$this") || name.startsWith("\$self")
}

/**
 * For a qualified or parenthesized expression, returns the selector, or
 * otherwise returns self.
 */
fun UElement.findSelector(): UElement {
    var curr = this
    while (true) {
        curr = when (curr) {
            is UQualifiedReferenceExpression -> curr.selector
            is UParenthesizedExpression -> curr.expression
            else -> break
        }
    }
    return curr
}

/**
 * For an element in an expression, returns the next expression in the
 * same block if any.
 */
fun UElement.nextStatement(): UExpression? {
    var prev = this
    var curr = prev.uastParent
    while (curr != null) {
        if (curr is UBlockExpression) {
            val expressions = curr.expressions
            val index = expressions.indexOf(prev)
            return if (index != -1 && index < expressions.size - 1) {
                expressions[index + 1]
            } else {
                null
            }
        }
        prev = curr
        curr = curr.uastParent
    }

    return null
}

/**
 * For an element in an expression, returns the previous expression in
 * the same block if any.
 */
fun UElement.previousStatement(): UExpression? {
    var prev = this
    var curr = prev.uastParent
    while (curr != null) {
        if (curr is UBlockExpression) {
            val expressions = curr.expressions
            val index = expressions.indexOf(prev)
            return if (index > 0) {
                expressions[index - 1]
            } else {
                null
            }
        }
        prev = curr
        curr = curr.uastParent
    }

    return null
}

/**
 * Returns true if [this] element is a child or indirect child of the
 * given [parent]. If [strict] is false, this method will return true
 * when [parent] is the same as [this].
 */
fun UElement.isBelow(parent: UElement, strict: Boolean = false): Boolean {
    var curr = if (strict) uastParent else this
    while (curr != null) {
        if (curr == parent) {
            return true
        }
        curr = curr.uastParent
    }
    return false
}

/**
 * Returns true if [this] element is a child or indirect child of the
 * given [parent]. If [strict] is false, this method will return true
 * when [parent] is the same as [this].
 */
fun PsiElement?.isBelow(parent: PsiElement, strict: Boolean = false): Boolean {
    this ?: return false
    return PsiTreeUtil.isAncestor(parent, this, strict)
}

/**
 * Like [UFile.accept], but in the case of multi-file classes (where
 * multiple source files containing top level declarations are annotated
 * with `@JvmMultifileClass`, all naming the same target class) the
 * [UFile] will contain functions and properties from *different* files.
 * Since lint is visiting each source file, that means it would visit
 * these methods multiple times, since they're included in the single
 * large [UFile] built up for each individual source file fragment.
 *
 * This method will visit a [UFile], but will limit itself to visiting
 * just the parts corresponding to the source file that the [UFile] was
 * constructed from.
 */
fun UFile.acceptSourceFile(visitor: UastVisitor) {
    val sourcePsi = this.sourcePsi
    if (sourcePsi is KtFile &&
        sourcePsi.annotationEntries.any { it.shortName.toString() == "JvmMultifileClass" }
    ) {
        acceptMultiFileClass(visitor)
    } else {
        accept(visitor)
    }
}

/**
 * Visits a multi-file class, limiting itself to just the parts from the
 * same source file as the root node.
 *
 * This method basically mirrors the implementation of [UFile.accept]
 * and [UClass.accept], except that it specifically looks for
 * declarations that seem to have been merged in from a different source
 * file than the origin one at the top level, and it then skips those.
 */
private fun UFile.acceptMultiFileClass(visitor: UastVisitor) {
    val targetFile = sourcePsi.virtualFile
    if (visitor.visitFile(this)) return

    //noinspection ExternalAnnotations
    uAnnotations.acceptList(visitor)
    imports.acceptList(visitor)

    for (uClass in classes) {
        if (visitor.visitClass(uClass)) return
        //noinspection ExternalAnnotations
        uClass.uAnnotations.acceptList(visitor)
        for (declaration in uClass.uastDeclarations) {
            val declarationFile = declaration.sourcePsi?.containingFile?.virtualFile
            if (declarationFile == null || declarationFile == targetFile) {
                declaration.accept(visitor)
            }
        }
        visitor.afterVisitClass(uClass)
    }

    visitor.afterVisitFile(this)
}
