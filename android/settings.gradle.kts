// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }

    // Gradle 9.5 auto-wires gradle/libs.versions.toml as the "libs" catalog for
    // both plugin-version resolution (libs.plugins.* in plugins{} blocks) and,
    // via each project's VersionCatalogsExtension, for dependencies{} blocks.
    // Declaring versionCatalogs.create("libs") { from(...) } here as well makes
    // Gradle import the same file into the same catalog a second time, which
    // 9.5 rejects ("from() called more than once").
}

rootProject.name = "mediagram-android"

// App module - picks touch or television surface at launch
include(":app")

// Surface modules - Compose UI per form factor, both render feature UiState
include(":ui-mobile")
include(":ui-tv")

// Feature modules - ViewModels and UiState only, no composables, no feature-to-feature deps
include(":feature:catalog")
include(":feature:player")

// Core modules - shared library code, direction is feature/ui -> core:data -> core:rust
include(":core:designsystem")
include(":core:data")
include(":core:rust")
include(":core:model")
