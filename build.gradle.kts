import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Target Rider — see squill issue #57. Rider isn't distributed as an installer,
        // so it must be resolved from the multi-OS archive. See
        // intellij-platform-gradle-plugin#1852.
        rider(providers.gradleProperty("platformVersion")) {
            useInstaller = false
        }

        // Bundled in Rider — provides the SQL dialects + per-file dialect mapping the plugin
        // uses to apply a provider-specific dialect to a project's .sql files.
        bundledPlugin("com.intellij.database")

        // The multi-OS archive doesn't bundle the JetBrains Runtime, so add it
        // explicitly — needed to launch the IDE via runIde and for platform tests.
        jetbrainsRuntime()

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    // Pin plugin verification to the single target Rider version. The default (recommended())
    // resolves several IDE builds, each a multi-GB download; one build keeps CI affordable.
    pluginVerification {
        ides {
            create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersion"))
        }
    }
}

// --- Fast unit tests (no IntelliJ Platform) -------------------------------------------------
//
// Pure-logic tests (provider-name parsing, enum mappings) live in src/unitTest and run against
// the compiled main output + JUnit only — no Rider on the classpath. This lets CI run them in a
// job that never downloads the multi-GB Rider archive. Platform-touching tests stay in src/test.
val unitTest: SourceSet by sourceSets.creating

configurations["unitTestImplementation"].extendsFrom(configurations["testImplementation"])

dependencies {
    "unitTestImplementation"(sourceSets["main"].output)
    // stdlib isn't added automatically (kotlin.stdlib.default.dependency=false) and the platform
    // deps that normally supply it aren't on this source set's classpath.
    "unitTestImplementation"(kotlin("stdlib"))
}

val unitTestTask = tasks.register<Test>("unitTest") {
    description = "Runs pure-logic unit tests without the IntelliJ Platform."
    group = "verification"
    testClassesDirs = unitTest.output.classesDirs
    classpath = unitTest.runtimeClasspath
}

tasks.named("check") {
    dependsOn(unitTestTask)
}
