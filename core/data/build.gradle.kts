plugins {
    id("nidoo.android.library")
    id("nidoo.serialization")
}

android {
    namespace = "com.gekkotron.nidoo.data"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(kotlin("test"))
}
