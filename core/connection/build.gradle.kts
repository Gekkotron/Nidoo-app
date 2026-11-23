// The connection wiring both apps share (iOS spec §7.2): the discovery session, the transport factory with the
// platform transports injected, the profile's transport specs and the onboarding connection test.
plugins {
    id("nidoo.kmp.library")
}

kotlin {
    android {
        namespace = "com.gekkotron.nidoo.connection"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:transport"))
            api(project(":core:data"))
            api(project(":core:source"))
            implementation(project(":core:flows"))
            implementation(project(":core:dashboards"))
            implementation(project(":core:climate"))
            implementation(project(":core:demo"))
            implementation(libs.atomicfu)
        }
        commonTest.dependencies {
            implementation(project(":core:transport-testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(project(":core:transport-testing"))
            implementation(libs.junit4)
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
