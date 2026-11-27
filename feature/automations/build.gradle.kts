// Automations in Compose Multiplatform (iOS spec §7.6): the Flows list, the Wake-up alarms and their editor run on
// both platforms with a Koin ViewModel. Strings are CMP resources; only the time picker's 12/24-hour clock is split.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.automations"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:flows"))
            implementation(project(":core:source"))
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
    packageOfResClass = "com.gekkotron.nidoo.feature.automations.resources"
}
