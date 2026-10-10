# Phase 04 — Cover, reel and Home details

**Spec:** `../spec.md` §2 (Home: Cover, Continue strip, Masthead blend; Featured reel), §3 (reduced motion). **Inventory:** rows #3, #4, #5, #11, #12, #13, #14, #25; §5. **Status:** pending.
**Depends on:** 01, and 02 for two things:
- `MediagramTheme` provides `LocalReducedMotion`. Without it the cover would rotate under "Remove animations", a regression against today's own read.
- 02's button rewrite: `FeaturedReel.kt` and `FeaturedSlide.kt` are shown here as 02 leaves them (`CatalogueTextButton`, `CatalogueButton`, `CatalogueOutlinedButton`).

## Context

**Web sources:**
- **Cover:** `web/public/styles/home.css:13-18,27,127-128`, `web/public/lib/catalog/home-cover.js:36-40,48,56-63`, `web/public/styles/catalog.css:136`.
- **Featured reel:** `web/public/styles/playback.css:477,489-510,585,624`, `web/public/lib/catalog/featured-reel.js:117-142,161,211`.
- **Masthead:** `web/public/styles/shell.css:230`.
- **Continue strip:** `web/public/styles/home.css:220,234`.

**Key insights (each verified against the code):**

1. **The web's cover turn.**
   - **Ground:** the cover has a near-black ground (`home.css:15`, `#0a0a0b`, both themes).
   - **Turn:** a turn replaces the stage's one child (`home-cover.js:62`, `replaceChildren`), so the old slide is gone at once. The new one fades from opacity 0 over 1400ms `--ease` (`home.css:18,127`). Its picture settles from scale 1.06 to 1 over 14s `--ease-out` (`:27,128`).
   - **When it fades:** the first slide fades on arrival too. Only a settled redraw skips both, for a slide not `.turned` (`catalog.css:136`), and a redraw with the same lineup resumes the same film (`home-cover.js:36-40`).
2. **Android today.** A `Crossfade` keyed on the `MediaSet` (`home/HomeCover.kt:164`) composes two slides for 1.4s on the default easing. Because it is keyed on the whole value, an enrichment pass that gives the shown film a new tagline or backdrop cross-fades the film into itself.
   - **New:** `key(setId)` over a slide that fades itself up, on a black ground.
   - **Which slide fades up and settles:** the arrival slide and a slide a turn brought in (timer or dot).
   - **Shown at once:** a film a refresh moves into view because its predecessor left the lineup, as the web's settled redraw does.
3. **The tap shield stays** (`HomeCover.kt:97-113,177-187`): turns only, as today. The arrival fade takes taps like any arriving page (Review Focus 3, "lands on the page under it"). Under reduced motion it is skipped: nothing fades, and today it leaves 1.4s of dead taps after a dot pick.
4. **A turn counts from the shown page, not `currentPage`.** After a lineup shrinks, `currentPage` can sit past the end while `page` is clamped (`HomeCover.kt:129`). The timer's next index (`:93`) would then be the film already showing, and it would fade up out of black again.
5. **Blurred multiplies, it doesn't override.** `HeroArtwork` puts the caller's modifier before its own `clipToBounds` and 1.12 layer (`ui-common/.../catalog/HeroArtwork.kt:55,91-94`). A drift in the caller's modifier therefore multiplies with the 1.12 rather than replacing it, which is the web quirk the inventory says not to copy (`appearance.css:22`). `HeroArtwork.kt` needs no change.
   - The clip goes at the call site, so the scaled picture never spills past the cover into the 12dp gap above the features (`.cover { overflow: hidden }`, `home.css:13`).
6. **Reduced motion.** `HomeCover` reads `LocalReducedMotion.current` for the rotation (`home-cover.js:48`).
   - `FeaturedReel`'s hand-rolled read (`FeaturedReel.kt:83,157-161`) fed only two things: the cross-fade duration, which Compose already scales to nothing (spec §3), and the drift guard, which `drift` now handles itself through `LocalReducedMotion`.
   - So it is **removed rather than replaced**. Nothing in the reel reads the setting.
7. **The reel's slides.** `Crossfade(targetState = index)` (`FeaturedReel.kt:86`) composes each slide anew, so each gets its own drift and copy rise.
   - **The web's leaving slide** drops its copy to `opacity: 0` (`playback.css:505`) and snaps its poster back to scale 1 at the turn (the drift is tied to `.on`, `:499`). That is a side effect of the stylesheet, not a decision, and the jump is visible. It is not copied: the spec says the slides cross-fade.
8. **The poster frame clips on the web** (`playback.css:495`). Android's `DriftingPoster` doesn't (`FeaturedSlide.kt:108`), so today's 1.05 already spills past the frame. A clip is added.
9. **The masthead blend.** `coverBlend` is shared with ui-tv (`ui-tv/.../TvCatalogBlend.kt:77,79`). A defaulted `reducedMotion` parameter makes the phone's bar solid and leaves the TV's call sites as they are.
10. **Every web `.resume-strip` snaps:** Home (`home-view.js:79`) and the department pages (`department-pages.js:110,160`, `anime-department.js:54`). So both `home/ResumeCard.kt` `ResumeRow` and the departments' `ResumeStrip.kt` snap. The inventory named only the first.
11. **Test harness.** A 14s drift outlasts the 9s hold. An auto-advancing test clock therefore keeps turning the cover for ever (each new slide drifts again), so `waitForIdle()` never returns. `HomeCoverTest`'s three-film test holds the rotation through reduced motion, and the new tests pause the clock. The same holds for any test that opens the Featured reel (8.5s drift, 7s hold).

