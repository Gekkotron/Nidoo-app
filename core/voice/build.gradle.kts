plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }
    }
}
