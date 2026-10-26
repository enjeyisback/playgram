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
        maven {
            url = java.net.URI("https://yuroyami.github.io/maven")
            content { includeModuleByRegex("io\\.github\\.yuroyami", "libmpvkt.*") }
        }
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "PlaygramApp"
include(":app")
