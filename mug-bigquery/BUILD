load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

java_library(
  name = "template",
  srcs = glob(["src/main/java/**/*.java"]),
  deps = [
    "//mug:base",
    "//mug:format",
    "@maven//:com_google_errorprone_error_prone_annotations",
    "@maven//:com_google_cloud_google_cloud_bigquery",
  ]
)

junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        ":template",
        "//mug:base",
        "//mug:format",
        "//mug-guava",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_truth_truth",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_cloud_google_cloud_bigquery",
        "@maven//:junit_junit",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
    ],
)