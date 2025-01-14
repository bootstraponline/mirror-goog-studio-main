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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_KT;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import kotlin.io.FilesKt;
import org.intellij.lang.annotations.Language;
import org.jetbrains.kotlin.util.capitalizeDecapitalize.CapitalizeDecapitalizeKt;

/** A utility class which provides unit test file descriptions */
public class TestFiles {

    private TestFiles() {}

    @NonNull
    public static TestFile file() {
        return new TestFile();
    }

    @NonNull
    public static TestFile source(@NonNull String to, @NonNull String source) {
        return file().to(to).withSource(source);
    }

    @NonNull
    public static TestFile java(@NonNull String to, @NonNull @Language("JAVA") String source) {
        return TestFile.JavaTestFile.create(to, source);
    }

    @NonNull
    public static TestFile java(@NonNull @Language("JAVA") String source) {
        return TestFile.JavaTestFile.create(source);
    }

    @NonNull
    public static TestFile kt(@NonNull @Language("kotlin") String source) {
        return kotlin(source);
    }

    @NonNull
    public static TestFile kt(@NonNull String to, @NonNull @Language("kotlin") String source) {
        return kotlin(to, source);
    }

    @NonNull
    public static TestFile kotlin(@NonNull @Language("kotlin") String source) {
        return TestFile.KotlinTestFile.create(source);
    }

    @NonNull
    public static TestFile kotlin(@NonNull String to, @NonNull @Language("kotlin") String source) {
        return TestFile.KotlinTestFile.create(to, source);
    }

    @NonNull
    public static TestFile xml(@NonNull String to, @NonNull @Language("XML") String source) {
        if (!to.endsWith(DOT_XML)) {
            throw new IllegalArgumentException("Expected .xml suffix for XML test file");
        }

        return TestFile.XmlTestFile.create(to, source);
    }

    @NonNull
    public static TestFile copy(
            @NonNull String from, @NonNull TestResourceProvider resourceProvider) {
        return file().from(from, resourceProvider).to(from);
    }

    @NonNull
    public static TestFile copy(
            @NonNull String from,
            @NonNull String to,
            @NonNull TestResourceProvider resourceProvider) {
        return file().from(from, resourceProvider).to(to);
    }

    @NonNull
    public static TestFile.GradleTestFile gradle(
            @NonNull String to, @NonNull @Language("Groovy") String source) {
        return new TestFile.GradleTestFile(to, source);
    }

    @NonNull
    public static TestFile.GradleTestFile gradle(@NonNull @Language("Groovy") String source) {
        return new TestFile.GradleTestFile(FN_BUILD_GRADLE, source);
    }

    @NonNull
    public static TestFile.ManifestTestFile manifest() {
        return new TestFile.ManifestTestFile();
    }

    @NonNull
    public static TestFile manifest(@NonNull @Language("XML") String source) {
        return TestFiles.source(ANDROID_MANIFEST_XML, source);
    }

    @NonNull
    public static TestFile.PropertyTestFile projectProperties() {
        return new TestFile.PropertyTestFile();
    }

    @NonNull
    public static TestFile.BinaryTestFile bytecode(
            @NonNull String to, @NonNull TestFile.BytecodeProducer producer) {
        return new TestFile.BinaryTestFile(to, producer);
    }

    @NonNull
    public static TestFile rClass(@NonNull String pkg, @NonNull String... urls) {
        int id = 0x7f040000;
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n");
        sb.append("public final class R {\n");
        Map<ResourceType, List<ResourceUrl>> map = new HashMap<>();
        for (String url : urls) {
            ResourceUrl reference = ResourceUrl.parse(url);
            assertNotNull("Resource reference was not a valid URL: " + url, reference);
            List<ResourceUrl> list = map.computeIfAbsent(reference.type, o -> new ArrayList<>());
            list.add(reference);
        }
        for (ResourceType type : ResourceType.values()) {
            List<ResourceUrl> resources = map.get(type);
            if (resources == null) {
                continue;
            }
            sb.append("    public static final class ").append(type).append(" {\n");
            for (ResourceUrl resource : resources) {
                sb.append("        public static final int ")
                        .append(resource.name)
                        .append(" = 0x")
                        .append(Integer.toHexString(id++))
                        .append(";\n");
            }
            sb.append("    }\n");
        }
        sb.append("}");
        return java(sb.toString());
    }

