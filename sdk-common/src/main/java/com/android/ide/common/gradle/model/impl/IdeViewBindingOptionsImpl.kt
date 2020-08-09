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
package com.android.ide.common.gradle.model.impl

import com.android.builder.model.ViewBindingOptions
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import java.io.Serializable
import java.util.Objects

class IdeViewBindingOptionsImpl(
  override val enabled: Boolean
) : IdeViewBindingOptions, Serializable {

  val hashCode: Int = calculateHashCode()

  // Used for serialization by the IDE.
  constructor() : this(enabled = false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    if (other !is IdeViewBindingOptionsImpl) return false

    return Objects.equals(enabled, other.enabled)
  }

  override fun hashCode(): Int = hashCode

  override fun toString(): String = "IdeViewBindingOptions{enabled=$enabled}"

  private fun calculateHashCode() : Int = Objects.hash(enabled)
}