**Rulings:**
- **Which slides fade:** insight 2.
- **Shield under reduced motion:** skipped (insight 3).
- **Reel's reduced-motion read:** removed rather than replaced (insight 6).
- **Department strips:** snap too (insight 10).
- **The web's leaving-slide jump:** not copied, reported (insight 7).

**Tests:** Robolectric with a paused `mainClock`. Scale and pan are read from layer-mapped bounds. Both pictures are clipped by their frames, so a drift shows as the gap between the clipped and the unclipped left edge. Alpha is drawn and can't be captured here (`ui-mobile/src/test/kotlin/ui/PageGroundColorTest.kt`), so "fades up out of black" is the phase 09 walk's; the old slide being gone at once is what is tested.

## Task 4.1: The cover turns the web's way

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/HomeCover.kt` (whole file)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/CoverSlide.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/HomeType.kt` (receives `OnImage`, `OnImage2`, `CoverGround`)
- Modify: `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTest.kt` (the lineup test holds the rotation)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTurnTest.kt`

**Interfaces:**
- **Consumes:** `Modifier.entrance`, `Modifier.drift`, `Motion.EaseOut` and `LocalReducedMotion` (phase 01, provided by the theme in 02).
- **Produces:**
  - `CoverSlide(…, turning: Boolean, …)`;
  - `internal const val COVER_FADE_MS`, renamed from `CROSSFADE_MS`, which had no other user;
  - `internal val CoverGround`, which moves to `HomeType.kt` with `OnImage` and `OnImage2`. Those two stay in package `ui.catalog.home`, so their importers (`ArtTile.kt:34`, `DeptHeroText.kt:33-34`, `WideTitleSpread.kt:33`, `CoverControls.kt`) don't change.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTurnTest.kt`:

```kotlin
package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.LocalReducedMotion
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.catalog.HERO_ARTWORK_TEST_TAG

/**
 * The cover turns the web's way (`home.css:15-28`, `home-cover.js:56-63`):
 * the old slide is gone at once, the new one comes up out of the black
 * ground, and its picture settles from 1.06 to its own size over 14s. A tap
 * while it comes up lands on nothing; a film a refresh moves into view is
 * simply there.
 *
 * The settle is read from the picture's unclipped bounds: the cover clips its
 * picture, as the web's `overflow: hidden` does, so the clipped bounds never
 * grow, while the mapped left edge sits left of the cover's own (0) while the
 * picture is scaled up. The fade is drawn, and nothing here captures pixels
 * (see PageGroundColorTest), so "out of black" is the device walk's; the old
 * slide being gone at once is what is checked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class HomeCoverTurnTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String, tagline: String? = null) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    private fun show(
        films: MutableState<List<MediaSet>>,
        backdrop: Backdrop = Backdrop.DEFAULT,
        reducedMotion: Boolean = false,
        onPlay: (MediaSet) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalBackdrop provides backdrop, LocalReducedMotion provides reducedMotion) {
                        HomeCover(
                            films = films.value, watchlist = emptySet(), width = 1164.dp,
                            onPlay = onPlay, onOpenTitle = {}, onToggleWatchlist = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun artLeft() = compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    @Test
    fun aTurnRemovesTheOldSlideAtOnce() {
        show(mutableStateOf(listOf(film("a"), film("b"), film("c"))))
        compose.onNodeWithContentDescription("Cover story 2 of 3").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("FILM A").assertDoesNotExist()
        compose.onNodeWithText("FILM B").assertExists()
    }

    /** Review Focus 3: mid-fade, a press lands on nothing rather than on a control still coming up out of the dark. */
    @Test
    fun aTapWhileTheNewFilmComesUpLandsOnNothing() {
        var played: String? = null
        show(mutableStateOf(listOf(film("a"), film("b"))), onPlay = { played = it.setId })
        compose.onNodeWithContentDescription("Cover story 2 of 2").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertNull(played)
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertEquals("b", played)
    }

    @Test
    fun theFirstSlideSettlesFromCloseUpAndASyncLeavesItSettled() {
        val films = mutableStateOf(listOf(film("a", tagline = "First line.")))
        show(films)
        assertTrue(artLeft() < -20f, "the picture should start scaled up past the cover's edge, left was ${artLeft()}")
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(0f, artLeft(), 0.5f)
        // Review Focus 2: the same film with new details redraws in place,
        // neither fading up nor settling again.
        compose.runOnUiThread { films.value = listOf(film("a", tagline = "A new line.")) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("A new line.").assertExists()
        assertEquals(0f, artLeft(), 0.5f)
    }

    @Test
    fun underBlurredThePictureStillSettles() {
        show(mutableStateOf(listOf(film("a"))), backdrop = Backdrop.BLURRED)
        assertTrue(artLeft() < -20f, "Blurred's own 1.12 must not replace the settle, left was ${artLeft()}")
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(0f, artLeft(), 0.5f)
    }

    /** A cover film watched on another device leaves the lineup: the one moved into view is there at once, with no fade and no shield. */
    @Test
    fun aFilmARefreshMovesIntoViewShowsAtOnce() {
        var played: String? = null
        val films = mutableStateOf(listOf(film("a"), film("b"), film("c")))
        show(films, onPlay = { played = it.setId })
        compose.onNodeWithContentDescription("Cover story 3 of 3").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { films.value = films.value.take(2) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("FILM B").assertExists()
        assertEquals(0f, artLeft(), 0.5f)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertEquals("b", played)
    }

    @Test
    fun underReducedMotionTheCoverNeverTurns() {
        show(mutableStateOf(listOf(film("a"), film("b"))), reducedMotion = true)
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("FILM A").assertExists()
        compose.onNodeWithText("FILM B").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.home.HomeCoverTurnTest'`
