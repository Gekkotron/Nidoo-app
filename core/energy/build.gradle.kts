plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:format"))
            implementation(project(":core:history"))
            implementation(project(":core:transport"))
        }
        jvmTest.dependencies {
            implementation(project(":core:state"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
