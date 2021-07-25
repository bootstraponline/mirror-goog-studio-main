load(":coverage.bzl", "coverage_baseline", "coverage_java_test")
load(":functions.bzl", "explicit_target")
load(":maven.bzl", "MavenInfo", "generate_pom", "import_maven_library", "maven_pom", "split_coordinates")
load(":merge_archives.bzl", "merge_jars")
load(":lint.bzl", "lint_test")
load(":merge_archives.bzl", "create_manifest_argfile", "run_singlejar")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_runtime_toolchain", "find_java_toolchain")
load(":functions.bzl", "create_option_file")

def test_kotlin_use_ir():
    return select({
        "//tools/base/bazel:kotlin_no_use_ir": False,
        "//conditions:default": True,
    })

def kotlin_compile(ctx, name, srcs, deps, friends, out, jre, transitive_classpath):
    """Runs kotlinc on the given source files.

    Args:
        ctx: the analysis context
        name: the name of the module being compiled
        srcs: a list of Java and Kotlin source files
        deps: a list of JavaInfo providers from direct dependencies
        friends: a list of friend jars (allowing access to 'internal' members)
        out: the output jar file
        jre: list of jars from the JRE bootclasspath
        transitive_classpath: whether to include transitive deps in the compile classpath

    Returns:
        JavaInfo for the resulting jar.

    Expects that ctx.files._kotlinc is defined.

    Note: kotlinc only compiles Kotlin, not Java. So if there are Java
    sources, then you will also need to run javac after this action.
    """

    # TODO: Either disable transitive_classpath in all cases, or otherwise
    # implement strict-deps enforcement for Kotlin to ensure that targets
    # declare dependencies on everything they directly use.
    if transitive_classpath:
        deps = [java_common.make_non_strict(dep) for dep in deps]

    # Normally ABI jars are used at compile time, but the ABI jars generated by
    # ijar are incorrect for Kotlin because (for example) kotlinc expects to see
    # the bodies of inline functions in class files. So, we have to explicitly
    # put 'full_compile_jars' on the classpath instead.
    # TODO: Support ABI jars for Kotlin.
    dep_jars = depset(transitive = [dep.full_compile_jars for dep in deps])

    args = ctx.actions.args()

    args.add("-module-name", name)
    args.add("-nowarn")  # Mirrors the default javac opts.
    args.add("-jvm-target", "1.8")
    args.add("-api-version", "1.3")  # b/166582569
    args.add("-Xjvm-default=enable")
    args.add("-no-stdlib")

    # Dependency jars may be compiled with a new kotlinc IR backend.
    args.add("-Xallow-unstable-dependencies")

    # Add "use-ir" to enable the new IR backend for kotlinc tasks when the
    # attribute "kotlin_use_ir" is set
    if ctx.attr.kotlin_use_ir:
        args.add("-Xuse-ir")

    # Use custom JRE instead of the default one picked by kotlinc.
    args.add("-no-jdk")
    classpath = depset(direct = jre, transitive = [dep_jars])

    # Note: there are some open questions regarding the transitivity of friends.
    # See https://github.com/bazelbuild/rules_kotlin/issues/211.
    args.add_joined(friends, join_with = ",", format_joined = "-Xfriend-paths=%s")

    args.add_joined("-cp", classpath, join_with = ":")
    args.add("-o", out)
    args.add_all(srcs)

    # To enable persistent Bazel workers, all arguments must come in an argfile.
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")

    ctx.actions.run(
        inputs = depset(direct = srcs, transitive = [classpath]),
        outputs = [out],
        mnemonic = "kotlinc",
        arguments = [args],
        executable = ctx.executable._kotlinc,
        execution_requirements = {"supports-workers": "1"},
    )

    return JavaInfo(output_jar = out, compile_jar = out)

def _kotlin_jar_impl(ctx):
    deps = [dep[JavaInfo] for dep in ctx.attr.deps]
    deps.append(ctx.attr._kotlin_stdlib[JavaInfo])
    return kotlin_compile(
        ctx = ctx,
        name = ctx.attr.module_name,
        srcs = ctx.files.srcs,
        deps = deps,
        friends = ctx.files.friends,
        out = ctx.outputs.output_jar,
        jre = ctx.files._bootclasspath,
        transitive_classpath = True,  # Matches Java rules (sans strict-deps enforcement)
    )

