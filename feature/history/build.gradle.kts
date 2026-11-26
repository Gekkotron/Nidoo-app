// History in Compose Multiplatform (iOS spec §7.6): the chart screen and its Koin ViewModel run on both platforms.
// Strings are CMP resources; dates and numbers go through :core:format.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.history"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:source"))
            implementation(project(":core:history"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:format"))
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
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
        }
    }
}

compose.resources {
    packageOfResClass = "com.gekkotron.nidoo.feature.history.resources"
}
