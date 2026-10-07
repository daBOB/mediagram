package ui.common.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A genre tile's proportions on the Genres page (`.genre-tile`, `catalog.css`). */
const val GENRE_TILE_ASPECT = 16f / 9f

/** A genre tile's proportions in the Movies department's own row (`.dept-row .genre-tile`, `departments.css`). */
const val GENRE_ROW_TILE_ASPECT = 16f / 8f

/** A franchise's or a list's card on Collections and in Search (`.destination`, `departments.css`). */
const val DESTINATION_ASPECT = 4f / 3f

/**
 * The dark fade an art tile's name is read through — `.genre-tile::before`
 * (`catalog.css`) or, with [destination], `.destination::before`
 * (`departments.css`): dark at the foot, easing to a light tone part-way up
 * and holding it to the top. Shared by the phone's and the television's own
 * tiles, so the two never drift from the web's stops separately.
 */
@Composable
fun ArtTileScrim(
    destination: Boolean,
    modifier: Modifier = Modifier,
) {
    val (clear, deep, reach) = if (destination) Triple(0x1A, 0xDB, 0.65f) else Triple(0x1F, 0xD1, 0.7f)
    Box(modifier.background(Brush.verticalGradient(0f to scrim(clear), (1f - reach) to scrim(clear), 1f to scrim(deep))))
}

private fun scrim(alpha: Int) = Color(alpha shl 24 or 0x080809)
