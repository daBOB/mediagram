package ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Lets a test assert no focused node ever sits under [TvHomeLayer] while it is covered. */
internal const val TvHomeLayerTestTag = "tv-home-layer"

/**
 * Whether [TvLibrary]'s own root — the chrome and whichever tab is chosen —
 * sits under a pushed frame right now (a title, the player, search, the
 * trimmed menu page…). Read wherever an arrival effect, a `BackHandler` or
 * the cover's own rotation needs to sit out a visit it cannot see, so a
 * fresh grant is ready the moment this turns false again rather than a
 * one-shot latch already spent on the mount that never happens any more.
 */
internal val LocalLibraryCovered = compositionLocalOf { false }

/**
 * [TvLibrary]'s own root, kept composed and laid out under every pushed
 * frame rather than torn down and rebuilt on every Back — the fix for the
 * multi-second freeze a return to Home cost before this (see the debugger's
 * report this plan's context links name). While [covered]:
 * - nothing here draws ([drawWithContent] skips `drawContent`, so a pushed
 *   frame is never seen through it) — but it is still placed, so there is
 *   nothing to re-measure on the way back;
 * - nothing here can be focused ([focusGroup] plus an `onEnter` that cancels
 *   the search) — a D-pad press on the pushed frame above can never land a
 *   hidden node here, and a restore effect that fires anyway (most are
 *   still gated on [LocalLibraryCovered] themselves, belt and suspenders)
 *   finds nothing to focus either;
 * - its semantics are cleared, so TalkBack and every `onNodeWithText` a test
 *   writes see only the frame actually on screen, never two of the same
 *   heading at once.
 */
@Composable
internal fun TvHomeLayer(
    covered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLibraryCovered provides covered) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .testTag(TvHomeLayerTestTag)
                    .let {
                        if (!covered) {
                            it
                        } else {
                            it
                                .drawWithContent { }
                                .focusGroup()
                                .focusProperties { onEnter = { cancelFocusChange() } }
                                .clearAndSetSemantics { }
                        }
                    },
        ) {
            content()
        }
    }
}

/**
 * [value], except while [covered]: a position save mid playback, or a
 * Continue reorder settling underneath, then keeps whatever [value] was the
 * moment covering began rather than recomposing a layer nothing can see.
 * Released the instant [covered] turns false — the very next read catches
 * up to [value] in one step, not a replay of every change made while frozen.
 */
@Composable
internal fun <T> heldWhile(
    covered: Boolean,
    value: T,
): T {
    var held by remember { mutableStateOf(value) }
    if (!covered) held = value
    return held
}
