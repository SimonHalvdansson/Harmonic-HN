import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.simon.harmonichackernews.shared.resources"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.multiplatform.resources)
            api(libs.compose.multiplatform.ui)
            api(libs.kmpalette.core)
            implementation(libs.compose.multiplatform.runtime)
        }
    }
}

compose.resources {
    packageOfResClass = "com.simon.harmonichackernews.resources"
    publicResClass = true
}
