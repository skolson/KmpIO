pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

val projectNameMavenName = "kmp-io"
rootProject.name = projectNameMavenName

include(":KmpIO")
// added this to solve a problem with the VannikTech publishing plugin (issue #23)
project( ":KmpIO" ).name = projectNameMavenName