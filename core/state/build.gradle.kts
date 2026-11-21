plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
            // SynchronizedObject/synchronized: the common lock (plain JVM monitors on Android).
            implementation(libs.atomicfu)
        }
    }
}
