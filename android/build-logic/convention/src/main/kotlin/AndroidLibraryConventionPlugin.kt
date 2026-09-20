/*
 * Convention plugin for Android library modules
 * Configures: Android, Lint, Testing
 * Note: AGP 9+ has built-in Kotlin support, no need for kotlin-android plugin
 */

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "app.android.lint")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    
                    // Version catalog entries for targetSdk
                    testOptions.targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
                    lint.targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
                }
                
                testOptions {
                    animationsDisabled = true
                }
                
                configureGradleManagedDevices(this)
                
                // Resource prefix based on module path
                // :core:data → core_data_
                resourcePrefix = path.split("""\W""".toRegex())
                    .drop(1)
                    .distinct()
                    .joinToString(separator = "_")
                    .lowercase() + "_"
            }
            
            extensions.configure<LibraryAndroidComponentsExtension> {
                configurePrintApksTask(this)
                disableUnnecessaryAndroidTests(target)
            }
            
            dependencies {
                // Plain androidx.test JUnit4, not kotlin-test: kotlin-test's bare artifact
                // only ships the common `expect` annotations, resolved to a real framework
                // by the Kotlin Gradle plugin's JVM/Android target substitution rule — a
                // rule that never runs here, since AGP 9's built-in Kotlin support means
                // that plugin is never applied. androidx.test's JUnit4 runner needs no such
                // substitution; it is a real implementation on its own.
                add("androidTestImplementation", libs.findLibrary("androidx.junit").get())
                add("androidTestImplementation", libs.findLibrary("androidx.test.runner").get())
                // kotlin-test alone only ships the common `expect` annotations; without the
                // Kotlin Gradle plugin's JVM/Android target (AGP 9's built-in Kotlin support
                // means it is never applied here) nothing resolves them to an actual test
                // framework, so kotlin.test.Test stays unresolved. kotlin-test-junit pulls
                // its own JUnit4-backed implementation and needs no such substitution.
                add("testImplementation", libs.findLibrary("kotlin.test.junit").get())
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
            }
        }
    }
}
