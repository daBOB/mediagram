/*
 * Print APKs task configuration
 * Creates task to print all generated APK paths
 */

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Configure task to print all APK paths for a project
 * Usage: ./gradlew print<Variant>Apks
 */
internal fun Project.configurePrintApksTask(
    extension: AndroidComponentsExtension<*, *, *>,
) {
    extension.onVariants { variant ->
        val apkFolder = variant.artifacts.get(SingleArtifact.APK)
        val variantName = variant.name

        tasks.register("print${variantName.replaceFirstChar(Char::uppercase)}Apks") {
            group = "help"
            description = "Prints all APK paths for $variantName variant"

            doLast {
                println("APKs for $variantName:")
                apkFolder.get().asFile
                    .walkTopDown()
                    .filter { it.extension == "apk" }
                    .forEach { apk -> println("  - ${apk.absolutePath}") }
            }
        }
    }
}
