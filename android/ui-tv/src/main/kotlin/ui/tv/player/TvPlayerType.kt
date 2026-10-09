package ui.tv.player

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import designsystem.CatalogueType

/**
 * The player overlay's own two sizes: every television screen was set in
 * these before the library took the web's roles ([designsystem.TvTypeScale]),
 * and the overlay keeps them for now, on purpose — its dialogs (Add to list,
 * Kids) draw in the library's `TvDialog` and take its roles. Nothing has measured its
 * overlay against the web's player card (`player-card.css`,
 * `playback.css`) yet, and the box check that would show a change there
 * plays no titles. The pass that does both replaces these, the way the
 * library's were replaced.
 */
internal object TvPlayerType {
    val title: TextStyle =
        TextStyle(fontFamily = CatalogueType.headlineSmall.fontFamily, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-0.1).sp)
    val body: TextStyle = TextStyle(fontFamily = CatalogueType.bodyMedium.fontFamily, fontSize = 18.sp)
}
