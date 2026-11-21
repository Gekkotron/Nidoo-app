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
        }
        commonTest.dependencies {
            implementation(project(":core:transport-testing"))
        }
        jvmMain.dependencies {
            // api: :app builds AthenaGatewayApi with its shared OkHttpClient (OkHttp comes with the SDK).
            api(libs.athena.gateway.client)
            implementation(libs.hivemq.mqtt.client)
        }
        jvmTest.dependencies {
            implementation(project(":core:crypto")) // seals replies as the gateway would
            implementation(libs.mockwebserver)
        }
    }
}
