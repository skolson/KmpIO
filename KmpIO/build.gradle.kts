import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform)
        alias(it.android.kmp.library)
        alias(it.kotlinx.atomicfu)
        alias(it.kotlin.cocoapods)
        alias(it.dokka.base)
        alias(it.maven.publish.vannik)
        // alias(it.android.jupiter) // this currently causes a sync error ClassCastException
    }
}


repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

val appleFrameworkName = "KmpIO"
val githubUri = "skolson/$appleFrameworkName"
val githubUrl = "https://github.com/$githubUri"

val publishDomain = "io.github.skolson"
val appVersion = libs.versions.appVersion.get()
group = publishDomain
version = appVersion

val iosMinSdk = "14"

/**
 * Unit tests using JUnit 6.x have desugaring issues, so this snippet sets android minimum sdk to
 * one value for maven publishing tasks (publishToMavenCentral and publishToMavenLocal) and a different
 * value fo all other Gradle tasks.
 */
val isPublishing = gradle.startParameter.taskNames.any { it.contains("publish") }
var androidMinSdk = if (isPublishing)
    libs.versions.androidSdkMinimum.get().toInt()
else
    libs.versions.androidSdkMinimumTest.get().toInt()
/*
 * For the publishing and signing tasks to work properly, insure the project settings for gradle have been
 * configured to set the Gradle User Home to /mnt/gradle where gradle.properties holds credential
 * information.

    For publishing to the central portal without release to maven, do this command:
    ./gradlew publishToMavenCentral --no-configuration-cache

    For publishing to the central portal and release to maven, do this command:
    ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache

    The --no-configuration-cache is required by https://github.com/gradle/gradle/issues/22779
 */
mavenPublishing {
    coordinates(publishDomain, name, appVersion)

    pom {
        name.set("Kotlin Multiplatform File I/O")
        description.set("Library for simple Text, Binary, and Zip file I/O on supported 64 bit platforms; Android, IOS, Windows, Linux, MacOS")
        url.set(githubUrl)
        inceptionYear.set("2020")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("oldguy")
                name.set("Steve Olson")
                email.set("skolson5903@gmail.com")
                url.set("https://github.com/skolson/")
            }
        }
        scm {
            url.set(githubUrl)
            connection.set("scm:git:git://git@github.com:${githubUri}.git")
            developerConnection.set("scm:git:ssh://git@github.com:${githubUri}.git")
        }
    }
}

val javaVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
java {
    toolchain {
        languageVersion.set(javaVersion)
    }
}

kotlin {
    android {
        compileSdk = libs.versions.androidSdk.get().toInt()
        buildToolsVersion = libs.versions.androidBuildTools.get()
        namespace = "com.oldguy.iocommon"

        minSdk = androidMinSdk
        println("androidMinSdk = $androidMinSdk")

        androidResources {
            enable = true
        }
        withHostTest {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test" // This provides androidDeviceTest access to commonTest
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments += mapOf(
                "runnerBuilder" to "de.mannodermaus.junit5.AndroidJUnit5Builder"
            )
            execution = "HOST"
        }

        packaging {
            resources.excludes.addAll(
                listOf(
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md"
                )
            )
        }

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("proguard-rules.pro"))
        }
    }

    cocoapods {
        name = appleFrameworkName
        ios.deploymentTarget = iosMinSdk
        summary = "Kotlin Multiplatform API for basic File I/O"
        homepage = githubUrl
        license = "Apache 2.0"
        authors = "Steven Olson"
        framework {
            baseName = appleFrameworkName
            isStatic = true
        }
        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    val appleXcf = XCFramework()
    listOf(
        macosX64(), macosArm64()
    ).forEach {
        it.binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
    }
    listOf(
        iosX64(), iosArm64(), iosSimulatorArm64()
    ).forEach {
        it.binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs =
                    freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    jvmToolchain {
        languageVersion = javaVersion
    }
    jvm()
    linuxArm64()
    linuxX64()

    // Turns off warnings about expect/actual class usage
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(libs.bundles.kotlin.test)
                implementation(libs.kotlinx.datetime)
            }
        }
        getByName("iosSimulatorArm64Test") {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        getByName("iosX64Test") {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.bundles.androidx.test)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.bundles.androidx.test)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.bundles.jvm.test)
            }
        }
        getByName("linuxX64Test") {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        getByName("linuxArm64Test") {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        all {
            languageSettings {
                optIn("kotlin.ExperimentalUnsignedTypes")
            }
        }
    }

}

dokka {
    moduleName.set("Kotlin Multiplatform Common IO Library")
    dokkaSourceSets.commonMain {
        includes.from("$appleFrameworkName.md")
    }
    dokkaPublications.html {
        includes.from("$appleFrameworkName.md")
    }
}

tasks.withType<Test> {
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
        showStackTraces = true
    }
}