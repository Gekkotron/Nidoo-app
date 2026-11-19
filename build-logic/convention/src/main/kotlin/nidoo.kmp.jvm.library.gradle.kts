// Kotlin Multiplatform library with a plain JVM target (no Android — this is for pure-data
// modules that must not pull in the Android SDK). Parallel to nidoo.jvm.library. Also compiles
// commonMain for iOS (iosArm64 for the phone, iosSimulatorArm64 for tests); jvmMain stays JVM-only.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)
    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        testRuns.named("test").configure {
            executionTask.configure { useJUnitPlatform() }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.findLibrary("junit-bom").get()))
            implementation(libs.findLibrary("junit-jupiter").get())
            implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            runtimeOnly(libs.findLibrary("junit-platform-launcher").get())
        }
    }
}
