package ui.tv.catalog

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the wall, rows or list on screen take the remote the moment they
 * appear, as every one of them does by default. Turned off by a screen
 * whose way back lands somewhere they cannot see — the masthead's Search,
 * a genre link in a show's header — so that stop keeps the remote rather
 * than losing it to the first plate a moment later.
 */
internal val LocalTakesArrivalFocus = compositionLocalOf { true }
