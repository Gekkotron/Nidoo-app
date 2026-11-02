plugins {
    id("nidoo.android.library")
    id("nidoo.serialization")
}

android {
    namespace = "com.gekkotron.nidoo.push"
}

dependencies {
    api(project(":core:alerts"))
    api(project(":core:transport"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.androidx.work.testing)
    testImplementation(testFixtures(project(":core:transport")))
}
