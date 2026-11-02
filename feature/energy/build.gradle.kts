plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
    id("nidoo.hilt")
}

android {
    namespace = "com.gekkotron.nidoo.feature.energy"
}

dependencies {
    implementation(project(":core:source"))
    implementation(project(":core:history"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
