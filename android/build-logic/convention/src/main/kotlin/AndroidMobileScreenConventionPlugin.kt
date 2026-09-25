/*
 * Convention plugin for touch screen modules
 * Configures: poster loading (Coil), lifecycle-aware state collection,
 * window-size-class adaptive layout, Material 3, and the hiltViewModel()
 * Compose helper.
 * Requires: `app.android.library` and `app.android.library.compose` already applied.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidMobileScreenConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                add("implementation", libs.findLibrary("coil.compose").get())
                add("implementation", libs.findLibrary("androidx.lifecycle.runtime.compose").get())
                add("implementation", libs.findLibrary("androidx.hilt.lifecycle.viewmodel.compose").get())
                // Material 3 lives here, not in the shared compose bundle: the
                // touch surface draws with it, the TV surface must never see
                // it on its compile classpath.
                add("implementation", libs.findLibrary("androidx.compose.material3").get())
                add("implementation", libs.findLibrary("androidx.compose.material3.adaptive").get())
            }
        }
    }
}
