// The design system in Compose Multiplatform (iOS spec §7.4): commonMain runs on Android and iOS; the Vico charts,
// the notification permission enabler, rememberNow and the icon preview stay in androidMain. The screenshot tests
// live in :core:designsystem-screenshots.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.designsystem"
    }
    sourceSets {
        commonMain.dependencies {
            // The alarm UI takes AlarmState, Capabilities and Command (security spec §4.1).
            api(project(":core:model"))
            // LinkState for the shared connection pill.
            implementation(project(":core:transport"))
            // Res.string.* / stringResource are part of this module's API (feature modules read Res.string.energy_*).
            api(libs.cmp.components.resources)
            api(libs.cmp.material3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose) // the permission launcher
            implementation(libs.androidx.core.ktx)
            implementation(libs.vico.compose.m3)
            implementation(libs.cmp.ui.tooling.preview)
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
    publicResClass = true
    packageOfResClass = "com.gekkotron.nidoo.designsystem.resources"
}
