/*
 * Convention plugin for Android application modules
 * Configures: Android, Lint, Dependency Guard
 * Note: AGP 9+ has built-in Kotlin support, no need for kotlin-android plugin
 */

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "app.android.lint")

            // No Telegram credentials are baked in here. They are a property
            // of the device the app runs on, not of the machine that built
            // it: the app asks for them on first run and keeps them in
            // keystore-backed storage, which is also the only arrangement a
            // television or a sideloaded APK can be set up under.

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk =
                        libs
                            .findVersion("targetSdk")
                            .get()
                            .toString()
                            .toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                testOptions {
                    animationsDisabled = true
                }

                configureGradleManagedDevices(this)
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                configurePrintApksTask(this)
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx.core").get())
                add("implementation", libs.findLibrary("androidx.lifecycle.runtime.ktx").get())
                libs.findBundle("unit.test").ifPresent { add("testImplementation", it) }
                add("testImplementation", libs.findLibrary("mockk").get())
            }
        }
    }
}
