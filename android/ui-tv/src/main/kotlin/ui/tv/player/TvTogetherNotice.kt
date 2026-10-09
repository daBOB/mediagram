package ui.tv.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.common.player.togetherNote

/**
 * What another screen in the session just did — "Paused by Tablet" — as a
 * pill at the top centre of the picture, the phone's and the web's
 * `.together-note`. It shows while the controls rest, and asks for no focus:
 * a sentence to read, as [TvActionNotice] is. The player takes the words
 * away after two seconds, so this keeps no timer of its own.
 */
@Composable
internal fun TvTogetherNotice(
    text: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (text != null) {
            Text(
                text = text,
                style = TvTypeScale.body,
                color = Palette.Text,
                modifier = Modifier.togetherNote().padding(horizontal = Spacing.medium, vertical = Spacing.small),
            )
        }
    }
}
