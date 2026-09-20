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
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "app.android.lint")

            // The Telegram *application* identity (not a user's account) that
            // the human supplies out of band, since it can't live in source
            // control. Empty defaults keep the project buildable without it;
            // the app itself refuses to reach the native core with a blank
            // identity rather than let it fail unpredictably at runtime.
            val localProperties = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) file.inputStream().use(::load)
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                    buildConfigField(
                        "String",
                        "MEDIAGRAM_API_ID",
                        "\"${localProperties.getProperty("MEDIAGRAM_API_ID", "")}\"",
                    )
                    buildConfigField(
                        "String",
                        "MEDIAGRAM_API_HASH",
                        "\"${localProperties.getProperty("MEDIAGRAM_API_HASH", "")}\"",
                    )
                }

                buildFeatures {
                    buildConfig = true
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
