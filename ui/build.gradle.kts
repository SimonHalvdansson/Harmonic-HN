import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simon.harmonichackernews.ui"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26

        withHostTest {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm("desktop")

    val harmonicXcFramework = XCFramework("HarmonicKit")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HarmonicKit"
            isStatic = true
            export(project.dependencies.project(":core"))
            export(project.dependencies.project(":resources"))
            // Keep public navigation types at the same versions in the Swift export graph.
            export(libs.androidx.navigationevent)
            export(libs.androidx.navigationevent.compose)
            transitiveExport = true
            harmonicXcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.savedstate)
            implementation(libs.androidx.savedstate.compose)
            implementation(libs.compose.multiplatform.lifecycle.viewmodel)
            implementation(libs.compose.multiplatform.lifecycle.viewmodel.compose)
            implementation(libs.compose.multiplatform.lifecycle.viewmodel.savedstate)
            implementation(libs.compose.multiplatform.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.collection)
            api(project(":core"))
            api(project(":resources"))
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.lifecycle.runtime.compose)
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
            implementation(libs.haze.glass)
            implementation(libs.ksoup)
            implementation(libs.latex.renderer)
            implementation(libs.latex.fonts.katex)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.androidx.material3.adaptive.navigation3)
            api(libs.androidx.navigationevent)
            api(libs.androidx.navigationevent.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.compose.material3)
        }
    }
}

compose.resources {
    packageOfResClass = "com.simon.harmonichackernews.ui.resources"
}
