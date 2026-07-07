pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val projectNameMavenName = "kmp-io"
rootProject.name = projectNameMavenName

include(":KmpIO")
// added this to solve a problem with the VannikTech publishing plugin (issue #23)
project( ":KmpIO" ).name = projectNameMavenName