pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // The gateway's own Kotlin Multiplatform SDK (athena-gateway-client, JVM + iOS), published by its
        // release workflow to a static Maven repository on GitHub Pages.
        maven("https://gekkotron.github.io/Nidoo-gateway/maven") {
            content { includeGroup("io.github.gekkotron") }
        }
    }
}

rootProject.name = "Nidoo"

include(":shared")
include(":core:model")
include(":core:crypto")
include(":core:transport")
include(":core:transport-testing")
include(":core:connection")
include(":core:state")
include(":core:source")
include(":core:data")
include(":core:alerts")
include(":core:history")
include(":core:format")
include(":core:flows")
include(":core:assist")
include(":core:dashboards")
include(":core:climate")
include(":core:push")
include(":app")
include(":core:demo")
include(":core:designsystem")
include(":core:designsystem-screenshots")
include(":feature:home")
include(":feature:onboarding")
include(":feature:energy")
include(":feature:history")
include(":feature:security")
include(":feature:automations")
include(":feature:settings")
include(":feature:alerts")
include(":feature:widget")
include(":feature:controls")
include(":feature:voice")
