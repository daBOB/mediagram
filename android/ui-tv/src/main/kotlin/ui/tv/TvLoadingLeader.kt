package ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.CountdownLeader
import designsystem.TvType
import designsystem.tvDp

/** The web's leader is drawn at 96px (`index.html:101`); this is that size as the television sets it. */
internal val TvLeaderSize = 96.tvDp

/** The leader stands this far above a wait's words, the web's `gap: 24px` (`shell.css:175`). */
internal val TvLeaderGap = 24.tvDp

/**
 * The library loading, the web's first paint (`index.html:119-121`,
 * `shell.css:175-178`): the film leader counting down above its words,
 * left-aligned with them, in the leader's own inks for a dark ground — the
 * television's is always dark. Drawn for every wait that opens the library,
 * the setup check, the profile read and the catalogue itself, so a cold
 * start is one wait, as the tablet's is.
 *
 * The accent is tv-material's, passed in: [CountdownLeader]'s default reads
 * M3's theme, which this surface never provides, and would sweep in M3's
 * baseline violet.
 */
@Composable
internal fun TvLibraryLoading() {
    TvSafeArea {
        Column(verticalArrangement = Arrangement.spacedBy(TvLeaderGap)) {
            CountdownLeader(size = TvLeaderSize, accent = MaterialTheme.colorScheme.primary)
            Text(text = "Loading your library…", style = TvType.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The leader for a wait inside a page, in the page's inks rather than its
 * fixed ones for artwork, as the tablet's in-page waits draw it. Whoever
 * places it puts the words under it, [TvLeaderGap] below.
 */
@Composable
internal fun TvPageLeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    CountdownLeader(
        modifier = modifier,
        size = TvLeaderSize,
        accent = colors.primary,
        ink = colors.onSurfaceVariant,
        numeralInk = colors.onSurface,
    )
}

/** A wait that fills a page: [TvPageLeader] centred above [message], in [ui.tv.catalog.TvCenteredMessage]'s words. */
@Composable
internal fun TvCentredLoading(message: String) {
    TvSafeArea {
        Column(
            verticalArrangement = Arrangement.spacedBy(TvLeaderGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvPageLeader()
            Text(text = message, style = TvType.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
