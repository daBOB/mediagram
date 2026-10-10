package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import designsystem.CountdownLeader

/** The block's ground, the web's `#0a0a0b` (`shell.css:177`): the cover's black before there is a cover. */
private val LoadingGround = Color(0xFF0A0A0B)

/** The web's room under the words and at the edges on a phone (`shell.css:185`); beside, 96px under the gutter (`:176`). */
private val PHONE_EDGE = 16.dp
private val PHONE_FOOT = 104.dp
private val WIDE_FOOT = 96.dp

/** The leader stands this far above the words (`shell.css:175`, `gap: 24px`). */
private val LEADER_GAP = 24.dp

/**
 * The library loading, shaped like the cover story it is about to become:
 * the web's `loading-state` (`index.html:119-121`, `shell.css:173-178`). A dark
 * block the cover's height, the film leader counting down above its words
 * near the foot. The web's reason holds here too: the catalogue arrives into
 * the page it is loading instead of replacing another.
 *
 * Drawn for every wait that opens the library: the setup check, the profile
 * gate's read, and the library itself, so a cold start is one block, as the
 * web's first paint is.
 */
@Composable
internal fun LibraryLoading() {
    val window = LocalConfiguration.current
    val width = window.screenWidthDp.dp
    val compact = width <= CompactBreakpoint
    val edge = if (compact) PHONE_EDGE else gutterFor(width)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(coverHeightFor(compact, window.screenHeightDp.dp))
                .background(LoadingGround)
                .padding(start = edge, end = edge, bottom = if (compact) PHONE_FOOT else WIDE_FOOT),
        verticalArrangement = Arrangement.spacedBy(LEADER_GAP, Alignment.Bottom),
    ) {
        CountdownLeader()
        Text(text = "Loading your library…", style = MaterialTheme.typography.bodyLarge, color = OnImage2)
    }
}
