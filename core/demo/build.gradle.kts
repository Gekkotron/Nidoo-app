plugins {
    id("nidoo.jvm.library")
    id("nidoo.serialization")
}

dependencies {
    api(project(":core:transport"))
    testImplementation(project(":core:source"))
    testImplementation(project(":core:dashboards"))
    testImplementation(project(":core:climate"))
}
