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
        // GeckoView, the Firefox engine. Only Mozilla groups are looked up here.
        maven("https://maven.mozilla.org/maven2/") { content { includeGroupByRegex("org\\.mozilla.*") } }
    }
}
rootProject.name = "Sheer"
include(":app")
