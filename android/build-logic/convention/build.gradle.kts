/*
 * Build script for convention plugins
 * This module contains reusable convention plugins for the project
 */

plugins {
    `kotlin-dsl`
}

group = "com.example.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(buildLogicLibs.android.gradlePlugin)
    compileOnly(buildLogicLibs.kotlin.gradlePlugin)
    compileOnly(buildLogicLibs.kotlin.composeGradlePlugin)
    compileOnly(buildLogicLibs.ksp.gradlePlugin)
    compileOnly(buildLogicLibs.spotless.gradlePlugin)
    implementation(buildLogicLibs.plugin.detekt)
    implementation(buildLogicLibs.kotlinx.coroutines.core)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "app.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "app.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "app.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "app.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidSecurityCrypto") {
            id = "app.android.security.crypto"
            implementationClass = "AndroidSecurityCryptoConventionPlugin"
        }
        register("androidFeature") {
            id = "app.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidTest") {
            id = "app.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidLint") {
            id = "app.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("hilt") {
            id = "app.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("detekt") {
            id = "app.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("spotless") {
            id = "app.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
        register("jvmLibrary") {
            id = "app.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "app.kotlin.serialization"
            implementationClass = "KotlinSerializationConventionPlugin"
        }
    }
}
