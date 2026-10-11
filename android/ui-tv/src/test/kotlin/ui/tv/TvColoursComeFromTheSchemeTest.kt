package ui.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every TV colour comes from the scheme, so a style's neutrals reach it:
 * only the theme and the looks read [designsystem.Palette] directly. A
 * direct read elsewhere keeps mediagram's ink under every style.
 */
class TvColoursComeFromTheSchemeTest {
    @Test
    fun onlyTheThemeAndTheLooksReadThePalette() {
        val sources = File("src/main/kotlin")
        assertTrue(sources.isDirectory, "run from the module, where the sources are: ${sources.absolutePath}")
        val readers =
            sources
                .walk()
                .filter { it.extension == "kt" && it.name !in setOf("TvTheme.kt", "TvLook.kt") }
                .filter { file ->
                    file.readLines().any { line ->
                        val code = line.trimStart()
                        "Palette." in code &&
                            !code.startsWith("*") &&
                            !code.startsWith("/*") &&
                            !code.startsWith("//")
                    }
                }.map { it.name }
                .sorted()
                .toList()
        assertEquals(emptyList(), readers)
    }
}
