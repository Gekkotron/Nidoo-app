// Kotlin Multiplatform library with an Android target, built on AGP 9's dedicated
// `com.android.kotlin.multiplatform.library` plugin (the old `com.android.library` +
// `kotlin.multiplatform` pairing was removed in AGP 9.0). commonMain + androidMain + iosMain
// (iosArm64 for the phone, iosSimulatorArm64 for tests). commonTest also runs on the JVM as the
// Android host test.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    jvmToolchain(17)
    android {
        compileSdk = 36
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
