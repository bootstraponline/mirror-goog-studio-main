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
package com.android.ide.common.resources

import com.android.ide.common.rendering.api.*
import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.resources.ResourceType
import com.android.resources.ResourceType.*
import com.google.common.collect.ImmutableBiMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ResourceResolverNamespacesTest {

    private val app = ResourceNamespace.fromPackageName("com.example.app")
    private val localLib = ResourceNamespace.fromPackageName("com.example.common")
    private val supportLib = ResourceNamespace.fromPackageName("com.android.support")

    private lateinit var resolver: ResourceResolver

    private fun StyleResourceValue.withItems(
        resolver: Resolver,
        vararg items: ItemResourceValue
    ): StyleResourceValue {
        for (item in items) {
            item.setNamespaceResolver(resolver)
            this.addItem(item)
        }
        return this
    }

    @Before
    fun createRes() {
        val androidRes = listOf(
            ResourceValue(ANDROID, COLOR, "black", "#000000"),
            ResourceValue(ANDROID, COLOR, "white", "#ffffff"),
            AttrResourceValue(ResourceReference(ANDROID, ATTR, "colorPrimary"), null)
        )

        val supportRes = listOf(
            ResourceValue(supportLib, COLOR, "material_blue", "#0000ff"),
            AttrResourceValue(ResourceReference(supportLib, ATTR, "fabColor"), null),
            AttrResourceValue(ResourceReference(supportLib, ATTR, "actionBarColor"), null)
        )

        val libRes = listOf(
            ResourceValue(localLib, COLOR, "logo_color", "@android:color/black"),
            ResourceValue(localLib, COLOR, "missing_ns", "@support:color/material_blue"),
            ResourceValue(localLib, STRING, "company_name", "Example"),
            ResourceValue(localLib, STRING, "header", "@string/company_name"),
            ResourceValue(localLib, STRING, "wrong_ref", "@string/made_up"),
            ResourceValue(
                localLib,
                STRING,
                "header_explicit",
                "@com.example.common:string/company_name"
            ),

            StyleResourceValue(
                ResourceReference(localLib, STYLE, "Theme.Base"),
                "android:Theme",
                null
            ).withItems(
                Resolver.EMPTY_RESOLVER,
                ItemResourceValue(app, "com.android.support:actionBarColor", "#00ff00", null)
            )
        )

        val appPrefixes = Resolver.fromBiMap(
            ImmutableBiMap.of(
                "common", localLib.xmlNamespaceUri,
                "support", supportLib.xmlNamespaceUri
            )
        )

        val appRes = listOf(
            ResourceValue(app, COLOR, "image_color", "@common:color/logo_color"),
            ResourceValue(app, COLOR, "title_bar_color", "@support:color/material_blue"),
            ResourceValue(app, COLOR, "broken_chain_color", "@common:color/missing_ns"),

            ResourceValue(app, COLOR, "from_theme_1", "?android:colorPrimary"),
            ResourceValue(app, COLOR, "from_theme_2", "?support:fabColor"),
            ResourceValue(app, COLOR, "from_theme_3", "?support:actionBarColor"),

            ResourceValue(app, STRING, "title_text", "@com.example.common:string/header"),
            ResourceValue(
                app,
                STRING,
                "title_text_explicit",
                "@com.example.common:string/header_explicit"
            ),

            StyleResourceValue(
                ResourceReference(app, STYLE, "AppTheme"),
                "common:Theme.Base",
                null
            ).withItems(
                appPrefixes,
                ItemResourceValue(app, "android:colorPrimary", "@android:color/white", null),
                ItemResourceValue(app, "support:fabColor", "@color/image_color", null)
            )
        )
        appRes.forEach { it.setNamespaceResolver(appPrefixes) }

        val allRes = sequenceOf(androidRes, appRes, supportRes, libRes)
            .flatMap { it.asSequence() }
            .asIterable()

        resolver = ResourceResolver.withValues(allRes, ResourceReference(app, STYLE, "AppTheme"))
    }

    private fun check(
        namespace: ResourceNamespace,
        type: ResourceType,
        name: String,
        resolvesTo: String
    ) {
        val ref = ResourceReference(namespace, type, name)
        assertNotNull(resolver.getUnresolvedResource(ref))
        val resolved = resolver.getResolvedResource(ref)
        assertNotNull(resolved)
        assertEquals(ref.resourceUrl.toString(), resolvesTo, resolved!!.value)
    }

    @Test
    fun referencesWithinLib() {
        check(localLib, STRING, "company_name", resolvesTo = "Example")

        val header = ResourceReference(localLib, STRING, "header")
        assertEquals("@string/company_name", resolver.getUnresolvedResource(header)?.value)
        resolver.getResolvedResource(header)!!.let { resolved ->
            assertEquals("Example", resolved.value)
            assertEquals("company_name", resolved.name)
        }

        val headerExplicit = ResourceReference(localLib, STRING, "header_explicit")
        resolver.getResolvedResource(headerExplicit)!!.let { resolved ->
            assertEquals("Example", resolved.value)
            assertEquals("company_name", resolved.name)
        }

        check(localLib, STRING, "wrong_ref", resolvesTo = "@string/made_up")
    }

    @Test
    fun referencesAcrossNamespaces() {
        check(app, COLOR, "image_color", resolvesTo = "#000000")
        check(app, COLOR, "title_bar_color", resolvesTo = "#0000ff")
        check(app, STRING, "title_text", resolvesTo = "Example")
        check(app, STRING, "title_text_explicit", resolvesTo = "Example")

        // The "support" prefix is defined in the context of `app` resources (using
        // setNamespaceResolver to simulate the xmlns syntax), but not in the context of `localLib`
        // (which is assumed to use fully qualified package names in XML). Below we make sure that
        // the resolution process doesn't "leak" the defined prefixes as it walks the graph.
        check(app, COLOR, "broken_chain_color", resolvesTo = "@support:color/material_blue")
    }

    @Test
    fun themeResolution() {
        check(app, COLOR, "from_theme_1", resolvesTo = "#ffffff")
        check(app, COLOR, "from_theme_2", resolvesTo = "#000000")
        check(app, COLOR, "from_theme_3", resolvesTo = "#00ff00")
    }
}
