package ui.catalog.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Consumes every tap without doing anything with it — no press tint, no
 * callback — so a press while a film comes up out of the dark lands on nothing.
 */
@Composable
internal fun BoxScope.CoverTapShield() {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    )
}
