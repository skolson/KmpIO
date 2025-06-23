buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform) apply false
        alias(it.android.library) apply false
        alias(it.kotlinx.atomicfu) apply false
        alias(it.maven.publish.vannik) apply false
    }
}
