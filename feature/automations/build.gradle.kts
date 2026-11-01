plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
    id("nidoo.hilt")
}

android {
    namespace = "com.gekkotron.nidoo.feature.automations"
}

dependencies {
    implementation(project(":core:flows"))
    implementation(project(":core:source"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