Expected: 5 FAIL, 1 PASS.

| Test | Why |
|---|---|
| `aTurnRemovesTheOldSlideAtOnce` | FAIL: the `Crossfade` still holds Film A |
| `theFirstSlideSettlesFromCloseUpAndASyncLeavesItSettled` | FAIL: no settle, left is 0 |
| `underBlurredThePictureStillSettles` | FAIL: as above |
| `aFilmARefreshMovesIntoViewShowsAtOnce` | FAIL: two slides, so two artwork nodes |
| `underReducedMotionTheCoverNeverTurns` | FAIL: Robolectric's duration scale is 1, so the old read rotates |
| `aTapWhileTheNewFilmComesUpLandsOnNothing` | PASS: today's shield; the new code must keep it |

- [ ] **Step 3: Move the cover's fixed colours into `HomeType.kt`**

In `ui-mobile/src/main/kotlin/ui/catalog/home/HomeType.kt`, add the import `androidx.compose.ui.graphics.Color` and append:

```kotlin
/** Type set over artwork keeps the same two colours in both themes — the web's `--on-image`/`--on-image-2` (`theme.css:99-100`); shared with the pills and pager in `CoverControls.kt`. */
internal val OnImage = Color(0xFFF6F2EA)
internal val OnImage2 = Color(0xD1F6F2EA)

/** What a cover slide comes up out of: the web's `.cover` ground, near-black in both themes (`home.css:15`). */
internal val CoverGround = Color(0xFF0A0A0B)
```

- [ ] **Step 4: Replace `ui-mobile/src/main/kotlin/ui/catalog/home/HomeCover.kt`**

The whole file, at 196 lines (it was 203):

