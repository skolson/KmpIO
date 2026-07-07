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
        alias(it.android.kmp.library) apply false
        alias(it.kotlinx.atomicfu) apply false
        alias(it.maven.publish.vannik) apply false
        alias(it.kotlin.cocoapods) apply false
        alias(it.dokka.base) apply false
        alias(it.android.jupiter) apply false
    }
}
