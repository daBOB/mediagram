package ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.mediagram.android.core.designsystem.R
import ui.common.MenuActions

/** The bar's own height, before the status-bar inset — the web's `--masthead-height` (`theme.css:114`, `76px` on the wide layout). */
internal val DepartmentsBarHeight = 76.dp

/** The bar's own colour over the cover, translucent black with light type — the web's `[data-cover]` opening state (`shell.css:227`). No blur: Compose cannot blur what is behind a node without a new dependency, so a flatter starting alpha stands in for it instead. */
internal val OverCoverBg = Color(0x590A0A0B)
private val OverImageInk = Color(0xFFF6F2EA)

/** The bar's own background, blended between [OverCoverBg] and [solid] — reaches [solid] exactly (not just close) once [blend] is 1, since nothing here can blur whatever a deep scroll would otherwise show through it. */
internal fun barBackground(
    solid: Color,
    blend: Float,
): Color = lerp(OverCoverBg, solid, blend)

/** The window width the pills themselves narrow at, matching [LibraryRail]'s own breakpoint (`shell.css:169-171`). */
private val NarrowBreakpoint = 1180.dp

/**
 * The departments pill bar: Home, then each shelf, then Collections, counts
 * beside each — `index.html`'s `nav.departments` (58-65) — plus search,
 * avatar and the trimmed [AndroidOnlyMenu]. EXPANDED-only, drawn over
 * [ui.chrome.LibraryHome]'s content; [blend] is 0 for the translucent
 * opening state over whichever tab's own hero art is on screen — Home's
 * cover, or a department's own — and 1 for the solid state everything else
 * starts in.
 */
@Composable
internal fun DepartmentsBar(
    pills: List<DepartmentPill>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: ProfileBarState,
    menu: MenuActions,
    onAskStartOver: () -> Unit,
    blend: Float,
    modifier: Modifier = Modifier,
) {
    // The web's own solid state is `--paper` at 78%, but it sits over a
    // `backdrop-filter: blur` the page behind it never stops drawing —
    // that blur is what lets 78% still read as solid. Compose has no blur
    // there (this file's own note on why, above); without one, anything
    // under a translucent bar just shows through it once the page has
    // scrolled deep enough to matter, so the solid state here is `--paper`
    // at its own full opacity instead. Only the opening state over the
    // cover stays translucent.
    val solidBg = MaterialTheme.colorScheme.background
    val solidInk = MaterialTheme.colorScheme.onBackground
    val bg = barBackground(solidBg, blend)
    val ink = lerp(OverImageInk, solidInk, blend)
    val narrow = LocalConfiguration.current.screenWidthDp.dp <= NarrowBreakpoint
    val gutter = gutterFor(LocalConfiguration.current.screenWidthDp.dp)

    // The status-bar strip is its own `Spacer`, not a `windowInsetsPadding`
    // folded into the pill row's own modifier chain: chained onto a
    // `background` that also carries a fixed `height()` and a horizontal
    // `padding()`, the inset padding here measured as reserving the right
    // amount of space but the background this bar draws stopped short of
    // painting over it — whatever a cover slide's own content behind it,
    // it showed through the status-bar strip even at `blend = 1`. A
    // `Column` — this height-only spacer, then the pill row, both inside
    // one shared `background(bg)` — leaves nothing for either to disagree
    // about the size of.
    Column(modifier = modifier.fillMaxWidth().background(bg)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    // A phone in EXPANDED landscape can have the 3-button nav
                    // bar on either side; the rail already claims the start
                    // side (`LibraryRail`'s own safeDrawing), so this only
                    // needs the end.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                    .height(DepartmentsBarHeight)
                    .padding(horizontal = gutter),
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pills.forEachIndexed { index, pill ->
                    Pill(pill = pill, active = index == selected, ink = ink, horizontalPadding = if (narrow) 12.dp else 16.dp, onClick = { onSelect(index) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(icon = R.drawable.core_designsystem_ic_search, description = "Search", tint = ink, onClick = onSearch)
                ChromeAvatar(profile = profile)
                AndroidOnlyMenu(menu = menu, onAskStartOver = onAskStartOver, tint = ink)
            }
        }
    }
}

/** The bar's own side margin — the web's `clamp(16px, 3.2vw, 56px)` gutter (`theme.css:112`). */
private fun gutterFor(windowWidth: Dp): Dp = (windowWidth * 0.032f).coerceIn(16.dp, 56.dp)
