load("//tools/base/bazel/sdk:sdk_utils.bzl", "platform_filegroup", "sdk_glob", "sdk_path")

filegroup(
    name = "licenses",
    srcs = sdk_glob(
        include = ["licenses/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/latest-preview",
    srcs = [":build-tools/26.0.0"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "dxlib-preview",
    jars = sdk_path(["build-tools/26.0.0/lib/dx.jar"]),
)

java_binary(
    name = "dx-preview",
    main_class = "com.android.dx.command.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":dxlib-preview"],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/30.0.3"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/latest/aidl",
    srcs = sdk_glob(
        include = ["build-tools/30.0.3/aidl"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/30.0.3",
    srcs = sdk_glob(
        include = ["build-tools/30.0.3/**"],
    ),
    visibility = [
        "//tools/adt/idea/build-attribution:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
    ],
)

filegroup(
    name = "build-tools/30.0.2",
    srcs = glob(
        include = ["*/build-tools/30.0.2/**"],
    ),
    visibility = [
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
    ],
)

filegroup(
    name = "build-tools/29.0.2",
    srcs = sdk_glob(
        include = ["build-tools/29.0.2/**"],
    ),
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//tools/base/build-system/previous-versions:__pkg__",
    ],
)

filegroup(
    name = "build-tools/28.0.3",
    srcs = sdk_glob(
        include = ["build-tools/28.0.3/**"],
    ),
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
    ],
)

filegroup(
    name = "build-tools/minimum",
    srcs = [":build-tools/25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/28.0.0",
    srcs = sdk_glob(
        include = ["build-tools/28.0.0/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.3",
    srcs = sdk_glob(
        include = ["build-tools/27.0.3/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.1",
    srcs = sdk_glob(
        include = ["build-tools/27.0.1/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/27.0.0",
    srcs = sdk_glob(
        include = ["build-tools/27.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/26.0.2",
    srcs = sdk_glob(
        include = ["build-tools/26.0.2/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/26.0.0",
    srcs = sdk_glob(
        include = ["build-tools/26.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "build-tools/25.0.2",
    srcs = sdk_glob(
        include = ["build-tools/25.0.2/**"],
    ),
)

filegroup(
    name = "build-tools/25.0.0",
    srcs = sdk_glob(
        include = ["build-tools/25.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = sdk_glob(
        include = ["build-tools/24.0.3/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__pkg__",
    ],
)

filegroup(
    name = "platform-tools",
    srcs = sdk_glob(
        include = ["platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest_build_only",
    srcs = [":platforms/android-33_build_only"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-33"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest-preview",
    srcs = [":platforms/android-33"],  # Currently there isn't a preview available
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest/framework.aidl",
    srcs = sdk_glob(
        include = ["platforms/android-33/framework.aidl"],
    ),
    visibility = ["//visibility:public"],
)

# Use this target to compile against.
# Note: these stubbed classes will not be available at runtime.
java_import(
    name = "platforms/latest_jar",
    jars = sdk_path(["platforms/android-33/android.jar"]),
    neverlink = 1,
    visibility = [
        "//tools/adt/idea/emulator/screen-sharing-agent:__pkg__",
        "//tools/base/app-inspection/agent:__pkg__",
        "//tools/base/app-inspection/inspectors:__subpackages__",
        "//tools/base/deploy/agent/runtime:__pkg__",
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
        "//tools/base/experimental/live-sql-inspector:__pkg__",
        "//tools/base/profiler/app:__pkg__",
        "//tools/vendor/google/directaccess-client/reverse-daemon:__pkg__",
    ],
)

# Use this target for tests that need the presence of the android classes during test runs.
# Note: these are stubbed classes.
java_import(
    name = "platforms/latest_runtime_jar",
    testonly = 1,
    jars = sdk_path(["platforms/android-33/android.jar"]),
    visibility = [
        "//tools/base/app-inspection/inspectors:__subpackages__",
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-TiramisuPrivacySandbox",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/project-system-gradle:__subpackages__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-33",
    visibility = ["//visibility:public"],
)

# Version-specific rule public while tests transition to platform 32
platform_filegroup(
    name = "platforms/android-32",
    visibility = ["//visibility:public"],
)

# Version-specific rule public while tests transition to platform 32
platform_filegroup(
    name = "platforms/android-31",
    visibility = ["//visibility:public"],
)

platform_filegroup(
    name = "platforms/android-30",
    #visibility = ["//visibility:private"],
    visibility = [
        "//tools/adt/idea/debuggers:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-29",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-28",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/vendor/google/lldb-integration-tests:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-27",
    # TODO: Restrict the visibility of this group. Although the comment above says "private", the default
    # visibility is public.
)

platform_filegroup(
    name = "platforms/android-25",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/vendor/google/android-apk:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/gradle-core:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/data-binding:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-23",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-21",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

platform_filegroup(
    name = "platforms/android-19",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

filegroup(
    name = "emulator",
    srcs = sdk_glob(
        include = ["emulator/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-latest",
    srcs = ["add-ons/addon-google_apis-google-24"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = sdk_glob(["add-ons/addon-google_apis-google-24/**"]),
)

filegroup(
    name = "docs",
    srcs = sdk_glob(["docs/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk-bundle",
    srcs = sdk_glob(
        include = ["ndk-bundle/**"],
        exclude = [
            "ndk-bundle/platforms/android-19/**",
            "ndk-bundle/platforms/android-21/**",
        ],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk",
    srcs = sdk_glob(
        include = ["ndk/25.1.8937393/**"],
        exclude = [
            # Bazel can't handle paths with spaces in them.
            "ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/python3/lib/python3.9/site-packages/setuptools/command/launcher manifest.xml",
            "ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/python3/lib/python3.9/site-packages/setuptools/script (dev).tmpl",
            "ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/python3/lib/python3.9/site-packages/setuptools/command/launcher manifest.xml",
            "ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/python3/lib/python3.9/site-packages/setuptools/script (dev).tmpl",
        ],
    ),
    visibility = ["//visibility:public"],
)

# NDK r20b is used for AGP tests that require RenderScript support.
filegroup(
    name = "ndk-20",
    srcs = sdk_glob(
        include = ["ndk/20.1.5948944/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "cmake",
    srcs = sdk_glob(
        include = ["cmake/**"],
        exclude = ["cmake/**/Help/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sources",
    srcs = sdk_glob(["sources/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "instant-apps-sdk",
    srcs = sdk_glob(
        include = ["extras/google/instantapps/**"],
    ),
    visibility = ["//visibility:public"],
)
