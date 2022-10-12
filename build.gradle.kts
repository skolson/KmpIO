import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val kotlinVersion: String by extra("1.7.20")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:7.3.0")
        // The following line works around an atomicfu gradle plugin issue (github atomicfu issue 248)
        classpath("org.jetbrains.kotlin:atomicfu:$kotlinVersion")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.18.4")
    }
}

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}
