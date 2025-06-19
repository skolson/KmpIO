import java.util.Properties
import java.io.FileInputStream
import org.gradle.kotlin.dsl.signing
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
    }
    kotlin("native.cocoapods")
    id("maven-publish")
    id("signing")
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

val localProps = Properties().apply {
    load(FileInputStream(project.rootProject.file("local.properties")))
    project.extra["signing.keyId"] = get("signing.keyId")
    project.extra["signing.password"] = get("signing.password")
    project.extra["signing.secretKeyRingFile"] = get("signing.secretKeyRingFile")
}

val mavenArtifactId = "kmp-io"
val appleFrameworkName = "KmpIO"
val githubUri = "skolson/$appleFrameworkName"
val githubUrl = "https://github.com/$githubUri"

group = "io.github.skolson"
version = libs.versions.appVersion.get()

val iosMinSdk = "14"
val kmpPackageName = "com.oldguy.common.io"

val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
val javadocTaskName = "javadocJar"
val javaVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())

java {
    toolchain {
        languageVersion.set(javaVersion)
    }
}

val htmlJarTask = tasks.register<Jar>("htmlJar") {
    archiveClassifier.set("htmldoc")
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(project.layout.buildDirectory.dir("dokka/html"))
}

publishing {
    val server = localProps.getProperty("ossrhServer")
    repositories {
        maven {
            url = uri("https://$server/service/local/staging/deploy/maven2/")
            credentials {
                username = localProps.getProperty("ossrhUsername")
                password = localProps.getProperty("ossrhPassword")
            }
        }
    }
    publications.withType(MavenPublication::class) {
        artifactId = artifactId.replace(project.name, mavenArtifactId)
        if (name != "androidDebug" && name != "androidRelease") {
            artifact(htmlJarTask)
        }

        pom {
            name.set("$appleFrameworkName Kotlin Multiplatform Common File I/O")
            description.set("Library for simple Text, Binary, and Zip file I/O on supported 64 bit platforms; Android, IOS, Windows, Linux, MacOS")
            url.set(githubUrl)
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("oldguy")
                    name.set("Steve Olson")
                    email.set("skolson5903@gmail.com")
                }
            }
            scm {
                url.set(githubUrl)
                connection.set("scm:git:git://git@github.com:${githubUri}.git")
                developerConnection.set("cm:git:ssh://git@github.com:${githubUri}.git")
            }
        }
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

kotlin {
    jvmToolchain {
        languageVersion = javaVersion
    }
    androidTarget {
        publishLibraryVariants("release", "debug")
        mavenPublication {
            artifactId = artifactId.replace(project.name, mavenArtifactId)
            /*
                causes a signing task cross-dependency problem in Dokka v2
            if (name == "androidRelease")
                artifact(htmlJarTask)
             */
        }
    }

    cocoapods {
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
                freeCompilerArgs = freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs = freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs = freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
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
/*
        val androidMain by getting {
            dependencies {
                implementation()
            }
        }

 */
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

signing {
    isRequired = true
    sign(publishing.publications)
}