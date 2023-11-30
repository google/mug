load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")


junit_tests(
    name = "AllTests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    deps = [
        "//mug:base",
        "//mug:format",
        "//mug-guava",
        "//mug-guava:safe_sql",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:com_google_truth_extensions_truth_java8_extension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:junit_junit",
    ],
)