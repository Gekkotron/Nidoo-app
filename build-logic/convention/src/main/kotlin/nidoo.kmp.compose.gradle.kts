// Compose Multiplatform for a KMP module (apply after nidoo.kmp.library): shared UI in commonMain
// that runs on Android and iOS, with Material3 and CMP resources (strings, fonts) in composeResources/.
plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    // CMP resources need Android resources on AGP 9's KMP library plugin.
    targets.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>().configureEach {
        androidResources.enable = true
    }
    sourceSets.named("commonMain").configure {
        dependencies {
            implementation(libs.findLibrary("cmp-runtime").get())
            implementation(libs.findLibrary("cmp-foundation").get())
            implementation(libs.findLibrary("cmp-ui").get())
            implementation(libs.findLibrary("cmp-material3").get())
            implementation(libs.findLibrary("cmp-components-resources").get())
        }
    }
}
