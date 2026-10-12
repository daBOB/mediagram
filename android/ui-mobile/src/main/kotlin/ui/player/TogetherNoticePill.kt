package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing
import ui.common.player.togetherNote

/**
 * What another screen in the session just did — "Paused by TV" — as a pill
 * at the top centre of the picture, the web's `.together-note`. It shows
 * while the controls rest. The player takes the words away after two
 * seconds, so this keeps no timer of its own. The region stays when it is
 * empty, as the web's does, so a screen reader hears each change.
 */
@Composable
internal fun TogetherNoticePill(
    text: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (text != null) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.togetherNote().padding(horizontal = Spacing.medium, vertical = Spacing.small),
            )
        }
    }
}
