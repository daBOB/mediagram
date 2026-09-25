package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import kotlinx.coroutines.delay
import ui.player.SCRIM_ALPHA

/** Finds the notice in a test. */
internal const val TvActionNoticeTag = "tv-action-notice"

/** Long enough to read a sentence from the couch, short enough not to sit over the film. */
internal const val ACTION_NOTICE_MS = 6_000L

/**
 * A mark that could not be confirmed, said over the film and then gone by
 * itself ([onGone]). The phone's snackbar carries a Dismiss button; on a
 * television that is a button the remote would have to leave the controls
 * to reach, only to make a sentence go away, so this has none and asks for
 * no focus — a banner to read, not a thing to answer. A new notice starts
 * its own time over.
 */
@Composable
internal fun TvActionNotice(
    notice: String?,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notice == null) return
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(notice) {
        delay(ACTION_NOTICE_MS)
        gone()
    }
    Text(
        text = notice,
        style = TvTypeScale.body,
        color = Palette.Text,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .padding(horizontal = Spacing.medium, vertical = Spacing.small)
                .testTag(TvActionNoticeTag),
    )
}