_kotlin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_empty = False,
            allow_files = True,
        ),
        "friends": attr.label_list(
            allow_files = [".jar"],
        ),
        "deps": attr.label_list(
            providers = [JavaInfo],
        ),
        "module_name": attr.string(
            default = "unnamed",
        ),
        "kotlin_use_ir": attr.bool(),
        "_bootclasspath": attr.label(
            # Use JDK 8 because AGP still needs to support it (b/166472930).
            default = Label("//prebuilts/studio/jdk:bootclasspath"),
            allow_files = [".jar"],
        ),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True,
        ),
        "_kotlin_stdlib": attr.label(
            default = Label("//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"),
            allow_files = True,
        ),
    },
    outputs = {
        "output_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def kotlin_library_legacy(
        name,
        srcs,
        javacopts = [],
        resources = [],
        resource_strip_prefix = None,
        deps = [],
        runtime_deps = [],
        bundled_deps = [],
        friends = [],
        data = [],
        pom = None,
        exclusions = None,
        visibility = None,
        jar_name = None,
        testonly = False,
        lint_baseline = None,
        lint_classpath = [],
        lint_is_test_sources = False,
        lint_timeout = None,
        manifest_lines = None,
        module_name = None):
    """Compiles a library jar from Java and Kotlin sources"""
    kotlins = [src for src in srcs if src.endswith(".kt")]
    javas = [src for src in srcs if src.endswith(".java")]

    if not testonly:
        coverage_baseline(
            name = name,
            srcs = javas + kotlins,
        )

    if not kotlins and not javas:
        fail("No sources found for kotlin_library_legacy " + name)

    targets = []
    kdeps = []
    if kotlins:
        kotlin_name = name + ".kotlin"
        targets += [kotlin_name]
        kdeps += [kotlin_name]
        _kotlin_jar(
            name = kotlin_name,
            srcs = srcs,
            deps = deps + bundled_deps,
            friends = friends,
            visibility = visibility,
            testonly = testonly,
            module_name = module_name,
            kotlin_use_ir = test_kotlin_use_ir(),
        )

    java_name = name + ".java"
    resources_with_notice = native.glob(["NOTICE", "LICENSE"]) + resources if pom else resources
    final_javacopts = javacopts + ["--release", "8"]

    if javas or resources_with_notice:
        targets += [java_name]
        native.java_library(
            name = java_name,
            srcs = javas,
            javacopts = final_javacopts if javas else None,
            resources = resources_with_notice,
            resource_strip_prefix = resource_strip_prefix,
            deps = (kdeps + deps + bundled_deps) if javas else None,
            runtime_deps = runtime_deps,
            resource_jars = bundled_deps,
            visibility = visibility,
            testonly = testonly,
        )

    jar_name = jar_name if jar_name else "lib" + name + ".jar"
    merge_jars(
        name = name + ".singlejar",
        jars = [":lib" + target + ".jar" for target in targets],
        out = jar_name,
        manifest_lines = manifest_lines,
        allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    native.java_import(
        name = name,
        jars = [jar_name],
        deps = deps + ["//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"],
        data = data,
        visibility = visibility,
        testonly = testonly,
    )

    if pom:
        maven_pom(
            name = name + "_maven",
            deps = [explicit_target(dep) + "_maven" for dep in deps if not dep.endswith("_neverlink")],
            exclusions = exclusions,
            library = name,
            visibility = visibility,
            source = pom,
        )

    lint_srcs = javas + kotlins
    if lint_baseline:
        if not lint_srcs:
            fail("lint_baseline set for iml_module that has no sources")

        kwargs = {}
        if lint_timeout:
            kwargs["timeout"] = lint_timeout

        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = deps + bundled_deps + lint_classpath,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
            is_test_sources = lint_is_test_sources,
            **kwargs
        )

