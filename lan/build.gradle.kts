import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The LAN protocol (docs/lan.md), shared by the Android app and the desktop app: both builds
// include this one with includeBuild and depend on "io.github.jameslunaa.taskora:lan".
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.jameslunaa.taskora"
version = "1"

// Java 17 bytecode and APIs, which the Android app (minSdk 26, desugared) can use.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
