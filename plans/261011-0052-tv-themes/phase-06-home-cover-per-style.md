# Phase 06 — Home cover per style

## Context
- Spec: § Home cover (with the planning correction: Apple's cover is today's slide with its own title rule).
- `ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeCover.kt` (128 lines): rotation (`HOLD_MS = 9_000L`, `CROSSFADE_MS = 1_400`), focus pause, `Crossfade { TvCoverSlide(…) }`, then `TvCoverActions` outside the fade, aligned bottom-start.
- `home/TvCoverSlide.kt` (145): backdrop (`HeroArtwork`, hidden under `Backdrop.SOLID`), `CoverScrim`, and one text column — kicker "Featured today · genre", the title uppercased in `CoverTitle`, tagline, facts line.
- Copy is unchanged in every style. Uppercasing is mediagram's typography, so Apple and Google set the title as written.

## Task 6.1: A bold sans title for Apple, an inset card for Google

**Files:**
- Modify: `ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCoverSlide.kt`, `home/TvHomeCover.kt:105-121`
- Create: `ui-tv/src/main/kotlin/ui/tv/catalog/home/TvInsetCardSlide.kt`
- Test: `ui-tv/src/test/kotlin/ui/tv/catalog/home/TvCoverStylesTest.kt` (create)

**Interfaces:**
- Consumes: `LocalTvStyle` (1.1), `inTvStyle` (2.2), `TvFocus.pillColors` (3.1, already used by the actions).
- Produces: `internal fun TvCoverWords(set: MediaSet, title: String, titleStyle: TextStyle, modifier: Modifier)`; `@Composable @ReadOnlyComposable internal fun sansCoverTitle(long: Boolean): TextStyle`; `internal fun TvInsetCardSlide(set: MediaSet, minHeight: Dp)`; `internal const val InsetCardTag`; `internal val InsetCardPadding`, `internal val InsetCardMargin`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.tv.catalog.home

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.set

/** The cover story per style: mediagram's capitals, Apple's bold title as written, Google's inset card — the same words and buttons in each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCoverStylesTest : TvScreenStateTest() {
    private val film = set("f1", Kind.MOVIE, "Die Hard", addedAt = 1)
    private val second = set("f2", Kind.MOVIE, "Heat", addedAt = 2)

    @Test
    fun mediagramSetsTheTitleInCapitals() {
        show { TvCoverSlide(set = film, minHeight = 400.dp) }
        compose.onNodeWithText("DIE HARD").assertExists()
    }

    @Test
    fun appleSetsTheTitleAsWrittenInBold() {
        show(TvStyle.APPLE) { TvCoverSlide(set = film, minHeight = 400.dp) }
        compose.onNodeWithText("DIE HARD").assertDoesNotExist()
        assertEquals(FontWeight.Bold, styleOf("Die Hard").fontWeight)
    }

    @Test
    fun googleDrawsTheStoryAndItsButtonsInsideAnInsetCard() {
        show(TvStyle.GOOGLE) { cover(listOf(film)) }
        val card = compose.onNodeWithTag(InsetCardTag).getUnclippedBoundsInRoot()
        val watch = compose.onNodeWithContentDescription("Watch now: Die Hard").getUnclippedBoundsInRoot()
        assertTrue(card.left > 0.dp, "inset from the screen's edge: $card")
        assertTrue(watch.left >= card.left + InsetCardPadding && watch.bottom <= card.bottom, "the buttons sit inside the card: $watch in $card")
        assertEquals(FontWeight.Bold, styleOf("Die Hard").fontWeight)
    }

    @Test
    fun googlesCoverStillTurnsToTheNextFilm() {
        compose.mainClock.autoAdvance = false
        show(TvStyle.GOOGLE) { cover(listOf(film, second)) }
        compose.mainClock.advanceTimeBy(9_000 + 1_400 + 100)
        compose.onNodeWithText("Heat").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun cover(films: List<model.MediaSet>) =
        TvHomeCover(
            films = films,
            watchlist = emptySet(),
            initialFilmId = null,
            onPlay = {},
            onOpenTitle = {},
            onToggleWatchlist = { _, _ -> },
            arrivalFocus = remember { FocusRequester() },
            upExit = remember { FocusRequester() },
        )
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.catalog.home.TvCoverStylesTest'`
Expected: compilation fails — `InsetCardTag`, `InsetCardPadding` are unresolved.

- [ ] **Step 3: Share the words; give Apple its title**

In `TvCoverSlide.kt`, move the text `Column`'s four children into:

```kotlin
/**
 * A cover story's words — kicker, title, tagline, facts — at the web's
 * spacing (`home.css:47,59,64`), shared by every style's slide so the
 * story reads the same in each; [title] and [titleStyle] are the only part a
 * style sets for itself.
 */
@Composable
internal fun TvCoverWords(
    set: MediaSet,
    title: String,
    titleStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        …the kicker Text, unchanged…
        Text(text = title, style = titleStyle, color = OnImage, maxLines = 2, overflow = TextOverflow.Ellipsis)
        …the tagline and facts Texts, unchanged…
    }
}

