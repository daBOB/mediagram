package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import ui.tv.currentTvLook

/**
 * What a row of titles — a Home strip, a department row, Similar — cuts its
 * cards to: posters under mediagram, 16:9 stills five across under Apple TV
 * and Google TV styles, as those home screens' rows are. Every row reads its
 * shape, picture and width here, so the rows switch together; a wall of
 * plates never reads it and stays posters in every style.
 */
internal val rowCardsAreLandscape: Boolean
    @Composable
    @ReadOnlyComposable
    get() = currentTvLook.landscapeRows

/** A row card's art proportions in the current style. */
internal val rowArtRatio: Float
    @Composable
    @ReadOnlyComposable
    get() = if (rowCardsAreLandscape) StillAspectRatio else PosterAspectRatio

/** A row card's width in a row [rowWidth] wide, in the current style. */
@Composable
@ReadOnlyComposable
internal fun rowPlateWidth(rowWidth: Dp): Dp = if (rowCardsAreLandscape) landscapeStripPlateWidth(rowWidth) else stripPlateWidth(rowWidth)

/** The picture a row card shows: the backdrop in a landscape row where there is one — else the poster, cropped — and the poster in a portrait row. */
internal fun <T> rowArtOf(
    landscape: Boolean,
    poster: T?,
    backdrop: T?,
): T? = if (landscape) backdrop ?: poster else poster

/** A still's own proportions. */
internal const val StillAspectRatio = 16f / 9f
