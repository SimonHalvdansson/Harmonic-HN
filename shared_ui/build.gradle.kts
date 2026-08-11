import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    iosArm64()
    iosSimulatorArm64()

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
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            implementation(libs.androidx.compose.material3)
        }
    }
}

compose.resources {
    packageOfResClass = "com.simon.harmonichackernews.shared.ui.resources"
}
