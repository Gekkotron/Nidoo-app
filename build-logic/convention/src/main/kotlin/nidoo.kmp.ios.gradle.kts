// iOS targets for a KMP module (applied by nidoo.kmp.library and nidoo.kmp.jvm.library):
// iosArm64 for the phone, iosSimulatorArm64 for tests. No iosX64 (Apple Silicon only).
//
// iOS tests run on the Simulator, which `check` would otherwise boot on a developer's Mac.
// They only run on CI (the CI variable is set) or when asked for with -Pnidoo.iosTests.
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

extensions.configure<KotlinMultiplatformExtension> {
    iosArm64()
    iosSimulatorArm64()
}

val runIosTests = providers.environmentVariable("CI").isPresent ||
    providers.gradleProperty("nidoo.iosTests").isPresent

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    // A local copy: the configuration cache can't serialize a reference to this script.
    val run = runIosTests
    onlyIf("iOS Simulator tests run on CI or with -Pnidoo.iosTests") { run }
}
