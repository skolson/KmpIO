import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform)
        alias(it.android.library)
        alias(it.kotlinx.atomicfu)
        alias(it.dokka.base)
        alias(it.maven.publish.vannik)
        kotlin("native.cocoapods")
    }
}


repositories {
    gradlePluginPortal()
    google()
    mavenCentral{
        /**
         * These may be usable in the Vanniktech publish plugin in the future
        credentials {
            username = project.extra["mavenCentralUsername"]
            password = project.extra["mavenCentralPassword"]
        }
        */
    }
}

val appleFrameworkName = "KmpIO"
val githubUri = "skolson/$appleFrameworkName"
val githubUrl = "https://github.com/$githubUri"

val publishDomain = "io.github.skolson"
val appVersion = libs.versions.appVersion.get()
group = publishDomain
version = appVersion

val iosMinSdk = "14"

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
    configure(
        KotlinMultiplatform(
            JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            true,
            listOf("debug", "release")
        )
    )

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
android {
    compileSdk = libs.versions.androidSdk.get().toInt()
    buildToolsVersion = libs.versions.androidBuildTools.get()
    namespace = "com.oldguy.iocommon"

    defaultConfig {
        minSdk = libs.versions.androidSdkMinimum.get().toInt()

        buildFeatures {
            buildConfig = false
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        testImplementation(libs.junit)
        androidTestImplementation(libs.bundles.androidx.test)
    }
}

/*
Only run apple targets on macos, so only those publish and do not overlap with linux
builds. Linux does JVM, linux native, and android targets.
 */
kotlin {
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
    macosX64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
    }
    macosArm64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
    }
    iosX64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs =
                    freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs =
                    freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
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
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
    jvm()
    linuxX64() {
        compilerOptions {
            freeCompilerArgs.add("-g")
        }
        binaries {
            executable {
                debuggable = true
            }
        }
    }
    linuxArm64()

    // Turns off warnings about expect/actual class usage
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        val iosSimulatorArm64Test by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        val iosX64Test by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
        val linuxX64Test by getting {
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