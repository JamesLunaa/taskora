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
    implementation(libs.datastore.preferences.core)
    implementation(libs.reorderable)
    implementation(libs.jna.platform)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
}

compose.resources {
    packageOfResClass = "io.github.jameslunaa.taskora.resources"
}

val toolchainJavaHome: File = javaToolchains
    .launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    .get().metadata.installationPath.asFile

compose.desktop {
    application {
        mainClass = "io.github.jameslunaa.taskora.MainKt"
        // The bundled runtime comes from the build's JDK, so the launcher fix below can copy its `java`.
        javaHome = toolchainJavaHome.absolutePath
        // Lets Main set the X11 window class, so desktops match windows to the .desktop entry.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // packaging/build-packages.sh turns the app image from createDistributable into the
        // AppImage, .deb and .rpm; packaging/arch/PKGBUILD does the same for Arch.
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "taskora"
            packageVersion = version.toString()
            description = "Offline to-do and task lists"
            vendor = "Jose Cynon Luna"
            copyright = "Copyright 2026 Jose Cynon Luna. Licensed under the Apache License, Version 2.0."
            licenseFile.set(rootProject.file("../LICENSE"))
            modules("java.instrument", "jdk.unsupported")
            linux {
                iconFile.set(project.file("packaging/icons/256x256/io.github.jameslunaa.taskora.png"))
            }
        }
    }
}

// jpackage's native launcher forks a helper that aborts while exiting ("pure virtual method
// called"), leaving a core dump on every start (seen with JDK 21.0.12). The app image starts
// through packaging/taskora-launcher.sh on the runtime's own `java` instead, which jpackage
// leaves out of the runtime.
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/taskora")
val launcherScript = file("packaging/taskora-launcher.sh")
val toolchainJava = File(toolchainJavaHome, "bin/java")
// Compose registers its tasks after this script runs, hence matching rather than named.
tasks.matching { it.name == "createDistributable" }.configureEach {
    inputs.file(launcherScript)
    doLast {
        val image = appImageDir.get().asFile
        toolchainJava.copyTo(File(image, "lib/runtime/bin/java"), overwrite = true).setExecutable(true, false)
        launcherScript.copyTo(File(image, "bin/taskora"), overwrite = true).setExecutable(true, false)
        File(image, "lib/libapplauncher.so").delete()
    }
}
