ANNOTATIONS_SRC = glob(["src/main/java/com/google/mu/annotations/*.java"])
SAFE_SQL_SRCS = glob(["src/main/java/com/google/mu/safesql/*.java"])

java_library(
    name = "annotations",
    srcs = ANNOTATIONS_SRC,
)

java_library(
    name = "mug-guava",
    srcs = glob(["src/main/java/**/*.java"], exclude = ANNOTATIONS_SRC + SAFE_SQL_SRCS),
    visibility = ["//visibility:public"],
    deps = [
        ":annotations",
        "//mug:base",
        "//mug:chain",
        "@@maven//:com_google_guava_guava",
        "@maven//:org_checkerframework_checker_qual",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "safe_sql",
    srcs = SAFE_SQL_SRCS,
    visibility = ["//visibility:public"],
    deps = [
        ":annotations",
        "//mug:base",
        "//mug:format",
        "@@maven//:com_google_guava_guava",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)


load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")


junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    resources = ["src/test/resources/com/google/mu/safesql/schema.sql"],
    deps = [
        ":mug-guava",
        ":safe_sql",
        "//mug:base",
        "//mug:chain",
        "//mug:format",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:com_h2database_h2",
        "@maven//:org_dbunit_dbunit",
        "@maven//:org_mockito_mockito_core",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:junit_junit",
    ],
)
