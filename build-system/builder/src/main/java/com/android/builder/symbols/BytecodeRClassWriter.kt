/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.symbols

import com.android.SdkConstants
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarFlinger
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.canonicalizeValueResourceName
import com.android.resources.ResourceType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Type.INT_TYPE
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.IOException
import java.nio.file.Path
import java.util.EnumSet
import java.util.zip.Deflater.NO_COMPRESSION

@Throws(IOException::class)
fun exportToCompiledJava(
        tables: Iterable<SymbolTable>,
        outJar: Path,
        finalIds: Boolean = false,
        rPackage: String? = null,
) {
    JarFlinger(outJar).use { jarCreator ->
        // NO_COMPRESSION because R.jar isn't packaged into final APK or AAR
        jarCreator.setCompressionLevel(NO_COMPRESSION)
        val mergedTables = tables.groupBy { it.tablePackage }.map { SymbolTable.merge(it.value) }
        mergedTables.forEach { table ->
            exportToCompiledJava(table, jarCreator, finalIds, rPackage)
        }
    }
}

@Throws(IOException::class)
fun exportToCompiledJava(
    table: SymbolTable,
    jarMerger: JarCreator,
    finalIds: Boolean = false,
    rPackage: String? = null,
) {
    val resourceTypes = EnumSet.noneOf(ResourceType::class.java)
    for (resType in ResourceType.values()) {
        // Don't write empty R$ classes.
        val bytes = generateResourceTypeClass(table, resType, finalIds, rPackage) ?: continue
        resourceTypes.add(resType)
        val innerR = internalName(table, resType)
        jarMerger.addEntry(innerR + SdkConstants.DOT_CLASS, bytes.inputStream())
    }

    // Generate and write the main R class file.
    val packageR = internalName(table, null)
    jarMerger.addEntry(
        packageR + SdkConstants.DOT_CLASS,
        generateOuterRClass(resourceTypes, packageR).inputStream())
}


private fun generateOuterRClass(resourceTypes: EnumSet<ResourceType>, packageR: String): ByteArray {
    val cw = ClassWriter(COMPUTE_MAXS)
    cw.visit(
            Opcodes.V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
            packageR, null,
            "java/lang/Object", null)

    for (rt in resourceTypes) {
        cw.visitInnerClass(
                packageR + "$" + rt.getName(),
                packageR,
                rt.getName(),
                ACC_PUBLIC + ACC_FINAL + ACC_STATIC)
    }

    // Constructor
    val mv: MethodVisitor
    mv = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(ALOAD, 0)
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()

    cw.visitEnd()

    return cw.toByteArray()
}

