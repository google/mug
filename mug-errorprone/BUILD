java_library(
    name = "errorprone",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        ":auto_service",
        ":error_prone_docgen",
        "@maven//:com_google_auto_service_auto_service_annotations",
        "@maven//:com_google_errorprone_error_prone_annotation",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_errorprone_error_prone_check_api",
        "@maven//:com_google_guava_guava",
        "//mug:base",
        "//mug:time_lib",
        "//mug-guava",
    ],
)

java_plugin(
    name = "plugin",
    visibility = ["//visibility:public"],
    deps = [
        ":errorprone",
    ],
)

java_library(
    name = "auto_service",
    exported_plugins = [
        ":auto_service_plugin",
    ],
    exports = [
        "@maven//:com_google_auto_service_auto_service_annotations",
    ],
)

java_plugin(
    name = "auto_service_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    deps = [
        "@maven//:com_google_auto_service_auto_service",
    ],
)

java_library(
    name = "error_prone_docgen",
    exported_plugins = [
        ":error_prone_docgen_plugin",
    ],
    exports = [
        "@maven//:com_google_auto_service_auto_service_annotations",
    ],
)

java_plugin(
    name = "error_prone_docgen_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    deps = [
        "@maven//:com_google_errorprone_error_prone_docgen_processor",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        ":errorprone",
        "//mug:format",
        "//mug:time",
        "//mug-guava:safe_sql",
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
