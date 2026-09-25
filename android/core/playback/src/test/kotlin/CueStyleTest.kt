package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from the web's `subtitle-style.test.ts` `cueStyle` cases, adapted to this app's `CueAppearance` shape rather than a CSS rule string. */
class CueStyleTest {

    // -- size --

    @Test
    fun statesTheSizeAsAPercentageOfTheBrowsersOwn() {
        assertEquals(1.4f, cueAppearance(140, "shadow").fontScale)
    }

    @Test
    fun aSizeNobodyCouldReadIsRefused() {
        assertEquals(0.5f, cueAppearance(5, "shadow").fontScale)
        assertEquals(2.0f, cueAppearance(9_000, "shadow").fontScale)
    }

    @Test
    fun aMissingSizeIsTheBrowsersOwn() {
        assertEquals(1.0f, cueAppearance(null, "shadow").fontScale)
    }

    // -- backing --

    @Test
    fun aBoxDrawsASolidScrim() {
        assertEquals(0.75f, cueAppearance(100, "box").backgroundAlpha)
    }

    @Test
    fun noneAndAMissingBackingDrawNoScrim() {
        assertEquals(0f, cueAppearance(100, "none").backgroundAlpha)
        assertEquals(0f, cueAppearance(100, null).backgroundAlpha)
    }

    @Test
    fun onlyAShadowBackingCarriesAShadow() {
        assertTrue(cueAppearance(100, "shadow").hasShadow)
        assertFalse(cueAppearance(100, "box").hasShadow)
        assertFalse(cueAppearance(100, "none").hasShadow)
    }

    // -- what the sheet offers, and what a corrupt preference falls back to --

    @Test
    fun theStoredPercentOrTheDefaultForAnythingElse() {
        assertEquals(80, cueSizePercentOrDefault("80"))
        assertEquals(DEFAULT_CUE_SIZE_PERCENT, cueSizePercentOrDefault("enormous"))
        assertEquals(DEFAULT_CUE_SIZE_PERCENT, cueSizePercentOrDefault(null))
        assertEquals(DEFAULT_CUE_SIZE_PERCENT, cueSizePercentOrDefault("140")) // not one of the four the sheet offers
    }

    @Test
    fun theStoredBackingOrTheDefaultForAnythingElse() {
        assertEquals("box", cueBackingOrDefault("box"))
        assertEquals(DEFAULT_CUE_BACKING, cueBackingOrDefault("glow"))
        assertEquals(DEFAULT_CUE_BACKING, cueBackingOrDefault(null))
    }

    @Test
    fun theFourSizesAndThreeBackingsMatchTheWeb() {
        assertEquals(listOf(80, 100, 115, 135), CUE_SIZES.map { it.percent })
        assertEquals(listOf("Small", "Normal", "Large", "Larger"), CUE_SIZES.map { it.label })
        assertEquals(listOf("shadow", "box", "none"), CUE_BACKINGS.map { it.stored })
        assertEquals(listOf("Shadow", "Box", "None"), CUE_BACKINGS.map { it.label })
    }
}
