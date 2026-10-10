package ui.tv.catalog

import designsystem.webDp

/*
 * The spacing a catalogue page takes from the web, each the web's px at the
 * TV's 1280px reading through `designsystem.TvScale`, so it shrinks and grows
 * with the text beside it.
 */

/** `.features` 12 under the cover, `.home-band` 28 and `.home-section` 36 above the rest (`home.css:135,189,192`). */
internal val HomeFeaturesTop = 12.webDp
internal val HomeBandTop = 28.webDp
internal val HomeSectionTop = 36.webDp

/** `.home-band`'s column gap, `clamp(32px, 4vw, 72px)` at 1280px (`home.css:188`). */
internal val HomeBandGap = 51.2.webDp

/** `.row-head`'s `margin-bottom` (`home.css:194`): a row head to the row it names, on Home and in a department. */
internal val RowHeadBelow = 20.webDp

/** `.dept-row`'s `margin-top` (`departments.css:53`): each titled row of a department, Collections' too. */
internal val DeptRowTop = 44.webDp

/** `.grid.plates.strip`'s gap (`catalog.css:36`): a row of posters on Home and in a department. */
internal val StripGap = 14.webDp

/** `.resume-strip`'s gap (`home.css:218`): Continue's cards. */
internal val ResumeGap = 16.webDp

/** `.grid.plates`' gap, 36 between lines and 22 across (`catalog.css:30`). */
internal val WallLineGap = 36.webDp
internal val WallPlateGap = 22.webDp

/** `.genre-tiles`' gap (`catalog.css:139`): a row of art tiles, or a wall of them (one with fewer columns than plates). */
internal val TileGap = 14.webDp

/**
 * `.shelf-head`'s `padding-bottom` (`catalog.css:11`), title to rule. Its 36
 * `margin-bottom` is the wall's own line gap below the head; the 16dp under
 * the rule is the TV's room for a focused first plate's grow, not the web's.
 */
internal val ShelfHeadRuleTop = 20.webDp
