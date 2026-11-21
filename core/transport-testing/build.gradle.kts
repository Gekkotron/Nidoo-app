// Test doubles for :core:transport (fake transports and gateway, an embedded broker on the JVM).
// A module of its own because Kotlin Multiplatform modules can't publish java-test-fixtures.
plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:transport"))
            api(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            api(libs.hivemq.mqtt.client)
            implementation(libs.moquette.broker)
        }
    }
}
