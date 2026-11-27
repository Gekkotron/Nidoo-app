// Settings in Compose Multiplatform (iOS spec §7.6): the list, pages and rooms run on both platforms with Koin
// ViewModels. Push and presence are Android-only and live in androidMain behind PlatformSettings; Voice is common.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.settings"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:source"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:transport"))
            implementation(project(":core:model"))
            implementation(project(":feature:voice"))
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.reorderable)
            implementation(libs.kotlinx.serialization.json) // the licences report
        }
        androidMain.dependencies {
            implementation(project(":core:push"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":core:transport-testing"))
            implementation(project(":core:connection")) // EntitySourceSession, for a source without a hub
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
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.gekkotron.nidoo.feature.settings.resources"
}
