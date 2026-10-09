package designsystem

import androidx.compose.ui.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The television's two type rules. Text reads the web's CSS at a 960px
 * window, one px to one sp — the phone's own roles, since [CatalogueType]
 * already holds those numbers — and never under [TvTypeScale.Floor].
 * Display type takes the web's 1920px sizes halved, so a department title
 * fills as much of the screen as it does on a desktop.
 */
class TvTypeScaleTest {
    @Test
    fun theRolesAreTheWebsSizesAtA960Window() {
        // `.departments a`, `.rail-nav a`, `.person-name`: 0.875rem (shell.css:47, title-page.css:182).
        assertEquals(14f, CatalogueType.labelLarge.fontSize.value)
        // `.pill`, `.tab`, `.fact dd`, `.row .title b`: 0.9375rem (title-page.css:14,127,141; catalog.css:81).
        assertEquals(15f, CatalogueType.bodyMedium.fontSize.value)
        // `.cover-meta`, `.row .meta`, `.see-all`: 0.8125rem (home.css:65,203; catalog.css:84).
        assertEquals(13f, CatalogueType.bodySmall.fontSize.value)
        // `.spread-overview`, `.cast-crew`: Newsreader 1.0625rem (title-page.css:101,174).
        assertEquals(17f, CatalogueType.bodyLarge.fontSize.value)
        assertEquals(Read, CatalogueType.bodyLarge.fontFamily)
        // `.eyebrow`: 0.6875rem (theme.css:200).
        assertEquals(11f, Eyebrow.fontSize.value)
        // `.setting-title`: Geist 600 1rem (appearance.css:31).
        assertEquals(16f, SectionHead.fontSize.value)
    }

    @Test
    fun noRoleFallsUnderTheFloor() {
        val roles =
            with(CatalogueType) {
                listOf(headlineSmall, titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall)
            } + listOf(Eyebrow, SectionHead, LedgerLabel, LedgerValue)
        roles.forEach { role: TextStyle -> assertTrue(role.fontSize >= TvTypeScale.Floor, "$role under the floor") }
        // The web's 10px counts and tallies (shell.css:60,66) are what the floor lifts.
        assertEquals(11f, TvTypeScale.Floor.value)
    }

    @Test
    fun displaySizesAreTheWebsAt1920Halved() {
        assertEquals(120f / 2, TvTypeScale.DepartmentTitle.value) // .dept-title 7.5rem (departments.css:32)
        assertEquals(80f / 2, TvTypeScale.FranchiseTitle.value) // .franchise-hero .dept-title 5rem (departments.css:108)
        assertEquals(112f / 2, TvTypeScale.CoverTitle.value) // .cover-title 7rem (home.css:50)
        assertEquals(73.6f / 2, TvTypeScale.CoverTitleLong.value) // .cover-title.long 4.6rem (home.css:56)
        assertEquals(92f / 2, TvTypeScale.SpreadTitle.value) // .spread-title 5.75rem (title-page.css:89)
        assertEquals(68f / 2, TvTypeScale.SpreadTitleLong.value) // .spread-title.long 4.25rem (title-page.css:93)
        assertEquals(57.6f / 2, TvTypeScale.ShelfHead.value) // .shelf-head h1 3.6rem (catalog.css:7)
    }
}
