plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
    id("nidoo.hilt")
}

android {
    namespace = "com.gekkotron.nidoo.feature.alerts"
}

dependencies {
    implementation(project(":core:alerts"))
    implementation(project(":core:push"))
    implementation(project(":core:source"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
