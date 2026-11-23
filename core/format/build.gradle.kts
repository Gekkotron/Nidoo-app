plugins {
    id("nidoo.kmp.jvm.library")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
