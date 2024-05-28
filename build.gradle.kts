@file:Suppress("UnstableApiUsage")

plugins {
    `jacoco-report-aggregation`
    `java-library`
    `java-test-fixtures`
    jacoco

    alias(libs.plugins.git.version)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotless)

    id("compress4j.publishing")
}

group = "io.github.compress4j"
description = "A simple archiving and compression library for Java."

version = "0.0.0-SNAPSHOT"

gitVersioning.apply {
    refs {
        branch(".+") {
            version = "\${describe.tag.version.major}.\${describe.tag.version.minor}.\${describe.tag.version.patch.next}-\${commit.short}-SNAPSHOT"
        }
        tag("v(?<version>.*)") {
            version = "\${ref.version}"
        }
    }

    // optional fallback configuration in case of no matching ref configuration
    rev {
        version = "\${commit}"
    }
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

dependencies {
    api(libs.jakarta.annotation.api)
    api(libs.org.apache.commons.commons.compress)

    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))

    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.logback.classic)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.jupiter)

    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(platform(libs.junit.bom))

    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(platform(libs.junit.bom))

                implementation(libs.org.tukaani.xz)
                implementation(libs.assertj.core)
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.params)

                runtimeOnly(libs.junit.platform.launcher)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

 tasks.testCodeCoverageReport {
    dependsOn(tasks.test, tasks.named<Test>("integrationTest"))
     executionData(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
    reports {
        xml.required = true
        html.required = true
    }
    mustRunAfter(tasks.spotlessApply, tasks.javadoc)
}

tasks.check {
    dependsOn(tasks.testCodeCoverageReport)
}

sonar {
    properties {
        property("sonar.projectKey", "austek_compress4j")
        property("sonar.organization", "austek")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.exclusions",
            listOf(
                "docs/**/*",
                "**/*Exception.java"
            )
        )
    }
}

tasks.sonar {
    dependsOn(tasks.check)
}

spotless {
    ratchetFrom("origin/main")
    java {
        toggleOffOn()
        palantirJavaFormat("2.47.0").formatJavadoc(true)
        licenseHeaderFile(rootProject.file(".config/spotless/copyright.java.txt"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