```kotlin
package ui.catalog.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.LocalReducedMotion
import kotlinx.coroutines.delay
import model.MediaSet

/** The cover's own outer bounds, for a test to check what sits under it never overlaps them. */
internal const val HOME_COVER_TEST_TAG = "home-cover"

/** How long one cover story holds before advancing — the web's own `HOLD_MS` (`home-cover.js:20`). */
private const val HOLD_MS = 9_000L

/** How long a slide takes to come up out of the ground — the web's own `cover-fade` (`home.css:18`, `1400ms`). */
internal const val COVER_FADE_MS = 1_400

/**
 * The cover story: one film at a time, full bleed under the chrome, its
 * backdrop behind a headline set at a magazine cover's own size — a Compose
 * port of `home-cover.js`.
 *
 * [width] is the window's own width, not this composable's own column (a
 * `BoxWithConstraints` below reads that for itself) — the web's clamps are
 * all `vw`-relative, the same value the departments bar's own gutter reads.
 */
@Composable
internal fun HomeCover(
    films: List<MediaSet>,
    watchlist: Set<String>,
    width: Dp,
    onPlay: (MediaSet) -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatchlist: (String, Boolean) -> Unit,
) {
    if (films.isEmpty()) return
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    // The web holds rotation on pointerenter/focusin (`home-cover.js:77-80`);
    // a TalkBack/switch-access user relies on touch exploration instead of a
    // resting finger, so rotation never starts for one at all.
    val touchExplorationEnabled =
        remember {
            (context.getSystemService(AccessibilityManager::class.java))?.isTouchExplorationEnabled == true
        }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var pointerDown by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val rotates = films.size > 1 && !reducedMotion && !touchExplorationEnabled
    val page = currentPage.coerceIn(0, films.lastIndex)

    // Which film the last turn brought in, by the timer or a dot. Only it and
    // the film the page arrived on come up out of the ground and settle; one a
    // refresh moves into view, its predecessor gone from the lineup, is simply
    // there, as on the web's settled redraw (`catalog.css:136`). Counted from
    // the page shown, so a shrunk lineup never "turns" to the film it shows.
    var turnedTo by remember { mutableStateOf<String?>(null) }
    val arrivedOn = remember { films[page].setId }
    val turn = { next: Int ->
        if (next != page) {
            turnedTo = films[next].setId
            currentPage = next
        }
    }

    if (rotates) {
        LaunchedEffect(page, paused, pointerDown, hasFocus, films) {
            if (paused || pointerDown || hasFocus) return@LaunchedEffect
            delay(HOLD_MS)
            turn((page + 1) % films.size)
        }
    }

    // A turn swallows taps while its film is still coming up out of the dark,
    // so a press lands on nothing rather than on a control the viewer can
    // barely see. The first film comes up as the page arrives and takes taps,
    // as any arriving page does; under reduced motion nothing fades at all.
    var fading by remember { mutableStateOf(false) }
    LaunchedEffect(turnedTo) {
        if (turnedTo == null || reducedMotion) return@LaunchedEffect
        fading = true
        delay(COVER_FADE_MS.toLong())
        fading = false
    }

    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    val compact = width <= CompactBreakpoint
    // A minimum, not an exact height — `CoverSlide`'s own root `Box` is
    // what enforces this floor (the `heightIn(min=)` + `matchParentSize()`
    // pattern `HomeFeatures.kt` already uses, see its own note); neither
    // this `BoxWithConstraints` nor the keyed slide below carry a size
    // modifier of their own, so both just wrap whatever height `CoverSlide`
    // resolves to. Nothing here ever asks a child to `fillMaxSize()` under
    // this `LazyColumn` item's own unbounded height — the trap that once
    // collapsed a `HorizontalPager` here — and a short window (split
    // screen) can grow the text past the floor instead of clipping it.
    val coverMinHeight =
        if (compact) (windowHeight.value * 0.84f).dp else fluid(620f, 0.635f, 705f, windowHeight.value).dp

    BoxWithConstraints(
        modifier =
            Modifier
                .testTag(HOME_COVER_TEST_TAG)
                .fillMaxWidth()
                .background(CoverGround)
                .focusGroup()
                .onFocusChanged { hasFocus = it.hasFocus }
                // `Initial`, not `Main`: sees every down/up even when a
                // pill or "Details" consumes its own tap, since any finger
                // resting on the cover holds the rotation, not just one on
                // empty space.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        pointerDown = true
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        pointerDown = false
                    }
                },
    ) {
        val columnWidth = maxWidth
        val set = films[page]
        // The old slide goes at once and the new one comes up out of the black
        // ground (`home.css:15,18`, `home-cover.js:62`), so two films never
        // share the screen. Keyed by id, so a refresh handing over the same
        // film with new details redraws it in place rather than turning.
        key(set.setId) {
            CoverSlide(
                set = set,
                width = width,
                columnWidth = columnWidth,
                compact = compact,
                minHeight = coverMinHeight,
                watchlisted = set.setId in watchlist,
                turning = set.setId == turnedTo || set.setId == arrivedOn,
                onPlay = { onPlay(set) },
                onDetails = { onOpenTitle(set.setId) },
                onToggleWatchlist = { listed -> onToggleWatchlist(set.setId, listed) },
            )
        }
        if (fading) {
            // Consumes every tap without doing anything with it — no press
            // tint, no callback — so a press mid-fade lands on nothing.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            )
        }
        if (films.size > 1) {
            CoverPager(
                count = films.size,
                current = page,
                rotates = rotates,
                paused = paused,
                onTogglePause = { paused = !paused },
                onPick = turn,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = gutterFor(width), bottom = if (compact) 40.dp else 18.dp),
            )
        }
    }
}
```

The dropped imports are `android.provider.Settings`, `androidx.compose.animation.Crossfade`, `androidx.compose.animation.core.tween` and `androidx.compose.ui.graphics.Color`.

- [ ] **Step 5: The slide comes up and settles, in `CoverSlide.kt`**

In `ui-mobile/src/main/kotlin/ui/catalog/home/CoverSlide.kt`:

**a.** Add the imports `androidx.compose.ui.draw.clipToBounds`, `designsystem.Motion`, `designsystem.drift` and `designsystem.entrance`.

**b.** After `private const val LONG_TITLE = 18` (line 37), add:

```kotlin

/** Each slide's picture settles once from 1.06 to its own size — `cover-drift 14s var(--ease-out)` (`home.css:27,128`). */
private const val DRIFT_FROM = 1.06f
private const val DRIFT_MS = 14_000
```

**c.** In the KDoc, replace lines 40-41:

```kotlin
 * One cover story's own picture, headline and actions, over its own
 * backdrop — a `Crossfade` slide in [HomeCover].
```

with:

```kotlin
 * One cover story's own picture, headline and actions, over its own
 * backdrop — one slide of [HomeCover].
 *
 * [turning]: the page arrived on this film or a turn brought it in, so the
 * slide comes up out of the cover's ground and its picture settles. A film a
 * refresh swapped in is drawn at rest, as on the web's settled page.
```

**d.** In the parameters, after `watchlisted: Boolean,` (line 59), add `turning: Boolean,`.

**e.** Replace line 64:

```kotlin
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)) {
```

with:

```kotlin
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).then(if (turning) Modifier.entrance(COVER_FADE_MS) else Modifier)) {
```

**f.** Replace line 73:

```kotlin
            HeroArtwork(path = backdropPath, modifier = Modifier.matchParentSize())
```

with:

```kotlin
            // Clipped here, so the settling picture never spills past the
            // cover (`.cover { overflow: hidden }`, `home.css:13`). Outside
            // HeroArtwork, so Blurred's own 1.12 overscan multiplies with the
            // settle instead of being overridden by it as the web's is.
            HeroArtwork(
                path = backdropPath,
                modifier =
                    Modifier
                        .matchParentSize()
                        .clipToBounds()
                        .then(if (turning) Modifier.drift(DRIFT_FROM, 1f, DRIFT_MS, Motion.EaseOut) else Modifier),
            )
```

Under reduced motion `drift` shows its rest scale (1, the `to` value) and never ticks, and `entrance` snaps with the duration scale. Neither needs a check of its own. The file goes from 171 to about 192 lines.

- [ ] **Step 6: Hold `HomeCoverTest`'s three-film cover still**

In `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTest.kt`, add the import `designsystem.LocalReducedMotion`. In `theLineupShrinkingPastTheShownPageDoesNotCrash`, replace:

```kotlin
        show(
            content = {
                HomeCover(films = films.value, watchlist = emptySet(), width = 1164.dp, onPlay = {}, onOpenTitle = {}, onToggleWatchlist = { _, _ -> })
            },
        )
```

with:

```kotlin
        // Held still: a slide's 14s settle outlasts the 9s hold, so this
        // test's auto-advancing clock would turn the cover for ever. The dots
        // still pick.
        show(
            content = {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    HomeCover(films = films.value, watchlist = emptySet(), width = 1164.dp, onPlay = {}, onOpenTitle = {}, onToggleWatchlist = { _, _ -> })
                }
            },
        )
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. That is the 6 new tests, every `HomeCoverTest`, and `HomeScreenTest`: its one cover film doesn't rotate, so the auto-advancing clock runs the 14s settle out and goes idle.

- [ ] **Step 8: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): the cover comes up out of black and settles, as the web's does"
```

## Task 4.2: The Featured reel moves the web's way

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/FeaturedReel.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/FeaturedSlide.kt`
- Test: `ui-mobile/src/test/kotlin/ui/catalog/FeaturedSlideMotionTest.kt`

**Interfaces:**
- **Consumes:** `Modifier.drift`, `Modifier.entrance`, `Motion.CssEase`, `Motion.CssEaseOut` and `LocalReducedMotion` (phase 01).
- **Produces:**
  - `FeaturedSlide(…, paused: Boolean, …)`, which replaces `drifting: Boolean`;
  - `internal const val FEATURED_POSTER_TEST_TAG = "featured-poster"`.

`FeaturedSlide` is tested on its own, not through `FeaturedReel`: the reel's `Dialog` adds a window and nothing this tests, and the reel only hands the slide `paused`.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/FeaturedSlideMotionTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import designsystem.LocalReducedMotion
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * One Featured slide's motion (`playback.css:499-510,585`): the poster closes
 * in once and holds where it is while the reel is paused, and rests at its
 * own size under reduced motion; the copy rises line by line.
 *
 * The poster's frame clips it, as the web's does, so its clipped left edge
 * stays at the frame's while the unclipped one is mapped left by the scale
 * and pan: the gap between the two is how far it has drifted, 0 at rest.
 * Alpha is drawn and cannot be captured here (see PageGroundColorTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h600dp")
class FeaturedSlideMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val film = MediaSet(
        setId = "f", kind = Kind.MOVIE, title = "A Film", show = null, chapter = null, path = null, season = null,
        episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 6_000, posterPath = null, totalBytes = 0,
    )

    private fun show(paused: MutableState<Boolean> = mutableStateOf(false), reducedMotion: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                        FeaturedSlide(
                            set = film, count = "Featured · 1 / 1", info = null, paused = paused.value,
                            onTogglePause = {}, onPlay = {}, onDetails = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun drifted(): Float {
        val poster = compose.onNodeWithTag(FEATURED_POSTER_TEST_TAG, useUnmergedTree = true)
        return poster.getBoundsInRoot().left.value - poster.getUnclippedBoundsInRoot().left.value
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    /** Review Focus 5: with "Remove animations" on, the poster is at its own size and stays there. */
    @Test
    fun underReducedMotionThePosterRestsAtItsOwnSize() {
        show(reducedMotion = true)
        assertEquals(0f, drifted(), 0.5f)
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(0f, drifted(), 0.5f)
    }

    @Test
    fun thePosterClosesInOnceAndHoldsWhereItIsWhilePaused() {
        val paused = mutableStateOf(false)
        show(paused)
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { paused.value = true }
        compose.mainClock.advanceTimeByFrame()
        val held = drifted()
        assertTrue(held > 1f, "the poster should have closed in by now, drift was $held")
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(held, drifted(), 0.1f)
        compose.runOnUiThread { paused.value = false }
        compose.mainClock.advanceTimeBy(9_000)
        assertTrue(drifted() > held, "resumed from where it held")
    }

    @Test
    fun theCopyRisesLineByLine() {
        show()
        compose.mainClock.advanceTimeBy(300)
        val count = top("Featured · 1 / 1")
        val play = top("Play")
        compose.mainClock.advanceTimeBy(1_500)
        // The buttons wait four beats (450ms), so at 300ms they have not moved.
        assertEquals(14f, play - top("Play"), 0.5f)
        val countToGo = count - top("Featured · 1 / 1")
        assertTrue(countToGo in 1f..10f, "the count line should be well on its way, $countToGo dp to go")
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.FeaturedSlideMotionTest'`
Expected: compilation fails. `FEATURED_POSTER_TEST_TAG` is unresolved, and there is no parameter named `paused`.