/**
 * The cover title in a sans style: the film's name as written, bold and
 * tight — the wordmark those home screens set over their picture, drawn
 * as text since the library holds no logo art. [long] steps it down as
 * mediagram's does.
 */
@Composable
@ReadOnlyComposable
internal fun sansCoverTitle(long: Boolean): TextStyle =
    CoverTitle.inTvStyle().copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em,
        fontSize = if (long) TvTypeScale.CoverTitleLong else TvTypeScale.CoverTitle,
        lineHeight = if (long) 0.95.em else 1.0.em,
    )
```

and `TvCoverSlide`'s `Column(modifier = …) { … }` becomes

```kotlin
        val long = set.title.length > LONG_TITLE
        val capitals = LocalTvStyle.current == TvStyle.MEDIAGRAM
        TvCoverWords(
            set = set,
            title = if (capitals) set.title.uppercase() else set.title,
            titleStyle =
                if (capitals) {
                    CoverTitle.inTvStyle().copy(fontSize = if (long) TvTypeScale.CoverTitleLong else TvTypeScale.CoverTitle, lineHeight = if (long) 0.9.em else CoverTitle.lineHeight)
                } else {
                    sansCoverTitle(long)
                },
            modifier = …the Column's existing modifier chain, unchanged…,
        )
```

so mediagram composes the same `Column` with the same children and modifiers as before. Add to `TvCoverSlide`'s KDoc: "Apple TV style draws this same slide with the title as written, in [sansCoverTitle]."

- [ ] **Step 4: Google's inset card**

Create `TvInsetCardSlide.kt`:

```kotlin
package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Spacing
import model.MediaSet
import ui.common.catalog.HeroArtwork
import ui.tv.catalog.TvBarClearance
import ui.tv.chrome.LocalTvPagePadding

/**
 * Google TV style's cover: the film's backdrop in a card inset from the
 * page's gutters with large rounded corners, its words at the card's foot
 * over a shade that darkens toward the start — that home screen's spotlight
 * card. The same words as every style's slide ([TvCoverWords]); the picture
 * goes under [Backdrop.SOLID] as mediagram's does. [TvHomeCover] draws the
 * buttons inside the card, padded by [InsetCardPadding].
 */
@Composable
internal fun TvInsetCardSlide(
    set: MediaSet,
    minHeight: Dp,
) {
    val page = LocalTvPagePadding.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(start = page.start, end = page.end, top = TvBarClearance, bottom = InsetCardMargin),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .testTag(InsetCardTag)
                    .clip(InsetCardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val art = set.backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
            if (art != null) HeroArtwork(path = art, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to InsetShade, 0.65f to Color.Transparent)))
        }
        TvCoverWords(
            set = set,
            title = set.title,
            titleStyle = sansCoverTitle(long = true),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = InsetTextMaxWidth)
                    .padding(start = InsetCardPadding, end = InsetCardPadding, top = InsetCardPadding, bottom = CoverActionsReservedHeight),
        )
    }
}

/** Finds the card in a test; also a resource id on the box, through `TvShell`'s `testTagsAsResourceId`. */
internal const val InsetCardTag = "inset-cover-card"

/** The card's words and buttons stand this far in from its own edge. */
internal val InsetCardPadding = Spacing.extraLarge

/** Room under the card, so the row below starts clear of its rounded foot. */
internal val InsetCardMargin = Spacing.large

private val InsetCardShape = RoundedCornerShape(24.dp)
private val InsetShade = Color(0xE6000000)
private val InsetTextMaxWidth = 560.dp
```

(`TvBarClearance` is `internal` in `ui.tv.catalog`, as `TvCoverSlide` already imports it. The title is always the long size: the card is narrower than mediagram's full-width cover.)

In `TvHomeCover.kt`:

```kotlin
        val inset = LocalTvStyle.current == TvStyle.GOOGLE
        Crossfade(targetState = current, animationSpec = tween(CROSSFADE_MS), label = "tvHomeCover") { set ->
            if (inset) TvInsetCardSlide(set = set, minHeight = coverHeight) else TvCoverSlide(set = set, minHeight = coverHeight)
        }
        TvCoverActions(
            …unchanged arguments…,
            // Inside Google TV's card, at its words' own inset.
            modifier = Modifier.align(Alignment.BottomStart).then(if (inset) Modifier.padding(start = InsetCardPadding, bottom = InsetCardMargin) else Modifier),
        )
```

(imports `designsystem.LocalTvStyle`, `designsystem.TvStyle`, `androidx.compose.foundation.layout.padding`.) Add to its KDoc: "Google TV style draws its slide as an inset card ([TvInsetCardSlide]) and the buttons inside it; the rotation, the pause and the buttons are the same in every style."

- [ ] **Step 5: Run the ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS — the new test, and `TvCoverSlideStateTest`, `TvHomeStateTest`, `TvHomeReturnTest`, `TvPageSpacingTest` unchanged.

- [ ] **Step 6: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): the Home cover in each TV style's own shape"
```

## Success criteria
- mediagram's cover composes exactly as before (capitals, Fraunces, full width).
- Apple: same full-bleed slide, title as written in Geist bold.
- Google: an inset rounded card holding the words and the buttons; the cover still turns every 9 s and pauses under focus.
