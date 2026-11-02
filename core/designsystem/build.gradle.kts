plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
}

android {
    namespace = "com.gekkotron.nidoo.designsystem"

    testOptions {
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.get()
            targetVariants.add("debug")
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
        }
    }
}

dependencies {
    // LinkState for the shared connection pill.
    implementation(project(":core:transport"))
    // The alarm UI takes AlarmState, Capabilities and Command (security spec §4.1).
    api(project(":core:model"))
    implementation(libs.androidx.activity.compose) // the permission launcher
    implementation(libs.androidx.core.ktx)
    implementation(libs.vico.compose.m3)
}

// Compose Preview packages resources through the host-test APK, so every variant needs unit tests.
androidComponents {
    beforeVariants { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
