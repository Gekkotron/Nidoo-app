// Voice in Compose Multiplatform (iOS spec §7.5): the session, ViewModel and sheet run on both platforms with a Koin
// ViewModel. Strings are CMP resources. The recognizer, the player and VoiceActivity stay in androidMain.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.voice"
        // The voice sheet's translucent window theme (res/values/themes.xml) is an Android resource.
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:assist"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.ext.junit)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.turbine)
        }
    }
}

compose.resources {
    packageOfResClass = "com.gekkotron.nidoo.feature.voice.resources"
}