    @NonNull
    public static TestFile.BinaryTestFile bytes(@NonNull String to, @NonNull byte[] bytes) {
        TestFile.BytecodeProducer producer =
                new TestFile.BytecodeProducer() {
                    @NonNull
                    @Override
                    public byte[] produce() {
                        return bytes;
                    }
                };
        return new TestFile.BinaryTestFile(to, producer);
    }

    @NonNull
    public static String toBase64(@NonNull byte[] bytes) {
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "\"\"\n+ \""
                + Joiner.on("\"\n+ \"").join(Splitter.fixedLength(60).split(base64))
                + "\"";
    }

    // Backwards compat: default to Java formatting
    public static String toBase64gzip(@NonNull byte[] bytes) {
        return toBase64gzipJava(bytes, 0, false, true);
    }

    public static String toBase64gzipJava(
            @NonNull byte[] bytes, int indent, boolean indentStart, boolean includeEmptyPrefix) {
        String base64 = toBase64gzipString(bytes);
        StringBuilder indentString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentString.append(' ');
        }

        Iterable<String> lines = Splitter.fixedLength(60).split(base64);
        StringBuilder result = new StringBuilder();
        if (indentStart) {
            result.append(indentString);
        }
        if (includeEmptyPrefix) {
            result.append("\"\" +\n");
            result.append(indentString);
        }
        result.append("\"");
        String separator = "\" +\n" + indentString + "\"";
        result.append(Joiner.on(separator).join(lines));
        result.append("\"");
        return result.toString();
    }

    public static String toBase64gzipKotlin(
            @NonNull byte[] bytes, int indent, boolean indentStart, boolean includeQuotes) {
        String base64 = toBase64gzipString(bytes).replace('$', '＄');
        StringBuilder indentString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentString.append(' ');
        }
        Iterable<String> lines = Splitter.fixedLength(60).split(base64);
        StringBuilder result = new StringBuilder();
        if (indentStart) {
            result.append(indentString);
        }
        if (includeQuotes) {
            result.append("\"\"\"\n");
            result.append(indentString);
        }
        result.append(Joiner.on("\n" + indentString.toString()).join(lines));
        if (includeQuotes) {
            result.append("\"\"\"");
        }
        result.append("\n");
        return result.toString();
    }

    private static String toBase64gzipString(@NonNull byte[] bytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream stream = new GZIPOutputStream(out)) {
                stream.write(bytes);
            }
            bytes = out.toByteArray();
            return Base64.getEncoder().encodeToString(bytes).replace('$', '＄');
        } catch (IOException ignore) {
            // Can't happen on a ByteArrayInputStream
            return "";
        }
    }

    @NonNull
    public static String toBase64(@NonNull File file) {
        return toBase64(FilesKt.readBytes(file));
    }

    @NonNull
    public static String toBase64gzip(@NonNull File file) {
        return toBase64gzip(FilesKt.readBytes(file));
    }

    @NonNull
    public static String toHexBytes(@NonNull File file) {
        return toHexBytes(FilesKt.readBytes(file));
    }

    /** Creates the string to initialize a {@link #hexBytes(String, String)} test file with. */
    @NonNull
    public static String toHexBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int column = 0;
        sb.append('"');
        for (byte b : bytes) {
            int i = b & 0xFF;
            String hex = Integer.toHexString(i);
            hex = CapitalizeDecapitalizeKt.toUpperCaseAsciiOnly(hex);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);

            column += 2;
            if (column > 60) {
                sb.append("\\n\" +\n\"");
                column = 0;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Creates a test file from the given base64 data. To create this data, use {@link
     * #toBase64(File)} or {@link #toBase64(byte[])}, for example via
     *
     * <pre>{@code assertEquals("", toBase64(new File("path/to/your.class")));}</pre>
     *
     * @param to the file to write as
     * @param encoded the encoded data
     * @return the new test file
     * @deprecated Use {@link #base64gzip(String, String)} instead
     */
    @Deprecated
    public static TestFile.BinaryTestFile base64(@NonNull String to, @NonNull String encoded) {
        encoded = encoded.replace('＄', '$');
        final byte[] bytes = Base64.getDecoder().decode(encoded);
        return new TestFile.BinaryTestFile(
                to,
                new TestFile.BytecodeProducer() {
                    @NonNull
                    @Override
                    public byte[] produce() {
                        return bytes;
                    }
                });
    }

    /**
     * Decodes base64 strings into gzip data, then decodes that into a data file. To create this
     * data, use {@link #toBase64gzip(File)} or {@link #toBase64gzip(byte[])}, for example via
     *
     * <pre>{@code assertEquals("", toBase64gzip(new File("path/to/your.class")));}</pre>
     */
    @NonNull
    public static TestFile.BinaryTestFile base64gzip(@NonNull String to, @NonNull String encoded) {
        return new TestFile.BinaryTestFile(to, getByteProducerForBase64gzip(encoded));
    }

    /**
     * Creates a bytecode producer which takes an encoded base64gzip string and returns the
     * uncompressed de-base64'ed byte array
     */
    @NonNull
    public static TestFile.ByteProducer getByteProducerForBase64gzip(@NonNull String encoded) {
        encoded =
                encoded
                        // Recover any $'s we've converted to ＄ to better handle Kotlin raw strings
                        .replace('＄', '$')
                        // Whitespace is not significant in base64 but isn't handled properly by
                        // the base64 decoder
                        .replace(" ", "")
                        .replace("\n", "")
                        .replace("\t", "");
        byte[] bytes = Base64.getDecoder().decode(encoded);

        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            GZIPInputStream stream = new GZIPInputStream(in);
            bytes = ByteStreams.toByteArray(stream);
        } catch (IOException ignore) {
            // Can't happen on a ByteArrayInputStream
        }

        byte[] finalBytes = bytes;
        return new TestFile.BytecodeProducer() {
            @NonNull
            @Override
            public byte[] produce() {
                return finalBytes;
            }
        };
    }

    /**
     * Decodes hex byte strings into the original byte array. To create this data, use {@link
     * #toHexBytes(File)} or {@link #toHexBytes(byte[])}, for example via
     *
     * <pre>{@code assertEquals("", toHexBytes(new File("path/to/your.class")));}</pre>
     *
     * Normally you'll be using {@link #base64gzip(String, String)} test files, since these are much
     * more compact. The main use case for hex byte files is very clearly seeing the binary contents
     * in the test description, and perhaps modifying these slightly (for example, to deliberately
     * change a field in a file format like a class file.)
     */
    @NonNull
    public static TestFile.BinaryTestFile hexBytes(@NonNull String to, @NonNull String encoded) {
        return new TestFile.BinaryTestFile(to, getByteProducerForHexBytes(encoded));
    }

    /**
     * Creates a bytecode producer which takes an encoded hex bytes string and returns the decoded
     * byte array.
     */
    @NonNull
    public static TestFile.ByteProducer getByteProducerForHexBytes(@NonNull String encoded) {
        encoded = encoded.replace(" ", "").replace("\n", "").replace("\t", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i += 2) {
            int b = Integer.parseInt(encoded.substring(i, i + 2), 16);
            out.write(b);
        }

        byte[] finalBytes = out.toByteArray();
        return new TestFile.BytecodeProducer() {
            @NonNull
            @Override
            public byte[] produce() {
                return finalBytes;
            }
        };
    }

    public static TestFile classpath(String... extraLibraries) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<classpath>\n"
                        + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                        + "\t<classpathentry kind=\"src\" path=\"gen\"/>\n"
                        + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                        + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                        + "\t<classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                        + "\t<classpathentry kind=\"output\" path=\"build/intermediates/javac/debug/classes\"/>\n");
        for (String path : extraLibraries) {
            sb.append("\t<classpathentry kind=\"lib\" path=\"").append(path).append("\"/>\n");
        }
        sb.append("</classpath>\n");
        return source(".classpath", sb.toString());
    }

    @NonNull
    public static TestFile.JarTestFile jar(@NonNull String to) {
        return new TestFile.JarTestFile(to);
    }

    @Deprecated // Use the method with the checksum instead
    public static CompiledSourceFile compiled(
            @NonNull String into, @NonNull TestFile source, @NonNull String... encoded) {
        CompiledSourceFile.Type type =
                source.targetRelativePath.endsWith(DOT_JAVA)
                                || source.targetRelativePath.endsWith(DOT_KT)
                        ? CompiledSourceFile.Type.SOURCE_AND_BYTECODE
                        : CompiledSourceFile.Type.RESOURCE;
        return new CompiledSourceFile(into, type, source, null, encoded);
    }

    public static CompiledSourceFile compiled(
            @NonNull String into,
            @NonNull TestFile source,
            long checksum,
            @NonNull String... encoded) {
        CompiledSourceFile.Type type =
                source.targetRelativePath.endsWith(DOT_JAVA)
                                || source.targetRelativePath.endsWith(DOT_KT)
                        ? CompiledSourceFile.Type.SOURCE_AND_BYTECODE
                        : CompiledSourceFile.Type.RESOURCE;
        return new CompiledSourceFile(into, type, source, checksum, encoded);
    }

    @Deprecated // Use the method with the checksum instead
    public static CompiledSourceFile bytecode(
            @NonNull String into, @NonNull TestFile source, @NonNull String... encoded) {
        CompiledSourceFile.Type type =
                source.targetRelativePath.endsWith(DOT_JAVA)
                                || source.targetRelativePath.endsWith(DOT_KT)
                        ? CompiledSourceFile.Type.BYTECODE_ONLY
                        : CompiledSourceFile.Type.RESOURCE;
        return new CompiledSourceFile(into, type, source, null, encoded);
    }

    public static CompiledSourceFile bytecode(
            @NonNull String into,
            @NonNull TestFile source,
            long checksum,
            @NonNull String... encoded) {
        CompiledSourceFile.Type type =
                source.targetRelativePath.endsWith(DOT_JAVA)
                                || source.targetRelativePath.endsWith(DOT_KT)
                        ? CompiledSourceFile.Type.BYTECODE_ONLY
                        : CompiledSourceFile.Type.RESOURCE;
        return new CompiledSourceFile(into, type, source, checksum, encoded);
    }

    @NonNull
    public static TestFile.JarTestFile jar(@NonNull String to, @NonNull TestFile... files) {
        if (!to.endsWith("jar") // don't insist on .jar since we're also supporting .srcjar etc
                && !to.endsWith("zip")) {
            throw new IllegalArgumentException("Expected .jar suffix for jar test file");
        }

        TestFile.JarTestFile jar = new TestFile.JarTestFile(to);
        jar.files(files);
        return jar;
    }

    public static TestFile.ImageTestFile image(@NonNull String to, int width, int height) {
        return new TestFile.ImageTestFile(to, width, height);
    }

    public static TestFile[] getLintClassPath() throws IOException {
        List<TestFile> paths = new ArrayList<>();
        List<File> libraries = KotlinClasspathKt.findFromRuntimeClassPath(TestFiles::isLintJar);

        for (File file : libraries) {
            TestFile testFile = new LibraryReferenceTestFile(file);
            paths.add(testFile);
        }

        return paths.toArray(new TestFile[0]);
    }

    private static boolean isLintJar(File file) {
        String name = file.getName();
        return name.startsWith("lint-")
                || name.startsWith("kotlin-compiler")
                || name.startsWith("uast-")
                || name.startsWith("intellij-core")
                || name.endsWith("uast.jar") // bazel
                || name.startsWith("android.sdktools.lint") // IJ ADT
                || name.endsWith(".lint-api-base") // IJ BASE
                || name.endsWith("lint-api.jar") // bazel
                || name.endsWith(".lint.checks-base") // IJ
                || name.endsWith("lint-checks.jar") // bazel
                || name.endsWith(".lint-model-base") // IJ
                || name.endsWith("lint-model.jar") // bazel
                || name.startsWith("lint-model") // Gradle
                || name.endsWith(".testutils")
                || name.endsWith("testutils.jar")
                || name.startsWith("testutils-")
                || name.endsWith(".lint.tests")
                || name.endsWith("lint-tests.jar") // bazel
                || (name.equals("main") && file.getPath().contains("lint-tests")) // Gradle
                || name.endsWith(".lint.cli");
    }

    public static class LibraryReferenceTestFile extends TestFile {
        public final File file;

        public LibraryReferenceTestFile(@NonNull File file) {
            this(file.getPath(), file);
        }

        public LibraryReferenceTestFile(@NonNull String to, @NonNull File file) {
            this.targetRelativePath = to;
            this.file = file;
        }
    }
}
