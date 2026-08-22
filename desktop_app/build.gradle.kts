import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        getByName("desktopMain").dependencies {
            implementation(project(":shared_ui"))
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(compose.desktop.currentOs)
        }
    }
}

tasks.named<ProcessResources>("desktopProcessResources") {
    from(rootProject.file("fastlane/metadata/android/en-US/images/icon.png")) {
        rename { "harmonic-app-icon.png" }
    }
    from(project.file("icons/harmonic-macos.png")) {
        rename { "harmonic-app-icon-macos.png" }
    }
    from(project.file("icons/harmonic-windows.png")) {
        rename { "harmonic-app-icon-windows.png" }
    }
}

compose.desktop {
    application {
        mainClass = "com.simon.harmonichackernews.desktop.DesktopAppMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Harmonic"
            packageVersion = "1.0.0"
            description = "A desktop Hacker News client powered by Harmonic's shared Kotlin app"
            vendor = "Simon Halvdansson"
            macOS {
                iconFile.set(project.layout.projectDirectory.file("icons/harmonic.icns"))
                bundleID = "com.simon.harmonichackernews.desktop"
            }
            windows {
                iconFile.set(project.layout.projectDirectory.file("icons/harmonic.ico"))
            }
            linux {
                iconFile.set(
                    rootProject.layout.projectDirectory.file(
                        "fastlane/metadata/android/en-US/images/icon.png",
                    ),
                )
            }
        }
    }
}
