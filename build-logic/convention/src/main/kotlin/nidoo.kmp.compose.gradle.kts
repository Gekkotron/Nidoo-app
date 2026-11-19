// Compose Multiplatform for a KMP module (apply after nidoo.kmp.library): shared UI in commonMain
// that runs on Android and iOS. Material3 is added when the design system moves to commonMain.
plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.named("commonMain").configure {
        dependencies {
            implementation(libs.findLibrary("cmp-runtime").get())
            implementation(libs.findLibrary("cmp-foundation").get())
            implementation(libs.findLibrary("cmp-ui").get())
        }
    }
}
