/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.StringWriter

/**
 * This is the key in the compiler settings cache.
 */
data class CmakeCompilerCacheKey(
    val ndkInstallationFolder : File?,
    val ndkSourceProperties : SdkSourceProperties?,
    val args: List<String>) {

    /**
     * Write to a file.
     */
    fun toFile(file : File) {
        FileWriter(file).use { writer ->
            GSON_BUILDER.toJson(this, writer)
        }
    }

    /**
     * Write to string.
     */
    fun toJsonString() : String {
        val writer = StringWriter()
        GSON_BUILDER.toJson(this, writer)
        return writer.toString()
    }

    companion object {
        private val TYPE_TOKEN = object : TypeToken<CmakeCompilerCacheKey>() {}
        private val ADAPTER = Gson().getAdapter<CmakeCompilerCacheKey>(TYPE_TOKEN)
        private val GSON_BUILDER = GsonBuilder().setPrettyPrinting().create()

        /**
         * Read from a file.
         */
        fun fromFile(file : File) : CmakeCompilerCacheKey {
            val reader = JsonReader(FileReader(file))
            reader.isLenient = false
            return ADAPTER.read(reader)
        }
    }
}