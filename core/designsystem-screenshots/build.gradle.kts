// Android-only home of the design system's Compose Preview screenshot tests: AGP's screenshot test suites
// don't run in a Kotlin Multiplatform library module, and :core:designsystem is one (iOS spec §7.4).
plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
}

android {
    namespace = "com.gekkotron.nidoo.designsystem.screenshots"

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
    implementation(project(":core:designsystem"))
}

// Compose Preview packages resources through the host-test APK, so every variant needs unit tests.
androidComponents {
    beforeVariants { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
