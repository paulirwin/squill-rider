import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "squill-rider"

pluginManagement {
    plugins {
        // Keep in step with the Kotlin the target Rider platform is built with (Rider 2026.1
        // ships Kotlin 2.3.x); an older compiler rejects its metadata.
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()

        // IntelliJ Platform Gradle Plugin Repositories Extension
        // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
