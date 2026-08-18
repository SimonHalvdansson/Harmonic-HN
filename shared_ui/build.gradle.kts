import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.simon.harmonichackernews.shared.ui"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm("desktop")

    val harmonicXcFramework = XCFramework("HarmonicShared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HarmonicShared"
            isStatic = true
            export(project(":shared_logic"))
            export(project(":shared_resources"))
            transitiveExport = true
            harmonicXcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared_logic"))
            api(project(":shared_resources"))
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.animation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.ksoup)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.androidx.material3.adaptive.navigation3)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.compose.material3)
        }
    }
}

compose.resources {
    packageOfResClass = "com.simon.harmonichackernews.shared.ui.resources"
}
