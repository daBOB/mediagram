package ui.tv.catalog

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