- [ ] **Step 3: Rewrite the poster and the copy in `FeaturedSlide.kt`**

In `ui-mobile/src/main/kotlin/ui/catalog/FeaturedSlide.kt`:

**a. Imports.** Remove `androidx.compose.animation.core.RepeatMode`, `…animateFloat`, `…infiniteRepeatable`, `…rememberInfiniteTransition`, `…tween` (lines 5-9), `androidx.compose.runtime.getValue` (line 26) and `androidx.compose.ui.graphics.graphicsLayer` (line 32). Add:
- `androidx.compose.ui.draw.clipToBounds`
- `androidx.compose.ui.geometry.Offset`
- `androidx.compose.ui.platform.testTag`
- `designsystem.Motion`
- `designsystem.drift`
- `designsystem.entrance`

**b. Parameter.** Rename it: `drifting: Boolean,` (line 55) becomes `paused: Boolean,`. Line 73 becomes:

```kotlin
        val art: @Composable (Modifier) -> Unit = { modifier -> DriftingPoster(poster, paused, onTogglePause, modifier) }
```

**c. Poster and copy.** Replace lines 98-144 (`DriftingPoster` through the end of `FeaturedCopy`) with:

```kotlin
/** The reel's poster, for a test to read its drift from. */
internal const val FEATURED_POSTER_TEST_TAG = "featured-poster"

/** `featured-drift`: once per slide, from its own size to 1.09 and up-left by 1.5% and 1%, over 8.5s (`playback.css:499-503`). */
private const val POSTER_DRIFT_MS = 8_500
private const val POSTER_DRIFT_TO = 1.09f
private val POSTER_DRIFT_PAN = Offset(-0.015f, -0.01f)

/**
 * The poster, closing in once while its slide shows; a tap pauses the reel.
 * Paused, it holds where it is and carries on from there, as the web's
 * `animation-play-state: paused` does (`playback.css:585`). Under reduced
 * motion it rests at its own size, where the web's `animation: none` leaves
 * it. Clipped to its frame, as `.featured-poster` is (`playback.css:495`).
 */
@Composable
private fun DriftingPoster(
    poster: File?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.aspectRatio(2f / 3f).clipToBounds().clickable(role = Role.Button, onClick = onTogglePause)) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .drift(1f, POSTER_DRIFT_TO, POSTER_DRIFT_MS, Motion.CssEaseOut, running = !paused, translate = POSTER_DRIFT_PAN, restScale = 1f)
                    .testTag(FEATURED_POSTER_TEST_TAG),
        )
    }
}

/** `featured-rise`: each line of the copy up 14dp and in over 900ms, a beat after the one above (`playback.css:505-510`). */
private const val RISE_MS = 900
private const val RISE_BEAT_MS = 150
private val RISE_FROM = 14.dp

/** A line of the copy rising [beat] beats after the first; the web stops counting at the fourth line (`:nth-child(n + 4)`). */
private fun Modifier.rise(beat: Int): Modifier = entrance(RISE_MS, delayMs = RISE_BEAT_MS * beat, fromY = RISE_FROM, easing = Motion.CssEaseOut)

@Composable
private fun FeaturedCopy(
    set: MediaSet,
    count: String,
    info: TitleInfo?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier,
) {
    val facts = listOfNotNull(set.year?.toString()) + set.genres.take(3) + listOfNotNull(ratingLabel(info?.rating?.takeIf { it > 0 }))
    // A fixed beat per line, as on the web, where the facts line is always
    // there (`featured-reel.js:161`) and the tagline goes in before the
    // buttons when it arrives, rising from then (`featured-reel.js:211`).
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(count, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
        Text(set.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.rise(1))
        if (facts.isNotEmpty()) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.rise(2))
        }
        info?.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Color.White, modifier = Modifier.rise(3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), modifier = Modifier.padding(top = Spacing.small).rise(3)) {
            CatalogueButton(onClick = onPlay) { Text("Play") }
            CatalogueOutlinedButton(onClick = onDetails) { Text("Details", color = Color.White) }
        }
    }
}
```

The file goes from 147 to about 160 lines.

- [ ] **Step 4: The reel's cross-fade on CSS `ease`, and no read of its own, in `FeaturedReel.kt`**

In `ui-mobile/src/main/kotlin/ui/catalog/FeaturedReel.kt`:

**a. Imports.** Remove `android.provider.Settings` (line 3), `androidx.compose.runtime.remember` (line 27) and `androidx.compose.ui.platform.LocalContext` (line 33). Add `designsystem.Motion`.

**b. The cross-fade.** Replace lines 83-92:

```kotlin
        val still = rememberReducedMotion()

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Crossfade(targetState = index, animationSpec = tween(if (still) 0 else FADE_MS), label = "featured") { at ->
                val set = films[at]
                FeaturedSlide(
                    set = set,
                    count = "Featured · ${at + 1} / ${films.size}",
                    info = rememberTitleInfo(set.posterKey, titleInfo),
                    drifting = !still && !paused,
```

