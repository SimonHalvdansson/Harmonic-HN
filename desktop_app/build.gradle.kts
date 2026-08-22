import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.WriteProperties
import org.gradle.language.jvm.tasks.ProcessResources

val desktopVersionName = providers.gradleProperty("harmonic.desktop.versionName").get()
val desktopVersionCode = providers.gradleProperty("harmonic.desktop.versionCode").get()

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
            implementation(libs.jna.platform)
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

val generateDesktopMetadata = tasks.register<WriteProperties>("generateDesktopMetadata") {
    destinationFile = layout.buildDirectory
        .file("generated/desktopMetadata/harmonic-desktop.properties")
        .get()
        .asFile
    property("versionName", desktopVersionName)
    property("versionCode", desktopVersionCode)
}

tasks.named<ProcessResources>("desktopProcessResources") {
    from(generateDesktopMetadata)
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

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        systemProperty("harmonic.desktop.debug", "true")
    }
}

compose.desktop {
    application {
        mainClass = "com.simon.harmonichackernews.desktop.DesktopAppMainKt"
        // Compose 1.12.0-rc01's ProGuard runner dereferences Gradle 9.7's nullable output stream.
        // Keep release packaging functional until the plugin is compatible; packaging still uses
        // the release runtime and excludes debug-only UI through runtime metadata below.
        buildTypes.release.proguard.isEnabled.set(false)
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Harmonic"
            packageVersion = desktopVersionName
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
