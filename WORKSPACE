
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "4.3"
RULES_JVM_EXTERNAL_SHA = "6274687f6fc5783b589f56a2f1ed60de3ce1f99bc4e8f9edef3de43bdf7c6e74"

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

# Re-usable building blocks for Bazel build tool
# https://gerrit.googlesource.com/bazlets/
# https://gerrit.googlesource.com/bazlets/+/968b97fa03a9d2afd760f2e8ede3d5643da390d2
git_repository(
    name = "com_googlesource_gerrit_bazlets",
    remote = "https://gerrit.googlesource.com/bazlets",
    commit = "968b97fa03a9d2afd760f2e8ede3d5643da390d2",
)
# We cannot use the tar.gz provided over HTTP because it contains timestamps and each download has a
# different hash.
#http_archive(
#    name = "com_googlesource_gerrit_bazlets",
#    sha256 = "...",
#    urls = [
#        "https://gerrit.googlesource.com/bazlets/+archive/968b97fa03a9d2afd760f2e8ede3d5643da390d2.tar.gz",
#    ],
#)
# This provides these useful imports:
# load("@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl", "maven_jar")
# load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")


http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.google.errorprone:error_prone_annotation:2.23.0",
        "com.google.errorprone:error_prone_core:2.23.0",
        "com.google.errorprone:error_prone_annotations:2.23.0",
        "com.google.errorprone:error_prone_check_api:2.23.0",
        "com.google.errorprone:error_prone_docgen_processor:2.23.0",
        "com.google.errorprone:error_prone_test_helpers:2.23.0",
        "com.google.guava:guava:33.4.0-jre",
        "com.google.guava:guava-testlib:33.4.0-jre",
        "org.mockito:mockito-core:2.27.0",
        "com.google.testparameterinjector:test-parameter-injector:1.8",
        "junit:junit:4.13.1",
        "org.dbunit:dbunit:2.7.0",
        "org.junit.jupiter:junit-jupiter-api:5.0.0-M3",
        "com.google.truth:truth:1.1.5",
        "com.google.truth.extensions:truth-java8-extension:1.1.5",
        "org.checkerframework:checker-qual:3.12.0",
        "com.google.auto.service:auto-service-annotations:1.1.1",
        "com.google.auto.service:auto-service:1.1.1",
        "com.google.protobuf:protobuf-java:4.31.1",
        "com.google.protobuf:protobuf-java-util:4.31.1",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.cloud:google-cloud-bigquery:2.34.2",
        "com.h2database:h2:2.3.232",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)
