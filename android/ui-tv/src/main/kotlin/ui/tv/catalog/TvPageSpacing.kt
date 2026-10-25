package ui.tv.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isFinite
import designsystem.tvDp

/*
 * The spacing a catalogue page takes from the web, each the web's px at the
 * TV's 1280px reading through `designsystem.TvScale`, so it shrinks and grows
 * with the text beside it.
 */

/** `.features` 12 under the cover, `.home-band` 28 and `.home-section` 36 above the rest (`home.css:135,189,192`). */
internal val HomeFeaturesTop = 12.tvDp
internal val HomeBandTop = 28.tvDp
internal val HomeSectionTop = 36.tvDp

/** `.home-band`'s column gap, `clamp(32px, 4vw, 72px)` at 1280px (`home.css:188`). */
internal val HomeBandGap = 51.2.tvDp

/** `.row-head`'s `margin-bottom` (`home.css:194`): a row head to the row it names, on Home and in a department. */
internal val RowHeadBelow = 20.tvDp

/** `.dept-row`'s `margin-top` (`departments.css:53`): each titled row of a department, Collections' too. */
internal val DeptRowTop = 44.tvDp

/** `.grid.plates.strip`'s gap (`catalog.css:36`): a row of posters on Home and in a department. */
internal val StripGap = 14.tvDp

/** `.grid.plates.strip`'s column floor, `8.5rem` (`catalog.css:35`). */
internal val StripPlateFloor = 136.tvDp

/**
 * A plate's width in a row of posters [rowWidth] wide: `.grid.plates.strip`'s
 * `minmax(8.5rem, calc((100% - 7 * 14px) / 8))` (`catalog.css:35`), an eighth
 * of the row less its gaps — and where the floor wins, as many whole plates
 * as fit at it, each widened to fill the row. The web leaves a part of the
 * next poster at the row's end for a pointer to scroll to; on a television,
 * where the user asked for it, a row ends on a whole poster instead. Each
 * row measures itself and asks this; a row with no bound (inside a sideways
 * scroll) takes the floor.
 */
internal fun stripPlateWidth(rowWidth: Dp): Dp = wholePlates(rowWidth, StripPlateFloor, columns = 8)

/** A row of stills' floor: half again the poster floor, 204 web px, so five still fit the box's row. */
internal val LandscapeStripPlateFloor = StripPlateFloor * 1.5f

/** A still's width in a row [rowWidth] wide: a fifth of the row less its gaps, or as many whole stills as fit at [LandscapeStripPlateFloor]; an unbounded row takes the floor. */
internal fun landscapeStripPlateWidth(rowWidth: Dp): Dp = wholePlates(rowWidth, LandscapeStripPlateFloor, columns = 5)

/** [columns] plates across [rowWidth] less their gaps, or as many whole ones as fit at [floor], widened to fill it. */
private fun wholePlates(
    rowWidth: Dp,
    floor: Dp,
    columns: Int,
): Dp {
    if (!rowWidth.isFinite) return floor
    val fit = ((rowWidth + StripGap) / (floor + StripGap)).toInt().coerceIn(1, columns)
    return (rowWidth - StripGap * (fit - 1)) / fit
}

/** `.resume-strip`'s gap (`home.css:218`): Continue's cards. */
internal val ResumeGap = 16.tvDp

/** `.grid.plates`' gap, 36 between lines and 22 across (`catalog.css:30`). */
internal val WallLineGap = 36.tvDp
internal val WallPlateGap = 22.tvDp

/** `.genre-tiles`' gap (`catalog.css:139`): a row of art tiles, or a wall of them (one with fewer columns than plates). */
internal val TileGap = 14.tvDp

/** `.shelf-head`'s `padding-bottom`, title to rule, and `margin-bottom`, rule to what follows (`catalog.css:11`). */
internal val ShelfHeadRuleTop = 20.tvDp
internal val ShelfHeadBelow = 36.tvDp

/** `.shelf-sub`'s margins (`catalog.css:98`): a wall's section heading ("Movies", "Series", "Films"). */
internal val ShelfSubAbove = 40.tvDp
internal val ShelfSubBelow = 20.tvDp

/** `.spread`'s padding, 48 under the bar and 56 under its words (`title-page.css:58`): a film's or a show's opening spread. */
internal val SpreadWordsTop = 48.tvDp
internal val SpreadBelow = 56.tvDp

/** `.spread-title` 18, `.spread-facts` 16 and `.spread-overview` 28 below each (`title-page.css:88,94,98`). */
internal val SpreadTitleBelow = 18.tvDp
internal val SpreadFactsBelow = 16.tvDp
internal val SpreadOverviewBelow = 28.tvDp

/** `.tabs`' `margin-top` 8 and `.tab-panel`'s `padding-top` 36 (`title-page.css:119,133`): spread to tab row, tab row to its panel. */
internal val TabsTop = 8.tvDp
internal val TabPanelTop = 36.tvDp

/** `.season-pick select`'s `margin-bottom` (`title-page.css:146`): a show's season picker to its episodes. */
internal val SeasonPickBelow = 20.tvDp

/** `.season h2`'s `margin-bottom` (`catalog.css:71`): a season's or a course part's heading to its first row. */
internal val SeasonHeadBelow = 12.tvDp
