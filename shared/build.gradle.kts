import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.shared"
    }
    // The iOS app links this module as the static `Nidoo` framework.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Nidoo"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }
    }
}
