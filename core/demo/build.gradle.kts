plugins {
    id("nidoo.kmp.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:transport"))
            api(libs.kotlinx.serialization.json)
            implementation(libs.atomicfu)
        }
        commonTest.dependencies {
            implementation(project(":core:source"))
            implementation(project(":core:dashboards"))
            implementation(project(":core:climate"))
        }
    }
}
