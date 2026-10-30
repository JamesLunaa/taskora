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
    // Only the core icons; the few others the app uses are copied into ui/icons.
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.components.resources)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.datastore.preferences.core)
    implementation(libs.reorderable)
    implementation(libs.jna.platform)
    // The Linux tray (StatusNotifierItem) talks D-Bus over the JDK's own Unix sockets.
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.unixsocket)
    // dbus-java logs through SLF4J, which warns at every start without a backend.
    runtimeOnly(libs.slf4j.nop)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
}

compose.resources {
    packageOfResClass = "io.github.jameslunaa.taskora.resources"
}

// jpackage only builds packages for the OS it runs on: MSI on Windows, the Linux app image elsewhere.
val windowsHost = System.getProperty("os.name").startsWith("Windows")

val toolchainJavaHome: File = javaToolchains
    .launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    .get().metadata.installationPath.asFile

compose.desktop {
    application {
        mainClass = "io.github.jameslunaa.taskora.MainKt"
        // The bundled runtime comes from the build's JDK, so the launcher fix below can copy its `java`.
        javaHome = toolchainJavaHome.absolutePath
        // Lets Main set the X11 window class, so desktops match windows to the .desktop entry.
        // Windows' JDK has no sun.awt.X11 and would warn about it.
        if (!windowsHost) jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // Linux: packaging/build-packages.sh turns the app image from createDistributable into the
        // AppImage, .deb and .rpm; packaging/arch/PKGBUILD does the same for Arch.
        // Windows: packageMsi builds the installer (Compose downloads WiX for it).
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi)
            packageName = if (windowsHost) "Taskora" else "taskora"
            packageVersion = version.toString()
            description = "Offline to-do and task lists"
            vendor = "Jose Cynon Luna"
            copyright = "Copyright 2026 Jose Cynon Luna. Licensed under the Apache License, Version 2.0."
            licenseFile.set(rootProject.file("../LICENSE"))
            // jdk.security.auth and jdk.net: dbus-java (the Linux tray) authenticates with the user id and uses Unix socket options.
            modules("java.instrument", "jdk.unsupported", "jdk.security.auth", "jdk.net")
            linux {
                iconFile.set(project.file("packaging/icons/256x256/io.github.jameslunaa.taskora.png"))
            }
            windows {
                iconFile.set(project.file("packaging/windows/taskora.ico"))
                // A per-user install needs no admin rights and matches the per-user data folder.
                perUserInstall = true
                dirChooser = true
                menu = true
                menuGroup = "Taskora"
                shortcut = true
                // Never change it: it's how a new MSI finds and replaces the installed version.
                upgradeUuid = "75c3dbee-8680-40a7-b281-d8c09aba240e"
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
// Windows keeps jpackage's launcher (Taskora.exe).
tasks.matching { it.name == "createDistributable" && !windowsHost }.configureEach {
    inputs.file(launcherScript)
    doLast {
        val image = appImageDir.get().asFile
        toolchainJava.copyTo(File(image, "lib/runtime/bin/java"), overwrite = true).setExecutable(true, false)
        launcherScript.copyTo(File(image, "bin/taskora"), overwrite = true).setExecutable(true, false)
        File(image, "lib/libapplauncher.so").delete()
    }
}
