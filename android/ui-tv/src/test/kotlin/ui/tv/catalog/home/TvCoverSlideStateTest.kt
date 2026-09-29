package ui.tv.catalog.home

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import model.Kind
import model.MediaSet
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import ui.tv.catalog.TvBarClearance
import ui.tv.catalog.TvScreenStateTest
import kotlin.test.assertTrue

/**
 * A real regression: [TvCoverSlide]'s own words column is
 * `Alignment.BottomStart` inside a `Box` whose height only ever floors at
 * [TvCoverSlide]'s own `minHeight` — content taller than that floor grows
 * the box to fit it, and without a top padding of its own the words then
 * start at that (taller) box's own top edge, squarely where the departments
 * bar draws over Home's own cover. A floor far smaller than any real
 * content forces that overflow deterministically here, rather than relying
 * on a long title or tagline actually wrapping under whatever font metrics
 * this test happens to run with (Fraunces has a documented history of
 * rendering bigger on a real device than in this harness) — [TvBarClearance]'s
 * own top padding is what still keeps the title clear of the bar either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCoverSlideStateTest : TvScreenStateTest() {
    @Test
    fun theTitleNeverStartsAboveItsOwnBarClearanceEvenWhenTheFloorIsFarTooSmall() {
        val set =
            film("film-0", "The Green Knight").copy(
                backdropPath = "/bd0",
                tagline = "A great king's nephew embarks on a perilous quest to confront a mysterious giant knight.",
            )
        show {
            // A floor of 1dp: far smaller than any real content, so the
            // words column's own height — not this floor — decides the
            // box's final size, the exact "overflow" shape the bar
            // collision needs, regardless of this run's own font metrics.
            TvCoverSlide(set = set, minHeight = 1.dp)
        }

        val title = compose.onNodeWithText("THE GREEN KNIGHT").fetchSemanticsNode()
        val clearancePx = with(compose.density) { TvBarClearance.toPx() }
        assertTrue(
            title.boundsInRoot.top >= clearancePx - 1f,
            "title starts at ${title.boundsInRoot.top}px, short of its own ${clearancePx}px clearance",
        )
    }

    private fun film(
        id: String,
        title: String,
    ) = MediaSet(
        setId = id,
        kind = Kind.MOVIE,
        title = title,
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
    )
}
