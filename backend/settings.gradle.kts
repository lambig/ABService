rootProject.name = "abservice-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "1.9.20"
        kotlin("plugin.allopen") version "1.9.20"
        id("io.quarkus") version "3.6.0"
    }
}