def kotlin_test(
        name,
        srcs,
        deps = [],
        runtime_deps = [],
        friends = [],
        visibility = None,
        lint_baseline = None,
        lint_classpath = [],
        **kwargs):
    kotlin_library(
        name = name + ".testlib",
        srcs = srcs,
        deps = deps,
        testonly = True,
        runtime_deps = runtime_deps,
        jar_name = name + ".jar",
        lint_baseline = lint_baseline,
        lint_classpath = lint_classpath,
        lint_is_test_sources = True,
        visibility = visibility,
        friends = friends,
    )

    coverage_java_test(
        name = name + ".test",
        runtime_deps = [
            ":" + name + ".testlib",
        ] + runtime_deps,
        visibility = visibility,
        **kwargs
    )

    native.test_suite(
        name = name,
        tests = [name + ".test"],
    )

# Rule set that supports both Java and Maven providers, still under development

# Creates actions to generate a resources_jar from the given resouces.
def _resources(ctx, resources, resources_jar):
    prefix = ctx.attr.resource_strip_prefix
    rel_paths = []
    for res in resources:
        short = res.short_path
        if short.startswith(prefix):
            short = short[len(prefix):]
            if short.startswith("/"):
                short = short[1:]
        rel_paths.append((short, res))
    zipper_args = ["c", resources_jar.path]
    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in rel_paths])
    zipper_list = create_option_file(ctx, resources_jar.basename + ".res.lst", zipper_files)
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = resources + [zipper_list],
        outputs = [resources_jar],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating resources zip...",
        mnemonic = "zipper",
    )

def kotlin_library(
        name,
        srcs,
        deps = None,
        javacopts = [],
        jar_name = None,
        lint_baseline = None,
        lint_classpath = [],
        lint_is_test_sources = False,
        lint_timeout = None,
        **kwargs):
    """Compiles a library jar from Java and Kotlin sources

    Args:
        srcs: The sources of the library.
        javacopts: Additional javac options.
        resources: Resources to add to the jar.
        resources_strip_prefix: The prefix to strip from the resources path.
        deps: The dependencies of this library.
        runtime_deps: The runtime dependencies.
        bundled_deps: The dependencies that are bundled inside the output jar and not treated as a maven dependency
        friends: The list of kotlin-friends.
        notice: An optional notice file to be included in the jar.
        coordinates: The maven coordinates of this artifact.
        exclusions: Files to exclude from the generated pom file.
        lint_*: Lint configuration arguments
        module_name: The kotlin module name.
    """

    kotlins = [src for src in srcs if src.endswith(".kt")]
    javas = [src for src in srcs if src.endswith(".java")]
    source_jars = [src for src in srcs if src.endswith(".srcjar")]
    final_javacopts = javacopts + ["--release", "8"]

    _kotlin_library(
        name = name,
        jar = jar_name if jar_name else "lib" + name + ".jar",
        java_srcs = javas,
        deps = deps,
        kotlin_srcs = kotlins,
        source_jars = source_jars,
        kotlin_use_ir = test_kotlin_use_ir(),
        javacopts = final_javacopts if javas else None,
        **kwargs
    )

    # TODO move lint tests out of here
    if lint_baseline:
        # TODO: use srcs once the migration is completed
        lint_srcs = javas + kotlins
        if not lint_srcs:
            fail("lint_baseline set for rule that has no sources")

        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = deps + lint_classpath,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
            is_test_sources = lint_is_test_sources,
            timeout = lint_timeout if lint_timeout else None,
        )

