// Onboarding in Compose Multiplatform (iOS spec §7.5): the flow, the forms and the connection test run on both
// platforms. The QR camera, the camera permission and the notification step have platform halves.
plugins {
    id("nidoo.kmp.library")
    id("nidoo.kmp.compose")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.feature.onboarding"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            // OnboardingViewModel's constructor takes a ConnectionTester.
            api(project(":core:connection"))
            implementation(project(":core:designsystem"))
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.cmp.ui.backhandler)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.compose)
            implementation(libs.zxing.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
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
    packageOfResClass = "com.gekkotron.nidoo.feature.onboarding.resources"
}
