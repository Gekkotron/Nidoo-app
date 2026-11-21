plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            // The common IOException (a typealias of java.io.IOException on the JVM).
            api(libs.kotlinx.io.core)
            api(libs.ktor.client.core)
            // api: :app builds the gateway client (JVM: with its shared OkHttpClient, which comes with the SDK).
            api(libs.athena.gateway.client)
            implementation(libs.ktor.network)
            implementation(libs.atomicfu)
        }
        commonTest.dependencies {
            implementation(project(":core:transport-testing"))
            implementation(libs.ktor.client.mock)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.hivemq.mqtt.client)
        }
        jvmTest.dependencies {
            implementation(project(":core:crypto")) // seals replies as the gateway would
            implementation(libs.mockwebserver)
        }
    }
}
