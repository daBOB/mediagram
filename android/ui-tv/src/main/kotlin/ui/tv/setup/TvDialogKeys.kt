package ui.tv.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Where a dialog sends the keys it has no use for, before its own
 * controls see them: the screen it was opened over.
 *
 * A dialog is a window of its own, so every key goes to it and none to the
 * screen behind — and a key no window takes goes on to the system, which
 * hands the media keys to the playback session. Over the player that
 * session restarts the title on Previous, which is not what a viewer
 * filing it into a list asked for. The player provides its own remote
 * here; everywhere else nothing is taken and nothing changes.
 */
internal val LocalTvDialogKeys = staticCompositionLocalOf<(KeyEvent) -> Boolean> { { false } }

/** Offers every key in the dialog to [LocalTvDialogKeys] first. */
@Composable
internal fun Modifier.keysToTheScreenBehind(): Modifier = onPreviewKeyEvent(LocalTvDialogKeys.current)