def _kotlin_library_impl(ctx):
    java_srcs = ctx.files.java_srcs
    kotlin_srcs = ctx.files.kotlin_srcs
    source_jars = ctx.files.source_jars
    name = ctx.label.name

    java_jar = ctx.actions.declare_file(name + ".java.jar") if java_srcs or source_jars else None
    kotlin_jar = ctx.actions.declare_file(name + ".kotlin.jar") if kotlin_srcs else None

    deps = [dep[JavaInfo] for dep in ctx.attr.deps] + [dep[JavaInfo] for dep in ctx.attr.bundled_deps]

    # Kotlin
    jars = []
    kotlin_providers = []
    if kotlin_srcs:
        deps.append(ctx.attr._kotlin_stdlib[JavaInfo])  # TODO why do we need stdlib
        kotlin_providers += [kotlin_compile(
            ctx = ctx,
            name = ctx.attr.module_name,
            srcs = java_srcs + kotlin_srcs,
            deps = deps,
            friends = ctx.files.friends,
            out = kotlin_jar,
            jre = ctx.files._bootclasspath,
            transitive_classpath = True,  # Matches Java rules (sans strict-deps enforcement)
        )]
        jars += [kotlin_jar]

    # Resources.
    resources = ([ctx.file.notice] if ctx.file.notice else []) + ctx.files.resources
    if resources:
        resources_jar = ctx.actions.declare_file(name + ".res.jar")
        _resources(ctx, resources, resources_jar)
        jars += [resources_jar]

    # Java
    if java_srcs or source_jars:
        java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain)

        java_provider = java_common.compile(
            ctx,
            source_files = java_srcs,
            source_jars = source_jars,
            output = java_jar,
            deps = deps + kotlin_providers,
            javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain) + ctx.attr.javacopts,
            java_toolchain = java_toolchain,
            host_javabase = find_java_runtime_toolchain(ctx, ctx.attr._host_javabase),
        )

        jars += [java_jar]

    manifest_argfile = None
    if ctx.files.manifests:
        manifest_argfile = create_manifest_argfile(ctx, name + ".manifest.lst", ctx.files.manifests)

    for dep in ctx.attr.bundled_deps:
        jars += [java_output.class_jar for java_output in dep[JavaInfo].outputs.jars]

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = ctx.outputs.jar,
        manifest_lines = ["@" + manifest_argfile.path] if manifest_argfile else [],
        extra_inputs = [manifest_argfile] if manifest_argfile else [],
        # allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    # Create an ijar to improve javac compilation avoidance.
    ijar = java_common.run_ijar(
        actions = ctx.actions,
        jar = ctx.outputs.jar,
        java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
    )

    providers = []
    providers = [JavaInfo(
        output_jar = ctx.outputs.jar,
        compile_jar = ijar,
        deps = deps,
        runtime_deps = deps,
    )]

    transitive_runfiles = depset(transitive = [
        dep[DefaultInfo].default_runfiles.files
        for dep in ctx.attr.deps + ctx.attr.bundled_deps
        if dep[DefaultInfo].default_runfiles
    ])
    runfiles = ctx.runfiles(files = ctx.files.data, transitive_files = transitive_runfiles)
    return [
        java_common.merge(providers),
        DefaultInfo(files = depset([ctx.outputs.jar]), runfiles = runfiles),
    ]