with:

```kotlin

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // The web's `.featured-slide` opacity transition, `1200ms ease`
            // (`playback.css:477`). "Remove animations" scales it to nothing,
            // as the web's reduced motion removes it, and the poster's drift
            // reads the setting itself, so the reel needs no read of its own.
            Crossfade(targetState = index, animationSpec = tween(FADE_MS, easing = Motion.CssEase), label = "featured") { at ->
                val set = films[at]
                FeaturedSlide(
                    set = set,
                    count = "Featured · ${at + 1} / ${films.size}",
                    info = rememberTitleInfo(set.posterKey, titleInfo),
                    paused = paused,
```

**c. The old read.** Delete lines 153-162: the KDoc, `@Composable private fun rememberReducedMotion()` and the blank line after it. `private val DOT = 8.dp` then follows the blank line closing `FeaturedNav`.

The file goes from 165 to about 157 lines.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. That is the 3 new tests and every existing one. No existing test opens the reel; if one is added, it must pause the clock (insight 11).

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): the Featured reel's poster closes in once and its copy rises, as the web's do"
```

## Task 4.3: The masthead under reduced motion, and Continue strips that rest on a card

**Files:**
- Modify: `ui-common/src/main/kotlin/ui/common/chrome/CoverBlend.kt`
- Modify: `ui-common/src/test/kotlin/ui/common/chrome/CoverBlendTest.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/LibraryHome.kt` (lines 121-126)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/ResumeCard.kt` (`ResumeRow`, lines 60-61)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/ResumeStrip.kt` (lines 52-54)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/home/ResumeRowSnapTest.kt`

**Interfaces:**
- **Consumes:** `LocalReducedMotion` (phase 01, provided in 02).
- **Produces:** `coverBlend(…, reducedMotion: Boolean = false)`. The default leaves ui-tv's two call sites (`TvCatalogBlend.kt:77,79`) as they are.

- [ ] **Step 1: Write the failing blend test**

Add to `CoverBlendTest`:

```kotlin
    @Test
    fun underReducedMotionTheBarIsSolidEvenOverTheCoversTop() {
        // The web settles its masthead over a cover only with
        // `prefers-reduced-motion: no-preference` (shell.css:230); otherwise
        // it is the ordinary paper bar.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, coverHeightPx = coverHeight, barHeightPx = barHeight, reducedMotion = true))
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :ui-common:testDebugUnitTest --tests 'ui.common.chrome.CoverBlendTest'`
Expected: compilation fails, no parameter named `reducedMotion`.

- [ ] **Step 3: Give `coverBlend` the web's opt-out**

In `CoverBlend.kt`, append to the KDoc (before its closing `*/`, line 22):

```kotlin
 *
 * Under reduced motion it is always 1: the web settles its masthead over a
 * cover only with `prefers-reduced-motion: no-preference` (`shell.css:230`)
 * and is the ordinary paper bar otherwise.
```

Then replace the function (lines 23-32) with:

```kotlin
fun coverBlend(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    coverHeightPx: Float,
    barHeightPx: Float,
    reducedMotion: Boolean = false,
): Float {
    if (reducedMotion || firstVisibleItemIndex > 0) return 1f
    val threshold = (coverHeightPx - barHeightPx).coerceAtLeast(1f)
    return (firstVisibleItemScrollOffset / threshold).coerceIn(0f, 1f)
}
```

In `ui-mobile/src/main/kotlin/ui/chrome/LibraryHome.kt`, add the import `designsystem.LocalReducedMotion`, and replace lines 121-126:

```kotlin
            val blend by remember(heroState, barHeightPx) {
                derivedStateOf {
                    val hero = heroState
                    if (hero == null) 1f else coverBlend(hero.firstVisibleItemIndex, hero.firstVisibleItemScrollOffset, hero.heroHeightPx, barHeightPx)
                }
            }
```

with:

```kotlin
            val reducedMotion = LocalReducedMotion.current
            val blend by remember(heroState, barHeightPx, reducedMotion) {
                derivedStateOf {
                    val hero = heroState
                    if (hero == null) 1f else coverBlend(hero.firstVisibleItemIndex, hero.firstVisibleItemScrollOffset, hero.heroHeightPx, barHeightPx, reducedMotion)
                }
            }
```

`LibraryHome.kt` goes from 196 to 198 lines.

- [ ] **Step 4: Run the blend tests to see them pass, and commit**

```bash
./gradlew :ui-common:testDebugUnitTest :ui-mobile:compileDebugKotlin :ui-tv:compileDebugKotlin
./gradlew :ui-common:spotlessApply :ui-common:detekt :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-common ui-mobile
git commit -m "feat(motion): the masthead is solid over the cover under reduced motion, as the web's is"
```

Expected: PASS, and ui-tv still compiles with its call sites unchanged.

- [ ] **Step 5: Write the failing snap test**

Create `ui-mobile/src/test/kotlin/ui/catalog/home/ResumeRowSnapTest.kt`:

