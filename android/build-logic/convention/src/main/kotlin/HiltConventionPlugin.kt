/*
 * Convention plugin for Hilt dependency injection
 * Configures: Hilt plugin, KSP compiler, common dependencies
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.google.devtools.ksp")
            apply(plugin = "dagger.hilt.android.plugin")

            val hiltCompiler = libs.findLibrary("hilt.compiler").get()
            val hiltTesting = libs.findLibrary("hilt.android.testing").get()

            dependencies {
                add("implementation", libs.findLibrary("hilt.android").get())
                // Every module that carries a `@HiltViewModel` needs `ViewModel`
                // and `viewModelScope`, which is why this lives alongside Hilt
                // rather than in the plain library convention plugin.
                add("implementation", libs.findLibrary("androidx.lifecycle.viewmodel.ktx").get())
                add("ksp", hiltCompiler)

                // For testing
                add("kspTest", hiltCompiler)
                add("testImplementation", hiltTesting)
                add("kspAndroidTest", hiltCompiler)
                add("androidTestImplementation", hiltTesting)
            }
        }
    }
}
