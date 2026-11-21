plugins {
    id("nidoo.kmp.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:flows"))
            api(project(":core:dashboards"))
            api(project(":core:climate"))
            api(project(":core:state"))
            api(libs.kotlinx.serialization.json)
            api(project(":core:transport"))
            implementation(libs.atomicfu)
        }
        commonTest.dependencies {
            implementation(project(":core:transport-testing"))
        }
    }
}
