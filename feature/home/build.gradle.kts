// Home in Compose Multiplatform (iOS spec §7.6): the cards, sheets, dashboards and rooms run on both platforms with
// Koin ViewModels. Strings are CMP resources; only the format locale has a platform half.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.home"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:source"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:history"))
            implementation(project(":core:energy"))
            implementation(project(":core:format"))
            implementation(project(":core:dashboards"))
            implementation(project(":core:flows"))
            implementation(project(":core:climate"))
            implementation(project(":core:transport"))
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.cmp.ui.backhandler) // BackHandler closes the search
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose) // media player covers
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":core:transport-testing"))
            implementation(project(":core:connection")) // HomeModuleTest's EntitySourceSession
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
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
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
            implementation(project(":core:transport-testing"))
        }
    }
}

compose.resources {
    packageOfResClass = "com.gekkotron.nidoo.feature.home.resources"
}
