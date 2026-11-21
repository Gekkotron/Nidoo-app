plugins {
    id("nidoo.kmp.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        // Android/JVM keeps javax.crypto; iOS has no public AES-GCM API outside CryptoKit (iOS spec §7.2).
        iosMain.dependencies {
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.cryptokit)
        }
    }
}
