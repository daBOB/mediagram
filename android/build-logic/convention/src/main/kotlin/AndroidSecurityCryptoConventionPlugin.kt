/*
 * Convention plugin for modules that store a secret on disk
 * Configures: androidx.security:security-crypto for EncryptedSharedPreferences
 * Applies to: modules that hold a value that must never sit in plain preferences
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidSecurityCryptoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                add("implementation", libs.findLibrary("androidx.security.crypto").get())
            }
        }
    }
}
