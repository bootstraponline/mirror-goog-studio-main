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
package com.android.build.gradle.internal.tasks.mlkit.codegen

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getAssociatedFileInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getFieldInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getInputProcessorInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getOutputProcessorInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getOutputsClassInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getProcessInjector
import com.android.tools.mlkit.MetadataExtractor
import com.android.tools.mlkit.MlkitNames
import com.android.tools.mlkit.ModelInfo
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.ArrayList
import javax.lang.model.element.Modifier

/** Generator to generate code for tflite model. */
class TfliteModelGenerator(
    modelFile: File,
    private val packageName: String,
    private val localModelPath: String
) : ModelGenerator {
    private val logger: Logger = Logging.getLogger(this.javaClass)
    private val modelInfo: ModelInfo = ModelInfo.buildFrom(
        MetadataExtractor(ByteBuffer.wrap(modelFile.readBytes()))
    )
    private val className: String = MlkitNames.computeModelClassName(modelFile)

    override fun generateBuildClass(outputDirProperty: DirectoryProperty) {
        val classBuilder = TypeSpec.classBuilder(className).addModifiers(
            Modifier.PUBLIC,
            Modifier.FINAL
        )
        if (modelInfo.isMetadataExisted) {
            classBuilder.addJavadoc(modelInfo.modelDescription)
        } else {
            classBuilder.addJavadoc(
                "This model doesn't have metadata, so no javadoc can be generated."
            )
        }
        buildFields(classBuilder)
        buildConstructor(classBuilder)
        buildStaticNewInstanceMethod(classBuilder)
        buildGetAssociatedFileMethod(classBuilder)
        buildProcessMethod(classBuilder)
        buildInnerClass(classBuilder)

        // Final steps.
        try {
            JavaFile.builder(packageName, classBuilder.build()).build()
                .writeTo(outputDirProperty.asFile.get())
        } catch (e: IOException) {
            logger.debug("Failed to write mlkit generated java file")
        }
    }

    private fun buildFields(classBuilder: TypeSpec.Builder) {
        for (tensorInfo in modelInfo.inputs) {
            getFieldInjector().inject(classBuilder, tensorInfo)
        }
        for (tensorInfo in modelInfo.outputs) {
            getFieldInjector().inject(classBuilder, tensorInfo)
        }
        val model = FieldSpec.builder(ClassNames.MODEL, FIELD_MODEL)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        classBuilder.addField(model)
    }

    private fun buildGetAssociatedFileMethod(classBuilder: TypeSpec.Builder) {
        val methodBuilder = MethodSpec.methodBuilder("getAssociatedFile")
            .addParameter(ClassNames.CONTEXT, "context")
            .addParameter(String::class.java, "fileName")
            .addModifiers(Modifier.PRIVATE)
            .addException(IOException::class.java)
            .returns(InputStream::class.java)
        methodBuilder
            .addStatement(
                "\$T inputStream = context.getAssets().open(\$S)",
                InputStream::class.java,
                localModelPath
            )
            .addStatement(
                "\$T zipFile = new \$T(new \$T(\$T.toByteArray(inputStream)))",
                ClassNames.ZIP_FILE,
                ClassNames.ZIP_FILE,
                ClassNames.SEEKABLE_IN_MEMORY_BYTE_CHANNEL,
                ClassNames.IO_UTILS
            )
            .addStatement("return zipFile.getRawInputStream(zipFile.getEntry(fileName))")
        classBuilder.addMethod(methodBuilder.build())
    }

    private fun buildInnerClass(classBuilder: TypeSpec.Builder) {
        getOutputsClassInjector().inject(classBuilder, modelInfo.outputs)
    }

    private fun buildConstructor(classBuilder: TypeSpec.Builder) {
        val constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(ClassNames.CONTEXT, "context")
            .addException(ClassNames.IO_EXCEPTION)
            .addStatement(
                "\$L = new \$T.Builder(context, \$S).build()",
                FIELD_MODEL,
                ClassNames.MODEL,
                localModelPath
            )

        // Init preprocessor
        for (tensorInfo in modelInfo.inputs) {
            if (tensorInfo.isMetadataExisted) {
                val preprocessorInjector = getInputProcessorInjector(tensorInfo)
                preprocessorInjector.inject(constructorBuilder, tensorInfo)
            }
        }

        // Init associated file and postprocessor
        for (tensorInfo in modelInfo.outputs) {
            if (tensorInfo.isMetadataExisted) {
                val postprocessorInjector = getOutputProcessorInjector(tensorInfo)
                postprocessorInjector.inject(constructorBuilder, tensorInfo)

                val codeBlockInjector: CodeBlockInjector = getAssociatedFileInjector()
                codeBlockInjector.inject(constructorBuilder, tensorInfo)
            }
        }
        classBuilder.addMethod(constructorBuilder.build())
    }

    private fun buildProcessMethod(classBuilder: TypeSpec.Builder) {
        val outputType: TypeName = ClassName.get(packageName, className)
            .nestedClass(MlkitNames.OUTPUTS)
        val localOutputs = "outputs"
        val methodBuilder = MethodSpec.methodBuilder("process")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassNames.NON_NULL)
            .returns(outputType)
        val byteBufferList: MutableList<String> = ArrayList()
        for (tensorInfo in modelInfo.inputs) {
            val processedTypeName = getProcessedTypeName(tensorInfo)
            val parameterSpec = ParameterSpec.builder(getParameterType(tensorInfo), tensorInfo.name)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            methodBuilder.addParameter(parameterSpec)
            byteBufferList.add("$processedTypeName.getBuffer()")
        }
        for (tensorInfo in modelInfo.inputs) {
            getProcessInjector(tensorInfo).inject(methodBuilder, tensorInfo)
        }
        methodBuilder.addStatement("\$T \$L = new \$T(model)", outputType, localOutputs, outputType)
        methodBuilder.addStatement(
            "\$L.run(\$L, \$L.getBuffer())",
            FIELD_MODEL,
            getObjectArrayString(byteBufferList.toTypedArray()),
            localOutputs
        )
        methodBuilder.addStatement("return \$L", localOutputs)
        classBuilder.addMethod(methodBuilder.build())
    }

    private fun buildStaticNewInstanceMethod(classBuilder: TypeSpec.Builder) {
        val returnType: TypeName = ClassName.get(packageName, className)
        val methodBuilder = MethodSpec.methodBuilder("newInstance")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(ClassNames.CONTEXT, "context")
            .addException(ClassNames.IO_EXCEPTION)
            .addAnnotation(ClassNames.NON_NULL)
            .returns(returnType)
            .addStatement("return new \$T(context)", returnType)
        classBuilder.addMethod(methodBuilder.build())
    }

    companion object {
        private const val FIELD_MODEL = "model"
    }
}