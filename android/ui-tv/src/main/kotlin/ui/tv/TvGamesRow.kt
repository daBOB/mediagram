package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import ui.common.SPIELTISCH_MISSING
import ui.common.openSpieltisch

/** How long the Games row's "not installed" line stays up. */
private const val GamesNoticeMillis = 3_000L

/** What the Games row does: [open] hands over to Spieltisch, and [notice] says so for a moment when it can't. */
internal class TvGamesRow(val notice: String?, val open: () -> Unit)

@Composable
internal fun rememberTvGamesRow(): TvGamesRow {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(GamesNoticeMillis)
            notice = null
        }
    }
    return TvGamesRow(notice) { notice = if (context.openSpieltisch()) null else SPIELTISCH_MISSING }
}
