import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simon.harmonichackernews.core"
        compileSdk = 37
        minSdk = 26

        withHostTest {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.ksoup)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.jna.platform)
        }
    }
}
