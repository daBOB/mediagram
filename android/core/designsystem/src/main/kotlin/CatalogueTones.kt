package designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two web tones M3 has no role for — [sidebar], [ruleSoft] — plus
 * [quiet], the same ink-3 value [Palette.RuleStrong]/[Palette.LightRuleStrong]
 * already carry under a name a settings screen can reach for without
 * knowing it is reading an outline role.
 */
data class CatalogueTones(
    val sidebar: Color,
    val ruleSoft: Color,
    val quiet: Color,
)

/** The phone's dark tones, and the television's in its mediagram style. */
val DarkTones = CatalogueTones(Palette.Sidebar, Palette.RuleSoft, Palette.RuleStrong)
internal val LightTones = CatalogueTones(Palette.LightSidebar, Palette.LightRuleSoft, Palette.LightRuleStrong)

/**
 * Dark by default: a `@Preview` or a bare test composes without
 * [MediagramTheme] or [ui.tv.TvTheme], and lands on the same tones the
 * phone's own dark theme uses rather than on an unstyled fallback.
 */
val LocalCatalogueTones = staticCompositionLocalOf { DarkTones }
