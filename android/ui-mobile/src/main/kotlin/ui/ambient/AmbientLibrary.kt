package ui.ambient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import designsystem.Backdrop
import designsystem.LocalBackdrop
import ui.common.ambient.AmbientBackdrop
import ui.common.ambient.AmbientTint
import ui.common.ambient.LocalAmbientTint
import ui.common.ambient.rememberPosterColor
import java.io.File

/** How far down the window the reading line sits: the viewer's choice, about a third. */
private const val LINE_FRACTION = 1f / 3f

/**
 * The library's Ambient: one tint and one reading line, held here so they
 * carry from a wall to the title it opened and back, with the glow drawn
 * under every page [content] shows. Switched on by [Backdrop.AMBIENT] (the
 * tablet's way in; television has its own Style). The structure is the same
 * with Ambient off — only the glow and the two locals go — so switching it in
 * Settings, itself a page of [content], loses nothing below. [drawn] is false
 * for the full-window player, which paints its own picture.
 */
@Composable
internal fun AmbientLibrary(drawn: Boolean, content: @Composable () -> Unit) {
    val on = LocalBackdrop.current == Backdrop.AMBIENT
    val tint = remember { AmbientTint() }
    val reading = remember { ReadingLine(tint) }
    Box(Modifier.fillMaxSize()) {
        if (on) {
            if (drawn) AmbientBackdrop(tint, Modifier.fillMaxSize())
            val lineY = LocalConfiguration.current.screenHeightDp * LocalDensity.current.density * LINE_FRACTION
            LaunchedEffect(reading, lineY) { reading.follow(lineY) }
            // Keyed: a poster's colour is worked out once per file, so each pick gets its own.
            key(reading.picked) { PickedGlow(reading) }
        }
        CompositionLocalProvider(
            LocalAmbientTint provides tint.takeIf { on },
            LocalReadingLine provides reading.takeIf { on },
        ) { content() }
    }
}

@Composable
private fun PickedGlow(reading: ReadingLine) {
    val colour = rememberPosterColor(reading.picked?.let(::File))
    LaunchedEffect(colour, reading.pins) { if (reading.pins == 0) colour?.let { reading.tint.target = it } }
}

/**
 * A title or show page glows in its own art's colour from the moment it
 * opens, and holds it against the rows on the page (a Similar shelf) until it
 * leaves; the reading line takes over again then.
 */
@Composable
internal fun AmbientFromArt(artPath: String?) {
    val reading = LocalReadingLine.current ?: return
    DisposableEffect(reading) {
        reading.pins++
        onDispose { reading.pins-- }
    }
    key(artPath) {
        val colour = rememberPosterColor(artPath?.let(::File))
        LaunchedEffect(colour) { colour?.let { reading.tint.target = it } }
    }
}
