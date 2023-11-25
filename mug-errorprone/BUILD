JDK_COMPILER_ADD_EXPORTS = [
    "jdk.compiler/com.sun.tools.javac.%s" % p
    for p in (
        "api",
        "code",
        "comp",
        "file",
        "main",
        "model",
        "parser",
        "processing",
        "resources",
        "tree",
        "util",
    )
]

java_library(
    name = "errorprone",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    add_exports = JDK_COMPILER_ADD_EXPORTS,
    deps = [
        "@maven//:com_google_errorprone_error_prone_core",
        "@maven//:com_google_errorprone_error_prone_annotation",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_errorprone_error_prone_check_api",
        "@maven//:com_google_errorprone_error_prone_docgen_processor",
        "@maven//:com_google_guava_guava",
        "//mug:base",
        "//mug-guava",
        "@maven//:com_google_auto_service_auto_service_annotations",
    ],
    exports = [
        "@maven//:com_google_errorprone_error_prone_core",
        "@maven//:com_google_auto_service_auto_service",
    ],
)

java_plugin(
    name = "plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    visibility = ["//visibility:public"],
    deps = [":errorprone"],
)

load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        ":errorprone",
        "//mug:format",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_errorprone_error_prone_test_helpers",
        "@maven//:junit_junit",
    ],
)
