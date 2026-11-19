// Kotlin Multiplatform library with an Android target, built on AGP 9's dedicated
// `com.android.kotlin.multiplatform.library` plugin (the old `com.android.library` +
// `kotlin.multiplatform` pairing was removed in AGP 9.0). commonMain + androidMain + iosMain
// (see nidoo.kmp.ios). commonTest also runs on the JVM as the Android host test.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
    id("nidoo.kmp.ios")
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
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
