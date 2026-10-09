package designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import com.mediagram.android.core.designsystem.R

/**
 * The huge uppercase title atop a page — Fraunces at the web's own weight
 * and optical size for a wide viewport (`.dept-title`, `departments.css:30-35`).
 * Font size is deliberately unset: [PageHead] sizes it per screen with
 * `TextAutoSize.StepBased`, so only the shape of the title — weight,
 * tracking, how tight the lines sit — belongs to the style. `Center` +
 * `Trim.None` keep the 0.86em line height from clipping a capital's top and
 * bottom, which `Trim.Both` (Compose's own default) does not guarantee.
 *
 * A static font, the same reason [Display] and [CoverTitle] are — see
 * `Type.kt`'s own top note. This was the face that first surfaced the bug: a
 * page title draws through `TextAutoSize.StepBased` (see [PageHead]),
 * which re-measures the same `FontFamily` at several candidate sizes in one
 * pass, and on the device that found this, that re-measurement left the
 * requested `wght`/`opsz` axis values behind and fell back to
 * `fraunces.ttf`'s own registered default instance — `wght 900`, `opsz 9`,
 * its heaviest, most decorative cut. It was not the only face that carried
 * the failure, only the one whose own re-measurement pass happened to
 * surface it first — that note's own on-device A/B is what showed
 * [Display] dropped the same axis values through a plain `Text`, no
 * `TextAutoSize` involved at all.
 */
val PageTitle =
    TextStyle(
        fontFamily = FontFamily(Font(resId = R.font.fraunces_page_title, weight = FontWeight.Medium)),
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.03).em,
        lineHeight = 0.86.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * [PageTitle] at the web's narrow-viewport optical size (`departments.css:90`)
 * — a compact phone screen, or a television read from the couch rather than
 * a tablet held in the hand. Its own static instance for the same reason.
 */
val PageTitleCompact =
    PageTitle.copy(fontFamily = FontFamily(Font(resId = R.font.fraunces_page_title_compact, weight = FontWeight.Medium)))

/**
 * The cover story's own headline — Fraunces at the widest cut the variable
 * font's optical-size axis offers, `opsz 144` (`.cover-title`'s own
 * `font-variation-settings: "opsz" 144`, `home.css:48-55`), a size no other
 * title on this catalogue reaches for. A static cut, like [Display] — see
 * `Type.kt`'s own top note; font size is left to the caller
 * (`ui.catalog.home.HomeType`'s own `fluid()`), the same split [PageTitle]
 * makes.
 */
val CoverTitle =
    TextStyle(
        fontFamily = FontFamily(Font(resId = R.font.fraunces_cover_600, weight = FontWeight.SemiBold)),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.035).em,
        lineHeight = 0.86.em,
    )
