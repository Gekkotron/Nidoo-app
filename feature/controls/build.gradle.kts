plugins {
    id("nidoo.android.library")
}

android {
    namespace = "com.gekkotron.nidoo.feature.controls"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":feature:widget"))
    implementation(libs.kotlinx.coroutines.core)
}