_kotlin_library = rule(
    attrs = {
        "java_srcs": attr.label_list(allow_files = True),
        "kotlin_srcs": attr.label_list(allow_files = True),
        "source_jars": attr.label_list(allow_files = True),
        "resources": attr.label_list(allow_files = True),
        "notice": attr.label(allow_single_file = True),
        "manifests": attr.label_list(allow_files = True),
        "data": attr.label_list(allow_files = True),
        "friends": attr.label_list(
            allow_files = [".jar"],
        ),
        "jar": attr.output(mandatory = True),
        "deps": attr.label_list(providers = [JavaInfo]),
        "bundled_deps": attr.label_list(
            providers = [JavaInfo],
        ),
        "runtime_deps": attr.label_list(
            providers = [JavaInfo],
        ),
        "module_name": attr.string(
            default = "unnamed",
        ),
        "resource_strip_prefix": attr.string(),
        "javacopts": attr.string_list(),
        "kotlin_use_ir": attr.bool(),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "_host_javabase": attr.label(default = Label("@bazel_tools//tools/jdk:current_host_java_runtime")),
        "_bootclasspath": attr.label(
            # Use JDK 8 because AGP still needs to support it (b/166472930).
            default = Label("//prebuilts/studio/jdk:bootclasspath"),
            allow_files = [".jar"],
        ),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True,
        ),
        "_kotlin_stdlib": attr.label(
            default = Label("//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"),
            allow_files = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    fragments = ["java"],
    implementation = _kotlin_library_impl,
)

def _maven_library_impl(ctx):
    infos = [dep[MavenInfo] for dep in ctx.attr.deps]
    pom_deps = [info.pom for info in infos]

    coordinates = split_coordinates(ctx.attr.coordinates)
    basename = coordinates.artifact_id + "-" + coordinates.version
    generate_pom(
        ctx,
        output_pom = ctx.outputs.pom,
        group = coordinates.group_id,
        artifact = coordinates.artifact_id,
        version = coordinates.version,
        deps = pom_deps,
    )
    repo_files = [
        (coordinates.repo_path + "/" + basename + ".pom", ctx.outputs.pom),
        (coordinates.repo_path + "/" + basename + ".jar", ctx.file.library),
    ]
    if ctx.file.notice:
        repo_files.append((coordinates.repo_path + "/" + ctx.file.notice.basename, ctx.file.notice))

    transitive = depset(direct = repo_files, transitive = [info.transitive for info in infos])

    return [
        ctx.attr.library[JavaInfo],
        MavenInfo(
            pom = ctx.outputs.pom,
            files = repo_files,
            transitive = transitive,
        ),
    ]

_maven_library = rule(
    attrs = {
        "notice": attr.label(allow_single_file = True),
        "library": attr.label(providers = [JavaInfo], allow_single_file = True),
        "coordinates": attr.string(),
        "deps": attr.label_list(providers = [MavenInfo]),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_pom": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:pom_generator"),
            allow_files = True,
        ),
    },
    outputs = {
        "pom": "%{name}.pom",
    },
    fragments = ["java"],
    implementation = _maven_library_impl,
)

def maven_library(
        name,
        srcs,
        javacopts = [],
        resources = [],
        resource_strip_prefix = None,
        deps = [],
        runtime_deps = [],
        bundled_deps = [],
        friends = [],
        notice = None,
        coordinates = None,
        exclusions = None,
        lint_baseline = None,
        lint_classpath = [],
        lint_is_test_sources = False,
        lint_timeout = None,
        module_name = None,
        legacy_name = "",
        **kwargs):
    """Compiles a library jar from Java and Kotlin sources

    Args:
        srcs: The sources of the library.
        javacopts: Additional javac options.
        resources: Resources to add to the jar.
        resources_strip_prefix: The prefix to strip from the resources path.
        deps: The dependencies of this library.
        runtime_deps: The runtime dependencies.
        bundled_deps: The dependencies that are bundled inside the output jar and not treated as a maven dependency
        friends: The list of kotlin-friends.
        notice: An optional notice file to be included in the jar.
        coordinates: The maven coordinates of this artifact.
        exclusions: Files to exclude from the generated pom file.
        lint_*: Lint configuration arguments
        module_name: The kotlin module name.
    """
    if legacy_name:
        # Create legacy rules and make them point to the new rules.
        import_maven_library(legacy_name, name, deps = deps)

    kotlins = [src for src in srcs if src.endswith(".kt")]
    javas = [src for src in srcs if src.endswith(".java")]
    source_jars = [src for src in srcs if src.endswith(".srcjar")]
    final_javacopts = javacopts + ["--release", "8"]

    _kotlin_library(
        name = name + ".lib",
        jar = name + ".jar",
        java_srcs = javas,
        kotlin_srcs = kotlins,
        source_jars = source_jars,
        deps = deps,
        bundled_deps = bundled_deps,
        friends = friends,
        notice = notice,
        module_name = module_name,
        kotlin_use_ir = test_kotlin_use_ir(),
        javacopts = final_javacopts if javas else None,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        runtime_deps = runtime_deps,
        **kwargs
    )

    _maven_library(
        name = name,
        notice = notice,
        deps = deps,
        coordinates = coordinates,
        library = ":" + name + ".lib",
        **kwargs
    )

    if lint_baseline:
        # TODO: use srcs once the migration is completed
        lint_srcs = javas + kotlins
        if not lint_srcs:
            fail("lint_baseline set for rule that has no sources")

        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = deps + bundled_deps + lint_classpath,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
            is_test_sources = lint_is_test_sources,
            timeout = lint_timeout if lint_timeout else None,
        )
