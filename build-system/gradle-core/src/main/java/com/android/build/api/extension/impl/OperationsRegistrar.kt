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

package com.android.build.api.extension.impl

import com.android.build.api.component.ActionableComponentObject
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.extension.VariantSelector
import org.gradle.api.Action

/**
 * Registrar object to keep track of Variant API operations registered on the [VariantBuilderT]
 */
class OperationsRegistrar<VariantBuilderT>
        where VariantBuilderT: ActionableComponentObject,
              VariantBuilderT: ComponentIdentity {

    private class Operation<VariantBuilderT>(
            val selector: VariantSelectorImpl<VariantBuilderT>,
            val callBack: Action<VariantBuilderT>
    ) where VariantBuilderT: ActionableComponentObject,
            VariantBuilderT: ComponentIdentity


    private val operations= mutableListOf<Operation<VariantBuilderT>>()

    private val noSelector = VariantSelectorImpl<VariantBuilderT>().all()

    fun addOperation(
            callback: Action<VariantBuilderT>,
            selector: VariantSelector<VariantBuilderT> = noSelector
    ) {
        operations.add(Operation(selector as VariantSelectorImpl, callback))
    }

    fun executeOperations(variant: VariantBuilderT) {
        operations.forEach { operation ->
            if (operation.selector.appliesTo(variant)) {
                operation.callBack.execute(variant)
            }
        }
    }
}
