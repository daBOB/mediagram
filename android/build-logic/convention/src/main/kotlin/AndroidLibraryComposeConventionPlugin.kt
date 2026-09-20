/*
 * Convention plugin for Android library with Compose
 * Applies: Compose compiler plugin and configures Compose options
 * Requires: `app.android.library` already applied so `com.android.library` runs exactly once.
 */

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<LibraryExtension>()
            configureAndroidCompose(extension)

            dependencies {
                libs.findBundle("compose").ifPresent { add("implementation", it) }
            }
        }
    }
}
