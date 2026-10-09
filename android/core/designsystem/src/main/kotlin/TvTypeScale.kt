package designsystem

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * How a television sizes text, by the two rules the web player sets it by.
 *
 * Text reads the web's CSS at a 960px window — the box's own width in dp —
 * with each px taken as one sp. That is the ten-foot adaptation: on a set
 * read from the couch, a dp subtends about what a CSS px does on a desktop
 * monitor at arm's length, so text lands at the web's desktop size. Those
 * are [CatalogueType]'s roles and [Eyebrow], [SectionHead], [LedgerLabel],
 * [LedgerValue] unchanged, since the phone already carries the web's
 * numbers; no text drops under [Floor].
 *
 * Display type — the titles the web sizes with `vw` clamps — takes the
 * web's 1920px size halved instead. At a 960px window those clamps run near
 * their ceilings and a department title stood 12% of the screen high,
 * against 8% on a desktop; halving keeps the desktop's proportions.
 */
object TvTypeScale {
    /** Under this, nothing: tv-material's smallest role, which lifts the web's 10px counts and tallies (`shell.css:60,66`). */
    val Floor: TextUnit = 11.sp

    /** `.dept-title` (`departments.css:32`), also the Settings and System page title (`settings-page.js:55`). */
    val DepartmentTitle: TextUnit = 60.sp

    /** `.franchise-hero .dept-title` (`departments.css:108`). */
    val FranchiseTitle: TextUnit = 40.sp

    /** `.cover-title` (`home.css:50`). */
    val CoverTitle: TextUnit = 56.sp

    /** `.cover-title.long` (`home.css:56`). */
    val CoverTitleLong: TextUnit = 36.8.sp

    /** `.spread-title` (`title-page.css:89`). */
    val SpreadTitle: TextUnit = 46.sp

    /** `.spread-title.long` (`title-page.css:93`). */
    val SpreadTitleLong: TextUnit = 34.sp

    /** `.shelf-head h1` (`catalog.css:7`). */
    val ShelfHead: TextUnit = 28.8.sp
}
