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
            // Swift implements MqttClientPort (CocoaMQTT, spec §9), so the framework exposes :core:transport.
            export(project(":core:transport"))
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            api(project(":core:transport"))
            implementation(project(":core:demo"))
            implementation(project(":core:source"))
            implementation(project(":core:designsystem"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.cmp.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
