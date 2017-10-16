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

package com.android.builder.desugaring;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.desugaring.samples.BaseInterface;
import com.android.builder.desugaring.samples.ClassLambdaInParam;
import com.android.builder.desugaring.samples.ClassSuperClassAndInterface;
import com.android.builder.desugaring.samples.FunInterface;
import com.android.builder.desugaring.samples.FunInterfaceSubtype;
import com.android.builder.desugaring.samples.LambdaClass;
import com.android.builder.desugaring.samples.LambdaOfSubtype;
import com.android.builder.desugaring.samples.SampleClass;
import com.android.builder.desugaring.samples.SampleInterface;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.objectweb.asm.Type;

/** Tests for the desugaring dependencies analysis. */
public class DesugaringClassAnalyzerTest {

    @Test
    public void testBaseInterface() throws IOException {
        DesugaringGraph graph = analyze(BaseInterface.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(Object.class, ImmutableSet.of(BaseInterface.class)), graph);
        assertFullDependenciesGraph(Object.class, ImmutableSet.of(BaseInterface.class), graph);
    }

    @Test
    public void testClassImplementingInterface() throws IOException {
        DesugaringGraph graph =
                analyze(BaseInterface.class, SampleInterface.class, SampleClass.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(
                        Object.class,
                                ImmutableSet.of(
                                        BaseInterface.class,
                                        SampleInterface.class,
                                        SampleClass.class),
                        BaseInterface.class, ImmutableSet.of(SampleInterface.class),
                        SampleInterface.class, ImmutableSet.of(SampleClass.class)),
                graph);

        assertFullDependenciesGraph(
                Object.class,
                ImmutableSet.of(BaseInterface.class, SampleInterface.class, SampleClass.class),
                graph);
        assertFullDependenciesGraph(
                BaseInterface.class,
                ImmutableSet.of(SampleInterface.class, SampleClass.class),
                graph);
    }

    @Test
    public void testSubClassAndLambda() throws IOException {
        DesugaringGraph graph = analyze(LambdaClass.class, FunInterface.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(
                        Object.class, ImmutableSet.of(FunInterface.class),
                        SampleClass.class, ImmutableSet.of(LambdaClass.class),
                        FunInterface.class, ImmutableSet.of(LambdaClass.class)),
                graph);
        assertFullDependenciesGraph(SampleClass.class, ImmutableSet.of(LambdaClass.class), graph);
        assertFullDependenciesGraph(LambdaClass.class, ImmutableSet.of(), graph);
    }

    @Test
    public void testLambdaOfSubtype() throws IOException {
        DesugaringGraph graph =
                analyze(LambdaOfSubtype.class, FunInterface.class, FunInterfaceSubtype.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(
                        Object.class,
                                ImmutableSet.of(
                                        FunInterface.class,
                                        LambdaOfSubtype.class,
                                        FunInterfaceSubtype.class),
                        FunInterface.class, ImmutableSet.of(FunInterfaceSubtype.class),
                        FunInterfaceSubtype.class, ImmutableSet.of(LambdaOfSubtype.class)),
                graph);
        assertFullDependenciesGraph(
                FunInterface.class,
                ImmutableSet.of(FunInterfaceSubtype.class, LambdaOfSubtype.class),
                graph);
    }

    @Test
    public void testClassHasSuperClassAndSuperInterface() throws IOException {
        DesugaringGraph graph = analyze(ClassSuperClassAndInterface.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(
                        SampleClass.class, ImmutableSet.of(ClassSuperClassAndInterface.class),
                        BaseInterface.class, ImmutableSet.of(ClassSuperClassAndInterface.class)),
                graph);
        assertFullDependenciesGraph(
                BaseInterface.class, ImmutableSet.of(ClassSuperClassAndInterface.class), graph);
    }

    @Test
    public void testClassWithLambdaInParams() throws IOException {
        DesugaringGraph graph = analyze(ClassLambdaInParam.class);

        assertDirectDependenciesGraph(
                ImmutableMap.of(
                        Object.class, ImmutableSet.of(ClassLambdaInParam.class),
                        FunInterfaceSubtype.class, ImmutableSet.of(ClassLambdaInParam.class)),
                graph);
    }

    @NonNull
    private DesugaringGraph analyze(@NonNull Class<?>... classes) throws IOException {
        Set<DesugaringData> data = Sets.newHashSet();
        for (Class<?> klass : classes) {
            try (InputStream is = getClassInput(klass)) {
                data.add(DesugaringClassAnalyzer.analyze(getPath(klass), is));
            }
        }
        return new DesugaringGraph(data);
    }

    private void assertDirectDependenciesGraph(
            @NonNull Map<Class<?>, Set<Class<?>>> ownerToDeps, @NonNull DesugaringGraph graph) {
        for (Class<?> owner : ownerToDeps.keySet()) {
            Set<String> internalNames =
                    ownerToDeps.get(owner).stream().map(this::internal).collect(Collectors.toSet());

            assertThat(graph.getDependents(internal(owner)))
                    .named("direct dependencies of " + owner.getName())
                    .containsExactlyElementsIn(internalNames);
        }

        // now confirm reverse lookup
        Map<Class<?>, Set<Class<?>>> depToOwners = Maps.newHashMap();
        for (Map.Entry<Class<?>, Set<Class<?>>> ownerAndDeps : ownerToDeps.entrySet()) {
            for (Class<?> dep : ownerAndDeps.getValue()) {
                Set<Class<?>> owners = depToOwners.getOrDefault(dep, Sets.newHashSet());
                owners.add(ownerAndDeps.getKey());
                depToOwners.put(dep, owners);
            }
        }

        for (Class<?> dep : depToOwners.keySet()) {
            Set<String> internalNames =
                    depToOwners.get(dep).stream().map(this::internal).collect(Collectors.toSet());

            assertThat(graph.getDependencies(internal(dep)))
                    .named("direct owners of " + dep.getName())
                    .containsExactlyElementsIn(internalNames);
        }
    }

    private void assertFullDependenciesGraph(
            @NonNull Class<?> owner,
            @NonNull Collection<Class<?>> deps,
            @NonNull DesugaringGraph graph) {
        Set<String> internalNames = deps.stream().map(this::internal).collect(Collectors.toSet());
        assertThat(graph.getAllDependentTypes(internal(owner)))
                .named("Full list of dependencies for " + owner.getName())
                .containsExactlyElementsIn(internalNames);
    }

    @NonNull
    private InputStream getClassInput(Class<?> klass) {
        return this.getClass()
                .getClassLoader()
                .getResourceAsStream(internal(klass) + SdkConstants.DOT_CLASS);
    }

    @NonNull
    private String internal(@NonNull Class<?> klass) {
        return Type.getInternalName(klass);
    }

    @NonNull
    private Path getPath(@NonNull Class<?> klass) {
        return Paths.get(internal(klass));
    }
}
