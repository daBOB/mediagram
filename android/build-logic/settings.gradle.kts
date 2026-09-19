dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Named differently from the outer build's "libs" catalog: pluginManagement's
    // includeBuild("build-logic") shares catalog names with the outer settings file,
    // and two catalogs both named "libs" collide ("from() called more than once").
    versionCatalogs {
        create("buildLogicLibs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
