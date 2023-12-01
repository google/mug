java_library(
    name = "base",
    visibility = ["//visibility:public"],
    srcs = glob([
        "src/main/java/com/google/mu/util/*.java",
        "src/main/java/com/google/mu/util/stream/*.java",
        "src/main/java/com/google/mu/function/*.java",
      ],
      exclude = ["src/main/java/com/google/mu/util/StringFormat.java"]),
)

java_library(
    name = "graph",
    visibility = ["//visibility:public"],
    srcs = glob([ "src/main/java/com/google/mu/util/graph/*.java"]),
    deps = [":base"]
)

java_library(
    name = "concurrent",
    visibility = ["//visibility:public"],
    srcs = glob([ "src/main/java/com/google/mu/util/concurrent/*.java"]),
    deps = [":base"]
)

java_library(
    name = "format",
    visibility = ["//visibility:public"],
    srcs = ["src/main/java/com/google/mu/util/StringFormat.java"],
    deps = [":base"],
    exported_plugins = ["//mug-errorprone:plugin"],
)

load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

java_library(
    name = "tests",
    srcs = glob(["src/test/java/**/*.java"], exclude =  glob(["src/test/java/**/*Test.java"])),
    deps = [
        ":base",
        ":concurrent",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:junit_junit",
    ],
)


junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        ":base",
        ":concurrent",
        ":format",
        ":graph",
        ":tests",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:junit_junit",
    ],
)