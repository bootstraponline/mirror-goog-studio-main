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
package com.android.tools.idea.wizard.template

import kotlin.reflect.KClass

/**
 * Constraints that can be applied to a parameter which helps the UI add a validator etc for user input.
 * These are typically combined into a set of constraints via an EnumSet.
 */
enum class Constraint {
  /**
   * This value must be unique. This constraint usually only makes sense when other constraints are specified, such as [LAYOUT],
   * which means that the parameter should designate a name that does not represent an existing layout resource name.
   */
  UNIQUE,
  /**
   * This value must already exist. This constraint usually only makes sense when other constraints are specified, such as [LAYOUT],
   * which means that the parameter should designate a name that already exists as a resource name.
   */
  EXISTS,
  /** The associated value must not be empty. */
  NONEMPTY,
  /** The associated value should represent a fully qualified activity class name. */
  ACTIVITY,
  /** The associated value should represent an API level. */
  API_LEVEL,
  /** The associated value should represent a valid class name. */
  CLASS,
  /** The associated value should represent a valid package name. */
  PACKAGE,
  /** The associated value should represent a valid Android application package name. */
  APP_PACKAGE,
  /** The associated value should represent a valid Module name. */
  MODULE,
  /** The associated value should represent a valid layout resource name. */
  LAYOUT,
  /** The associated value should represent a valid drawable resource name. */
  DRAWABLE,
  /** The associated value should represent a valid values file name. */
  NAVIGATION,
  /** The associated value should represent a valid navigation resource name. */
  VALUES,
  /** The associated value should represent a valid id resource name. */
  ID,
  /** The associated value should represent a valid source directory name. */
  SOURCE_SET_FOLDER,
  /** The associated value should represent a valid string resource name. */
  STRING,
  /**  The associated value should represent a valid URI authority. Format: [userinfo@]host[:port] */
  URI_AUTHORITY;
}

/**
 * This is a parameter which is a part of [Template].
 *
 * Each parameter will be rendered to its own field when rendering UI from [Template], albeit possibly disabled or hidden.
 * A user should provide [value]s to all parameters via interacting with UI.
 * Later this data is passed to the [Recipe] and used to render actual template files.
 */
// TODO(qumeric): consider renaming/changing the whole hierarchy to make users more aware that it is used for rendering UI
sealed class Parameter<T> {
  /** Name of the parameter. Should be unique. */
  abstract val name: String
  open val help: String? = null
  abstract val defaultValue: T
  abstract var value: T
  // should be updated only by [Parameters]
  internal lateinit var wizardParameterData: WizardParameterData

  /**
   * Tells if the [Parameter] should be shown in UI.
   *
   * We do not show parameters which are not visible in UI, but use them (fill with data and send to the [Recipe]).
   * @see enabled
   */
  val isVisibleAndEnabled: Boolean
    get() = enabled && visible

  /**
   * Returns false if the [Parameter] should be completely ignored (sometimes will be rendered in gray in UI).
   */
  abstract val enabled: Boolean
  abstract val visible: Boolean
}

/**
 * A wrapper which overrides some [Parameter] methods with given lambdas.
 *
 * Actual parameters should inherit this, not [Parameter].
 */
sealed class DslParameter<T>(
  private val _visible: WizardParameterData.() -> Boolean = { true },
  private val _enabled: WizardParameterData.() -> Boolean = { true }
): Parameter<T>() {
  override val enabled get() = wizardParameterData._enabled()
  override val visible get()= wizardParameterData._visible()
}

/**
 * String parameter. Rendered as a text field in UI.
 */
data class StringParameter(
  override val name: String,
  override val help: String? = null,
  private val _visible: WizardParameterData.() -> Boolean = { true },
  private val _enabled: WizardParameterData.() -> Boolean = { true },
  override val defaultValue: String,
  val constraints: List<Constraint>,
  private val _suggest: () -> String? = { null },
  val type: Type = Type.STRING
) : DslParameter<String>(_visible, _enabled) {
  override var value: String = defaultValue

  /**
   * Value suggested by the Studio. If it was evaluated to null, then [defaultValue] is used.
   * Often calculated using different parameters, e.g "activity_super" layout name generated from "SuperActivity".
   */
  fun suggest() = _suggest()

  /**
   * Different [Type]s of StringParameter have different UIs.
   */
  enum class Type {
    STRING,
    LANGUAGE,
    PACKAGE_NAME
  }
}

/**
 * Enum parameter. Rendered as a combo box in UI.
 */
data class EnumParameter<T: Enum<T>>(
  private val enumClass: KClass<T>,
  override val name: String,
  override val help: String? = null,
  private val _visible: WizardParameterData.() -> Boolean = { true },
  private val _enabled: WizardParameterData.() -> Boolean = { true },
  override val defaultValue: T
) : DslParameter<T>(_visible, _enabled) {
  override var value: T = defaultValue
  val options: Array<T> = enumClass.java.enumConstants
}

/**
 * Boolean parameter. Rendered as a checkbox in UI.
 */
data class BooleanParameter(
  override val name: String,
  override val help: String? = null,
  private val _visible: WizardParameterData.() -> Boolean = { true },
  private val _enabled: WizardParameterData.() -> Boolean = { true },
  override val defaultValue: Boolean
) : DslParameter<Boolean>(_visible, _enabled) {
  override var value: Boolean = defaultValue
}

/**
 * Separator. Not passed to the render, only shows in UI.
 */
class Separator: DslParameter<Nothing?>({true}, {true}) {
  override val name: String = ""
  override val defaultValue: Nothing? get() = throw UnsupportedOperationException()
  override var value: Nothing?
    get() = throw UnsupportedOperationException()
    set(_) = throw UnsupportedOperationException()
}
