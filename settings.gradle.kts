pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Mini_Project"
include(":core-crypto")
include(":core-db")
include(":transport-tor")
include(":transport-mesh")
include(":transport-relay")
include(":mailbox")
include(":ui")
