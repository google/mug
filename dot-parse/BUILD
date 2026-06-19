load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

java_library(
    name = "parse",
    srcs = glob(["src/main/java/com/google/common/labs/parse/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//mug:base",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        ":parse",
        "//mug:base",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:junit_junit",
    ],
)