```kotlin
package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import catalog.SetCard
import kotlin.math.abs
import kotlin.test.assertTrue
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The Continue strip comes to rest with a card at its start, as the web's
 * `scroll-snap-type: x mandatory` strip does (`home.css:220,234`), rather
 * than wherever a fling runs out. Unclipped bounds, so a card half scrolled
 * off reads where it really is rather than clipped to the strip's edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h500dp")
class ResumeRowSnapTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film $id", show = null, chapter = null, path = null, season = null,
        episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 3_600, posterPath = null, totalBytes = 0,
    )

    /** Where card [i] starts, or null once the strip has let go of it. */
    private fun cardLeft(i: Int): Float? {
        val card = hasClickAction() and hasText("Film $i")
        if (compose.onAllNodes(card).fetchSemanticsNodes().isEmpty()) return null
        return compose.onNode(card).getUnclippedBoundsInRoot().left.value
    }

    @Test
    fun aSwipeComesToRestWithACardAtTheStripsStart() {
        val cards = (1..8).map { SetCard(film("$it"), "1h left", 0.4f, false) }
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { ResumeRow(cards = cards, onPlay = {}, modifier = Modifier.testTag("strip")) } }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(startX = centerX + 150f, endX = centerX - 150f, durationMillis = 400) }
        compose.waitForIdle()
        val first = cardLeft(1)
        assertTrue(first == null || first < -1f, "the strip should have moved, the first card starts at $first")
        val lefts = (1..8).mapNotNull(::cardLeft)
        assertTrue(lefts.any { abs(it) < 1f }, "a card should rest at the strip's start, cards start at $lefts")
    }
}
```

- [ ] **Step 6: Run it to see it fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.home.ResumeRowSnapTest'`
Expected: FAIL on the second assertion: the fling stops part-way through a card.

**If it passes before the change**, the fling happened to end on a card edge. Change the swipe distance until it fails. The assertion is about where the strip rests, not how far it went.

- [ ] **Step 7: Snap both Continue strips**

In `ui-mobile/src/main/kotlin/ui/catalog/home/ResumeCard.kt`, add the imports `androidx.compose.foundation.gestures.snapping.SnapPosition` and `androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior`, and replace lines 60-61:

```kotlin
    LazyRow(
        state = rememberRowState(cards.map { it.set.setId }),
```

with:

```kotlin
    // Rests with a card at its start, as the web's strip does (`home.css:220,234`); other rows coast.
    val state = rememberRowState(cards.map { it.set.setId })
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
```

`ResumeCard.kt` goes from 191 to 196 lines.

In `ui-mobile/src/main/kotlin/ui/catalog/ResumeStrip.kt`, add the same two imports, and replace lines 52-54:

```kotlin
    if (cards.isEmpty()) return
    LazyRow(
        state = rememberRowState(cards.map { it.set.setId }),
```

with:

```kotlin
    if (cards.isEmpty()) return
    // A department's Continue strip rests on a card too: every web `.resume-strip` snaps (`home.css:220,234`).
    val state = rememberRowState(cards.map { it.set.setId })
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
```

`rememberSnapFlingBehavior(LazyListState, SnapPosition)` and `SnapPosition.Start` are stable API in foundation 1.11.4 (checked in the cached jar: no opt-in annotation). The keyed-row anchoring in `rememberRowState` (`ui-common/.../RememberRowState.kt`) still calls `requestScrollToItem(0)` at a strip's start, and that is unaffected.

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 9: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): Continue strips come to rest on a card, as the web's do"
```

## Success criteria

- **Cover:**
  - a turn leaves exactly one slide composed from the next frame;
  - a slide that a turn or the arrival brings in starts its picture at scale 1.06 and rests at 1 by 14s, under Blurred too;
  - a tap 300ms into a turn reaches nothing, and one at 1.7s plays (Review Focus 3);
  - a sync with the same film moves nothing (Review Focus 2);
  - a film a refresh moves in is at rest and takes taps at once;
  - under reduced motion it never turns.

  `HomeCoverTurnTest`, 6 tests.
- **Reel:**
  - under reduced motion the poster rests at scale 1 (Review Focus 5);
  - paused, it holds within 0.1dp over 3s and resumes;
  - the buttons hold their 14dp for 450ms while the count line is under way.

  `FeaturedSlideMotionTest`, 3 tests.
- **Masthead:** solid under reduced motion. `CoverBlendTest`. ui-tv compiles unchanged.
- **Continue strips:** on Home and the department pages they rest with a card at the start. `ResumeRowSnapTest`.
- **Hand-rolled reads:**
  - `git grep -n ANIMATOR_DURATION_SCALE -- ui-mobile` prints nothing;
  - the only read left is `rememberReducedMotion` in `core/designsystem`;
  - `InfiniteTransition` is gone from `FeaturedSlide.kt`.
- Every touched file is under 200 lines. `HomeCover.kt` drops from 203 to 196.
- `:ui-common` and `:ui-mobile` unit tests are green.
- **Left to phase 09:** the fades on the tablet, and the Blurred frame-time measurement (spec §3, Risk: Blurred). If Blurred janks, the cover stops settling under Blurred, and that is written down.
