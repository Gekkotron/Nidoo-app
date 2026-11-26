// Energy in Compose Multiplatform (iOS spec §7.6): the live diagrams, sheets and editor run on both platforms with
// a Koin ViewModel. Strings are CMP resources; only the format locale has a platform half.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.energy"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:source"))
            implementation(project(":core:history"))
            implementation(project(":core:energy"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:data"))
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
    packageOfResClass = "com.gekkotron.nidoo.feature.energy.resources"
}
