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
        rider("2026.1") {
            useInstaller = false
        }

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
}
