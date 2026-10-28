import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

group = "io.github.jameslunaa.taskora"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.components.resources)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
}

compose.resources {
    packageOfResClass = "io.github.jameslunaa.taskora.resources"
}

compose.desktop {
    application {
        mainClass = "io.github.jameslunaa.taskora.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "taskora"
            packageVersion = version.toString()
            description = "Offline to-do and task lists"
            vendor = "Jose Cynon Luna"
            licenseFile.set(rootProject.file("../LICENSE"))
        }
    }
}
