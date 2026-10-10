package designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The television reads the web as a 1280px viewport on its 960dp, one CSS px
 * to [TvScale] sp. The roles are the phone's ([CatalogueType] already holds
 * the web's numbers) through that scale, with no floor under them; display
 * type takes the web's 1920px size halved, through the same scale.
 */
class TvTypeScaleTest {
    @Test
    fun theScaleIsA1280ViewportOnA960Screen() {
        assertEquals(0.75f, TvScale)
        assertEquals(7.5f, 10.webSp.value, "`.n`, 0.625rem: no floor lifts it")
        assertEquals(30.72f, 40.96.webDp.value, 0.001f, "`--gutter`, 3.2vw at 1280px")
    }

    @Test
    fun theRolesAreTheWebsPxThroughTheScale() {
        // `.departments a`, `.rail-nav a`, `.person-name`: 0.875rem (shell.css:47, title-page.css:182).
        assertEquals(14 * TvScale, TvType.labelLarge.fontSize.value)
        // `.pill`, `.tab`, `.fact dd`, `.row .title b`: 0.9375rem (title-page.css:14,127,141; catalog.css:81).
        assertEquals(15 * TvScale, TvType.bodyMedium.fontSize.value)
        // `.cover-meta`, `.row .meta`, `.see-all`: 0.8125rem (home.css:65,203; catalog.css:84).
        assertEquals(13 * TvScale, TvType.bodySmall.fontSize.value)
        // `.spread-overview`, `.cast-crew`: Newsreader 1.0625rem (title-page.css:101,174).
        assertEquals(17 * TvScale, TvType.bodyLarge.fontSize.value)
        assertEquals(Read, TvType.bodyLarge.fontFamily)
        // `.eyebrow`: 0.6875rem (theme.css:200).
        assertEquals(11 * TvScale, TvType.Eyebrow.fontSize.value)
        // `.setting-title`: Geist 600 1rem (appearance.css:31).
        assertEquals(16 * TvScale, TvType.SectionHead.fontSize.value)
    }

    @Test
    fun aRolesLineHeightShrinksWithItsSize() {
        assertEquals(CatalogueType.bodyMedium.lineHeight.value * TvScale, TvType.bodyMedium.lineHeight.value)
        assertEquals(Eyebrow.lineHeight, TvType.Eyebrow.lineHeight, "an `em` line height already follows the size")
    }

    @Test
    fun displaySizesAreTheWebsAt1920HalvedThroughTheScale() {
        assertEquals(120f / 2 * TvScale, TvTypeScale.DepartmentTitle.value) // .dept-title 7.5rem (departments.css:32)
        assertEquals(80f / 2 * TvScale, TvTypeScale.FranchiseTitle.value) // .franchise-hero .dept-title 5rem (departments.css:108)
        assertEquals(112f / 2 * TvScale, TvTypeScale.CoverTitle.value) // .cover-title 7rem (home.css:50)
        assertEquals(73.6f / 2 * TvScale, TvTypeScale.CoverTitleLong.value, 0.001f) // .cover-title.long 4.6rem (home.css:56)
        assertEquals(92f / 2 * TvScale, TvTypeScale.SpreadTitle.value) // .spread-title 5.75rem (title-page.css:89)
        assertEquals(68f / 2 * TvScale, TvTypeScale.SpreadTitleLong.value) // .spread-title.long 4.25rem (title-page.css:93)
        assertEquals(57.6f / 2 * TvScale, TvTypeScale.ShelfHead.value, 0.001f) // .shelf-head h1 3.6rem (catalog.css:7)
    }
}