private fun generateResourceTypeClass(
    table: SymbolTable, resType: ResourceType, finalIds: Boolean, rPackage: String?): ByteArray? {
    val symbols = table.getSymbolByResourceType(resType)
    if (symbols.isEmpty()) {
        return null
    }
    val cw = ClassWriter(COMPUTE_MAXS)
    val internalName = internalName(table, resType)
    cw.visit(
            Opcodes.V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
            internalName, null,
            "java/lang/Object", null)

    cw.visitInnerClass(
            internalName,
            internalName(table, null),
            resType.getName(),
            ACC_PUBLIC + ACC_FINAL + ACC_STATIC)

    if (resType == ResourceType.ATTR) {
        // Starting S, the android attributes might not have a stable ID and a reference to the
        // android.R.attr class should be used instead of a int value.
        cw.visitInnerClass(
                "android/R\$attr",
                "android/R",
                resType.getName(),
                ACC_PUBLIC + ACC_FINAL + ACC_STATIC)
    }

    for (s in symbols) {
        cw.visitField(
                ACC_PUBLIC + ACC_STATIC + if (finalIds) ACC_FINAL else 0,
                s.canonicalName,
                s.javaType.desc,
                null,
                if (s is Symbol.StyleableSymbol || rPackage != null) null else s.intValue
        )
                .visitEnd()

        if (s is Symbol.StyleableSymbol) {
            val children = s.children
            for ((i, child) in children.withIndex()) {
                cw.visitField(
                        ACC_PUBLIC + ACC_STATIC + if (finalIds) ACC_FINAL else 0,
                        "${s.canonicalName}_${canonicalizeValueResourceName(child)}",
                        "I",
                        null,
                        i)
            }
        }
    }

    // Constructor
    val init = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
    init.visitCode()
    init.visitVarInsn(ALOAD, 0)
    init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    init.visitInsn(RETURN)
    init.visitMaxs(0, 0)
    init.visitEnd()

    // init method
    if (resType == ResourceType.STYLEABLE || rPackage != null) {
        val method = Method("<clinit>", "()V")
        val clinit = GeneratorAdapter(ACC_PUBLIC.or(ACC_STATIC), method, null, null, cw)
        clinit.visitCode()
        if (rPackage != null) {
            clinit.visitFieldInsn(GETSTATIC, rPackage.replace(".", "/") + "/RPackage", "packageId", "I")
            clinit.storeLocal(1, INT_TYPE)
        }

        for (s in symbols) {
            if (resType == ResourceType.STYLEABLE) {
                s as Symbol.StyleableSymbol
                val values = s.values
                clinit.push(values.size)
                clinit.newArray(INT_TYPE)

                for ((i, value) in values.withIndex()) {
                    if (isUnstableAndroidAttr(value, s.children[i])) {
                        // For unstable android attributes a reference to android.R.attr should be used
                        // instead of the value (0).
                        val name = s.children[i].substringAfter("android").drop(1)
                        clinit.dup()
                        clinit.push(i)
                        clinit.visitFieldInsn(
                                GETSTATIC,
                                "android/R\$attr",
                                canonicalizeValueResourceName(name),
                                "I")
                        clinit.arrayStore(INT_TYPE)
                    } else {
                        clinit.dup()
                        clinit.push(i)
                        if (rPackage != null) {
                            clinit.push(value - 0x7f000000)
                            clinit.loadLocal(1)
                            clinit.visitInsn(Opcodes.IADD)
                        } else {
                            clinit.push(value)
                        }
                        clinit.arrayStore(INT_TYPE)
                    }
                }

                clinit.visitFieldInsn(PUTSTATIC, internalName, s.canonicalName, "[I")
            } else {
                clinit.push(s.intValue - 0x7f000000)
                clinit.loadLocal(1)
                clinit.visitInsn(Opcodes.IADD)
                clinit.visitFieldInsn(PUTSTATIC, internalName, s.canonicalName, "I")
            }
        }
        clinit.returnValue()
        clinit.endMethod()
    }

    cw.visitEnd()

    return cw.toByteArray()
}

private fun isUnstableAndroidAttr(value: Int, name: String) : Boolean {
    // Only platform attributes should have ID value of 0, but check the prefix to
    // be safe. Sometimes the name is already canonicalized, so either "android."
    // or "android_" can be used.
    return value == 0 && (
            name.startsWith("android.")
                    || name.startsWith("android_")
                    || name.startsWith("android:"))
}

private fun internalName(table: SymbolTable, type: ResourceType?): String {
    val className = if (type == null) "R" else "R$${type.getName()}"

    return if (table.tablePackage.isEmpty()) {
        className
    } else {
        "${table.tablePackage.replace(".", "/")}/$className"
    }
}

/**
 * Write RPackage class for privacy sandbox SDKs
 *
 * See b/243502800
 */
fun writeRPackages(packageNameToId: Map<String, Int>, outJar: Path) {
    JarFlinger(outJar).use { jarCreator ->
        // NO_COMPRESSION because RPackage.jar isn't packaged into final APK or AAR
        jarCreator.setCompressionLevel(NO_COMPRESSION)
        packageNameToId.forEach { (packageName, packageId) ->
            val (internalName, bytes) = generateRPackageClass(packageName, packageId)
            jarCreator.addEntry(
                    internalName + SdkConstants.DOT_CLASS,
                    bytes.inputStream()
            )
        }
    }
}

fun generateRPackageClass(packageName: String, packageId: Int): Pair<String, ByteArray> {
    val cw = ClassWriter(COMPUTE_MAXS)
    val internalName = packageName.replace(".", "/") + "/" + "RPackage"
    cw.visit(
            Opcodes.V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
            internalName, null,
            "java/lang/Object", null)
    cw.visitField(ACC_PUBLIC + ACC_STATIC + ACC_FINAL, "packageId", "I", null, packageId)
    cw.visitEnd()
    return Pair(internalName, cw.toByteArray())
}

