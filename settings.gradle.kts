pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SmartIntercom"

// App module
include(":app")

// Core modules
include(":core:model")
include(":core:common")

// Engine modules
include(":engine:audio")
include(":engine:session")

// Transport modules
include(":transport:api")
include(":transport:local-nearby")
include(":transport:local-wifidirect")
include(":transport:internet-webrtc")
include(":transport:mesh")

// Feature modules
include(":feature:ui")
include(":bluetooth")
include(":security")
include(":data")
include(":service")

// Unified AstraRide Application


