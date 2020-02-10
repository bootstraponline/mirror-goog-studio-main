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

package com.android.build.api.variant.impl

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.utils.appendCamelCase
import java.io.File
import java.io.Serializable
import java.util.Locale

data class VariantOutputConfigurationImpl(
    private val isUniversal: Boolean = false,
    override val filters: Collection<FilterConfiguration> = listOf()
) : VariantOutputConfiguration, Serializable {

    override val outputType: VariantOutputConfiguration.OutputType
        get() {
            if (isUniversal) return VariantOutputConfiguration.OutputType.UNIVERSAL
            return if (filters.isEmpty()) VariantOutputConfiguration.OutputType.SINGLE
            else VariantOutputConfiguration.OutputType.ONE_OF_MANY
        }

    /**
     * Returns the [FilterConfiguration] for a particular [FilterConfiguration.FilterType] or null
     * if not such filter is configured on this variant output
     */
    fun getFilter(type: FilterConfiguration.FilterType)
            : FilterConfiguration? = filters.firstOrNull { it.filterType == type }
}

fun VariantOutputConfiguration.dirName(): String {
    return when (this.outputType) {
        VariantOutputConfiguration.OutputType.UNIVERSAL -> outputType.name
        VariantOutputConfiguration.OutputType.SINGLE -> ""
        VariantOutputConfiguration.OutputType.ONE_OF_MANY ->
            filters.map(FilterConfiguration::identifier).joinToString(File.separator)
        else -> throw RuntimeException("Unhandled OutputType $this")
    }
}

fun VariantOutputConfiguration.OutputType.fullName(
    voc: VariantOutputConfiguration,  variantDslInfo: VariantDslInfo): String {
    return when (this) {
        VariantOutputConfiguration.OutputType.UNIVERSAL ->
            variantDslInfo.computeFullNameWithSplits(name.toLowerCase(Locale.US))
        VariantOutputConfiguration.OutputType.SINGLE ->
            variantDslInfo.componentIdentity.name
        VariantOutputConfiguration.OutputType.ONE_OF_MANY -> {
            val filterName = voc.filters.getFilterName()
            return variantDslInfo.computeBaseNameWithSplits(filterName)
        }
        else -> throw RuntimeException("Unhandled OutputType $this")
    }
}

fun Collection<FilterConfiguration>.joinToString() =
    this.joinToString { filter -> filter.identifier }

fun Collection<FilterConfiguration>.getFilterName(): String {
    val sb = StringBuilder()
    val densityFilter = firstOrNull { it.filterType == FilterConfiguration.FilterType.DENSITY }?.identifier
    if (densityFilter != null) {
        sb.append(densityFilter)
    }
    val abiFilter = firstOrNull() { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
    if (abiFilter != null) {
        sb.appendCamelCase(abiFilter)
    }
    return sb.toString()
}