package ui.tv.chrome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import designsystem.LocalTvStyle
import designsystem.Overscan
import designsystem.TvStyle
import ui.common.RailItem
import ui.tv.hasRail

/**
 * What a page under the chrome is padded by, read instead of [Overscan]
 * directly by every page the library shows — the catalogue's own and every
 * page opened over it, which [ui.tv.TvFrameChrome] puts under the same
 * [TvLibraryChrome]. The default, plain [Overscan] on every side, is only
 * for a page drawn outside it: a preview, a test.
 */
internal data class TvPagePadding(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

internal val LocalTvPagePadding =
    compositionLocalOf { TvPagePadding(Overscan.horizontal, Overscan.vertical, Overscan.horizontal, Overscan.vertical) }

/** [TvPagePadding] as a [PaddingValues] — every reader wants it as one, not as four separate [Dp]s. */
internal fun TvPagePadding.asPaddingValues(): PaddingValues = PaddingValues(start = start, top = top, end = end, bottom = bottom)

/**
 * Everything [TvLibraryChrome] hands its own rows and pills their focus
 * through, and everything a restore key resolves to once it names one of
 * them — built once per [ui.tv.catalog.TvCatalogScreen] by
 * [rememberTvChromeFocus] so a screen coming back to "the search button"
 * or "the Latest row" always reaches the very requester [TvLibraryChrome]
 * itself renders onto.
 */
internal class TvChromeFocus(
    val selectedPillFocus: FocusRequester,
    val searchFocus: FocusRequester,
    val avatarFocus: FocusRequester,
    val settingsFocus: FocusRequester,
    val railRowFocus: Map<RailItem, FocusRequester>,
    val contentFocus: FocusRequester,
    private val style: TvStyle,
) {
    /**
     * Where the remote goes for [item]: its own row on the rail, in the one
     * style that draws a rail; otherwise whatever stands for the rail now —
     * Google TV style's gear for Settings, the avatar (whose menu lists the
     * rail's rows) for the rest. A rail row's requester has nothing attached
     * to it in a rail-less style, so it must never be asked directly.
     */
    fun railTarget(item: RailItem): FocusRequester =
        when {
            style.hasRail -> railRowFocus.getValue(item)
            style == TvStyle.GOOGLE && item == RailItem.SETTINGS -> settingsFocus
            else -> avatarFocus
        }
}

/**
 * One instance per style, so the readers handed it (and its [TvChromeFocus.railTarget])
 * see the same object every frame and skip. A style changes only from Settings, which
 * the catalogue is not composed under, so no requester is ever replaced under focus.
 */
@Composable
internal fun rememberTvChromeFocus(): TvChromeFocus {
    val style = LocalTvStyle.current
    return remember(style) {
        TvChromeFocus(
            selectedPillFocus = FocusRequester(),
            searchFocus = FocusRequester(),
            avatarFocus = FocusRequester(),
            settingsFocus = FocusRequester(),
            railRowFocus = RailItem.entries.associateWith { FocusRequester() },
            contentFocus = FocusRequester(),
            style = style,
        )
    }
}
