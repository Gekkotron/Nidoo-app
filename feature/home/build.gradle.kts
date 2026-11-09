plugins {
    id("nidoo.android.library")
    id("nidoo.android.compose")
    id("nidoo.hilt")
}

android {
    namespace = "com.gekkotron.nidoo.feature.home"
}

dependencies {
    implementation(project(":core:source"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:history"))
    implementation(project(":feature:energy"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose) // BackHandler closes the search
    implementation(libs.coil.compose) // media player covers
    testImplementation(testFixtures(project(":core:transport")))
}
