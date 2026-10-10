# Phase 05 — Title and department pages

**Spec:** `../spec.md` §2 (Title and department pages: hero art, tab content). **Status:** pending. **Depends on:** 01.
**Runs after 03.** It passes Task 3.2's `Reveals` into the heroes, and its edits to the department screens sit inside the blocks Task 3.3 rewrote. Where a line number below is main's and phase 03 moved it, find it by the code quoted.
**Inventory rows:** #6, #7, #8.

## Context

**What the web does**
- **Art-in (#6).** `.spread-art img` and `.dept-art img` run `art-in 1400ms var(--ease) both` (`web/public/styles/title-page.css:63`, `departments.css:17`). The keyframes are `from { opacity: 0; transform: scale(1.04) }` (`title-page.css:73`).
  - Only the picture moves. The scrims are the still container's `::after` (`title-page.css:64-72`, `departments.css:18-28`).
  - The container clips (`overflow: hidden`, `title-page.css:62`, `departments.css:16`).
- **Pull-quote (#7).** Same keyframes, 200 ms later (`title-page.css:115`, `departments.css:45`). Hidden at ≤900px (`title-page.css:161`, `departments.css:91`), and Android's compact layouts already leave it out.
  - A late tagline is appended when it arrives (`web/public/lib/catalog/title-spread.js:44-46`), so its art-in plays then.
  - The department quote's dark patch is the art's `::after` (`departments.css:27`). It never moves.
- **Tab panel (#8).** `.tab-panel { animation: panel-in 520ms var(--ease) both }`, `from { opacity: 0; transform: translateY(8px) }` (`title-page.css:133-134`).
  - It replays whenever a panel is un-hidden (`web/public/lib/catalog/tabs.js:85-97`), on the film page and the series page alike (`series-page.js:101`).
  - The panel rises as one block. Rows scrolled to later were already in it.
- **When it plays.** `.settled` turns all three off on a redraw of the address already showing (`title-page.css:170`, `departments.css:94`, `web/public/lib/redraw.js:16-23`).
  - Any arrival plays them, Back included: Back is a new address, not a redraw.
  - Resizing redraws nothing.

**The rule on Android, one for every site.** A fresh page visit plays it, Back included. Scrolling within the page, a redraw, a sync and turning the tablet never do.

**How each lifetime gives that (traced):**
- **Visits.**
  - A pushed frame is `key(frameKey) { SaveableStateProvider(frameKey) … }` (`ui-mobile/src/main/kotlin/ui/LibraryBranchSupport.kt:126-131`). Only the top frame is composed (`LibraryFlowBranches.kt:134-135`), so a covered frame leaves composition, and Back composes it again.
  - The shelves leave composition under a pushed frame too (`LibraryFlowBranches.kt:225-257`), and a department tab switch swaps `when` branches (phase 03 insight 3).
  - So every visit is a new composition. A plain `remember` starts fresh on it.
  - A `rememberSaveable` doesn't. The held slots restore it on Back (`LibraryBranchSupport.kt:114-124`, `LibraryFlowBranches.kt:65-68`), so it must not hold these flags.
- **Turning the tablet.** `MainActivity` handles `orientation|screenSize|…` itself (`app/src/main/AndroidManifest.xml:57-59`). The composition survives, and only the width branch flips (`TitleSpread.kt:77-80`, `DepartmentHero.kt:99-108`). A flag held above that branch survives it.
- **Scrolling.**
  - The film page draws its spread and tabs in a scrolling `Column` (`TitleDetailScreen.kt:90-142`), never disposed mid-visit.
  - Every `DepartmentHero` is a lazy item: Movies `MoviesDepartmentScreen.kt:78`, Shows `ShowsDepartmentScreen.kt:76`, Anime `AnimeDepartmentScreen.kt:101`, Documentaries `DocumentariesDepartmentScreen.kt:106`, Collections `CollectionsScreen.kt:57`, Franchise `FranchiseScreen.kt:44`.
  - So is the series page's spread (`CollectionScreen.kt:182`) and its tab bodies (`:204-218`).
  - A lazy item scrolled away is disposed, or parked and handed back (phase 03 insight 6). Its flag therefore lives with the page, above the list.
- **The page's holder is phase 03's `Reveals`** (`ui/catalog/SectionReveal.kt`, Task 3.2): a plain-`remember` map of `mutableStateOf` flags by key. Task 3.3 already holds one per department screen, above its list.
  - The heroes take that holder and keep their two flags in it, under `HERO_ART` and `HERO_QUOTE`.
  - The series page and `FranchiseScreen`, which Task 3.3 leaves without one, get their own.
- **Restarting a parked node.** Phase 01's `EntranceNode` starts over when it is handed a different `seen` state, and an `onReset` node that has played stays at rest (`phase-01-motion-module.md`, `Entrance.kt`).
  - A lazy list's reuse pool hands nodes across keys, so keying items doesn't by itself give fresh nodes.
  - A new flag per showing does. That is how the series page's tab bodies restart on a switch (Task 5.3).

**Rulings**
1. **Held, never saved.** Every flag in this phase is a plain-`remember` state held by the page, never `rememberSaveable`. Back replays, as on the web.
2. **One art flag per hero, held above the width branch.** On the film page `TitleSpread` holds a `Reveals` of its own. Elsewhere it is the screen's. Both layouts read the same flag, so turning a tablet across the breakpoint doesn't replay.
3. **The quote's flag is created when the quote first composes** (`Reveals.of` is `getOrPut`), in the same holder as its hero. A late tagline plays then.
4. **Only the words of the department quote move.** The entrance sits inside the quote's `drawBehind` patch, because the web's patch belongs to the art's still `::after`.
5. **The picture is clipped before it is scaled** (`clipToBounds()` ahead of the entrance), as `.spread-art` and `.dept-art` clip.

**Contradiction found in phase 03.** Its ruling "Rotation: a rebuilt window draws its page anew" (`phase-03-…md:45`), and the `ponytail` notes at `:251` and `:794`, assume a rotation rebuilds the window. It doesn't: `MainActivity` handles the change itself (`AndroidManifest.xml:59`), and `WidthClassStateTest` says the same.
- Plain `remember`, and so `Reveals`, survives a rotation, which is the behaviour wanted here.
- Phase 03's comments are wrong, not its code. This phase leaves that file to its owner.

**Files over 200 lines, each split in the task that grows it:**

| File | Lines on main | What moves out | To |
|---|---|---|---|
| `DeptHeroText.kt` | 205 | `DeptQuote` | `DeptQuote.kt` |
| `CollectionScreen.kt` | 245 | `SeriesPage` and `SeriesSimilarTab` | `SeriesPage.kt` |

## Task 5.1: The hero's picture and pull-quote settle in, once a visit

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/catalog/ArtIn.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/TitleSpread.kt` (imports, KDoc, `:63-80`, `:87`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/WideTitleSpread.kt` (imports, KDoc, `:68-76`, `:90`, `:138-158`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/DepartmentHero.kt` (KDoc, `:61-70`, `:100`, `:107`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/DepartmentHeroLayouts.kt` (imports, `:31-44`, `:64`, `:107-111`, `:141-148`, `:156`)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/DeptQuote.kt`; modify `DeptHeroText.kt` (lose `DeptQuote`, its two tags, their imports)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/SeriesPage.kt`; modify `CollectionScreen.kt` (lose `SeriesPage` and `SeriesSimilarTab`; gain `state`)
- Modify, one line each, passing the screen's holder: `MoviesDepartmentScreen.kt`, `ShowsDepartmentScreen.kt`, `AnimeDepartmentScreen.kt`, `DocumentariesDepartmentScreen.kt`, `CollectionsScreen.kt`
- Modify: `FranchiseScreen.kt` (a holder of its own, then the same line)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/HeroArtInTest.kt`
- Modify test: `ui-mobile/src/test/kotlin/ui/catalog/DepartmentHeroTest.kt` (`:69-72`, the one direct `DepartmentHero` call)

**Interfaces:**
- **Consumes:** `Modifier.entrance(…, seen)` (phase 01); `Reveals`, `Reveals.of(key)` and `rememberReveals()` (phase 03 Task 3.2).
- **Produces** (package `ui.catalog`, internal):
```kotlin
internal fun Modifier.artIn(seen: MutableState<Boolean>, delayMs: Int = 0): Modifier   // ArtIn.kt
internal const val HERO_ART = "hero-art"; internal const val HERO_QUOTE = "hero-quote"; internal const val QUOTE_IN_DELAY_MS = 200
internal fun TitleSpread(..., bleed: Dp = 0.dp, reveals: Reveals? = null, actions: @Composable () -> Unit = {})
internal fun WideTitleSpread(art, title, facts, overview, tagline, actions, reveals: Reveals, modifier: Modifier = Modifier)
internal fun DepartmentHero(title, line, lead, onOpenTitle, reveals: Reveals, modifier = Modifier, leadName = …, franchiseTitle = false, overview = null)
internal fun BoxScope.WideDeptHero(..., overview: String?, reveals: Reveals)
internal fun CompactDeptHero(..., overview: String?, reveals: Reveals)
internal fun DeptQuote(tagline, leadName, width, onOpen, reveals: Reveals, modifier: Modifier = Modifier)   // moved to DeptQuote.kt
internal fun SeriesPage(..., onSelectSeason: (String) -> Unit, state: LazyListState)   // moved to SeriesPage.kt, was private
fun CollectionScreen(..., onSelectSeason: (String) -> Unit = {}, state: LazyListState = rememberLazyListState())
```
- `reveals` is required on `DepartmentHero`, so a caller can't fall back to an item-local flag that a scroll would lose.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/HeroArtInTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.catalog.HERO_ARTWORK_TEST_TAG
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hero art-in plays once a page visit, Back included, as the web's does,
 * and never on a scroll back, a redraw or a turn of the tablet. A picture
 * still settling from 1.04 has its top-left further left than at rest, and a
 * node's unclipped bounds in root follow the layer, so that is what is read.
 * Alpha cannot be read here (see PageGroundColorTest); the fade is the device
 * walk's. Native graphics give text its real width, so a quote's scale shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class HeroArtInTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun artLeft() =
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun leftOf(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private val film =
        MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
        )

    private val info = TitleInfo(overview = "Spice.", tagline = "A beginning", genres = null, rating = null, network = null, status = null)

    private val lead =
        MediaSet(
            setId = "lead", kind = Kind.MOVIE, title = "a lowercase title", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", tagline = "a quotable line",
        )

    private val series =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 40, chapters = 1,
            divisions =
                listOf(
                    Division(
                        "Season 1", 1,
                        (1..40).map { n ->
                            MediaSet(
                                setId = "s1e$n", kind = Kind.EPISODE, title = "Ep-%03d".format(n), show = "Show", chapter = null,
                                path = null, season = 1, episodeFirst = n, episodeLast = null, year = 2020, durationSecs = 1800,
                                posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
                            )
                        },
                        emptyList(),
                    ),
                ),
        )

    @Composable
    private fun department(reveals: Reveals) =
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { DepartmentHero(title = "Movies", line = "four films", lead = lead, onOpenTitle = null, reveals = reveals) }
            // Enough rows of the same content type that the hero's slot is reused, not kept.
            items(12) { Spacer(Modifier.fillMaxWidth().height(400.dp)) }
        }

    @Test fun aTitlesArtSettlesFromAboveRestAndARedrawLeavesItThere() {
        var pinned by mutableStateOf<String?>(null)
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}, editorsChoice = pinned) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        assertTrue(start < rest - 5f, "the art starts at 1.04: left $start against $rest at rest")
        compose.runOnUiThread { pinned = film.setId }
        frames(2)
        assertEquals(rest, artLeft(), 0.5f, "a redraw of the same page never replays it")
    }

    @Test fun theQuoteWaits200msBehindItsPicture() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val art = artLeft()
        val quote = leftOf("“A beginning”")
        compose.mainClock.advanceTimeBy(150)
        assertTrue(artLeft() > art + 1f, "the picture is already settling")
        assertEquals(quote, leftOf("“A beginning”"), 0.05f, "the quote is still waiting")
        compose.mainClock.advanceTimeBy(1_700)
        assertTrue(leftOf("“A beginning”") > quote + 1f, "and then it settles too")
    }

    @Test fun aNewlyOpenedTitlePlaysIt() {
        var visit by mutableStateOf(0)
        show { key(visit) { TitleDetailScreen(film, info, {}, onOpenGenre = {}) } }
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        compose.runOnUiThread { visit = 1 }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "a new frame key is a new visit, and it plays")
    }

    /** The frame's saved slot outlives a page pushed over it, as `keyedFrame`'s does; the art-in must not. */
    @Test fun aTitleComeBackToWithBackPlaysItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("title") { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        }
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "Back is a new visit, and plays it, as on the web")
    }

    /** A fake [WindowInfo] flips the width class in place, as a real rotation does here (WidthClassStateTest). */
    @Test fun turningTheTabletAcrossTheWideBreakpointDoesNotReplayIt() {
        var widthPx by mutableStateOf(1280)
        show {
            val real = LocalWindowInfo.current
            val turned = object : WindowInfo by real { override val containerSize: IntSize get() = IntSize(widthPx, 800) }
            CompositionLocalProvider(LocalWindowInfo provides turned) { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(WIDE_TITLE_SPREAD_TEST_TAG, useUnmergedTree = true).assertExists()
        compose.runOnUiThread { widthPx = 800 }
        frames(2)
        compose.onNodeWithTag(WIDE_TITLE_SPREAD_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        val arrived = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertEquals(arrived, artLeft(), 0.5f, "the stacked spread's art arrives at rest")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aPhonesStackedSpreadSettlesToo() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(start < artLeft() - 3f, "the stacked spread's art starts at 1.04 too")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1600dp")
    fun aSeriesSpreadScrolledAwayAndBackStaysAtRest() {
        val list = LazyListState()
        show { CollectionScreen(collection = series, info = info, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenGenre = {}, state = list) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        assertTrue(start < rest - 3f, "the series spread's art starts at 1.04")
        compose.runOnUiThread { list.requestScrollToItem(40) }
        frames(3)
        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        assertEquals(rest, artLeft(), 0.5f, "scrolled back, the picture is already at rest")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentHeroScrolledAwayAndBackStaysAtRest() {
        val list = LazyListState()
        show {
            val reveals = rememberReveals()
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item { DepartmentHero(title = "Movies", line = "four films", lead = lead, onOpenTitle = null, reveals = reveals) }
                items(12) { Spacer(Modifier.fillMaxWidth().height(400.dp)) }
            }
        }
        val art = artLeft()
        val quote = leftOf("“a quotable line”")
        compose.mainClock.advanceTimeBy(150)
        assertEquals(quote, leftOf("“a quotable line”"), 0.05f, "the quote waits behind its picture")
        compose.mainClock.advanceTimeBy(1_700)
        val artRest = artLeft()
        val quoteRest = leftOf("“a quotable line”")
        assertTrue(art < artRest - 5f && quote < quoteRest - 1f, "both settle from 1.04")
        compose.runOnUiThread { list.requestScrollToItem(12) }
        frames(3)
        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        assertEquals(artRest, artLeft(), 0.5f, "scrolled back, the picture is already at rest")
        assertEquals(quoteRest, leftOf("“a quotable line”"), 0.5f, "and so is its quote")
    }

    /** The shelves' saved slot outlives a title pushed over them; the department's art-in must not. */
    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentComeBackToWithBackPlaysItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("shelves") { department(rememberReveals()) }
        }
        compose.mainClock.advanceTimeBy(1_800)
        val rest = artLeft()
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "Back is a new visit, and plays it, as on the web")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aPhonesDepartmentHeroSettlesToo() {
        show { department(rememberReveals()) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(start < artLeft() - 3f, "the compact hero's strip starts at 1.04 too")
    }
}
```

In `DepartmentHeroTest.kt`, the one direct call (`:69-72`) gains the holder. Replace:

```kotlin
                        DepartmentHero(
                            title = title, line = "48 shows", lead = lead,
                            onOpenTitle = onOpenTitle, franchiseTitle = franchiseTitle,
                        )
```

with:

```kotlin
                        DepartmentHero(
                            title = title, line = "48 shows", lead = lead,
                            onOpenTitle = onOpenTitle, reveals = rememberReveals(), franchiseTitle = franchiseTitle,
                        )
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.HeroArtInTest'`
Expected: compilation fails. `DepartmentHero` has no `reveals` and `CollectionScreen` has no `state` yet, and that failure is the first fail. Once Steps 3-8 add the parameters, the test bodies fail at their first `assertTrue` for as long as no art-in exists.

- [ ] **Step 3: Create `ui-mobile/src/main/kotlin/ui/catalog/ArtIn.kt`**

```kotlin
package ui.catalog

import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import designsystem.entrance

/** `art-in 1400ms var(--ease)` (`title-page.css:63`, `departments.css:17`). */
private const val ART_IN_MS = 1400

/** `@keyframes art-in { from { opacity: 0; transform: scale(1.04) } }` (`title-page.css:73`). */
private const val ART_IN_FROM_SCALE = 1.04f

/** The pull-quote follows its picture by this much (`title-page.css:115`, `departments.css:45`). */
internal const val QUOTE_IN_DELAY_MS = 200

/** Where a hero keeps its two art-ins in its page's [Reveals]. */
internal const val HERO_ART = "hero-art"
internal const val HERO_QUOTE = "hero-quote"

/**
 * The art-in a title spread or a department hero gives its picture and its
 * pull-quote: a fade up from the page while settling from 1.04 to rest.
 *
 * [seen] belongs to the page, never to a lazy item and never to saved state,
 * so the art-in plays once a visit: not again on a redraw (`.settled`,
 * `title-page.css:170`), a scroll back or a turn of the tablet, but again on
 * any arrival, Back included, as the web's router draws one.
 */
internal fun Modifier.artIn(seen: MutableState<Boolean>, delayMs: Int = 0): Modifier =
    entrance(ART_IN_MS, delayMs = delayMs, fromScale = ART_IN_FROM_SCALE, seen = seen)
```

- [ ] **Step 4: The title spread**

`TitleSpread.kt`. Add `import androidx.compose.runtime.remember` and `import androidx.compose.ui.draw.clipToBounds`.

In the KDoc (`:58-61`), after the paragraph ending "…The stacked spread keeps the inset — see the series page.", add:

```kotlin
 *
 * [reveals] is the page's holder for the art-in where the spread sits in a
 * lazy list (the series page). Without one the spread holds its own, which
 * the film page's scrolling column never disposes mid-visit.
```

Replace the signature's tail and the start of the body (`:70-80`):

```kotlin
    bleed: Dp = 0.dp,
    actions: @Composable () -> Unit = {},
) {
    // Solid drops the art — no picture, no placeholder, no tagline quote —
    // the same "plain pages, no artwork" the web's own
    // `[data-backdrop="solid"]` rule leaves behind.
    val art = backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
        WideTitleSpread(art, title, facts, overview, tagline, actions, modifier.bleed(bleed))
        return
    }
```

with:

```kotlin
    bleed: Dp = 0.dp,
    reveals: Reveals? = null,
    actions: @Composable () -> Unit = {},
) {
    // Solid drops the art — no picture, no placeholder, no tagline quote —
    // the same "plain pages, no artwork" the web's own
    // `[data-backdrop="solid"]` rule leaves behind.
    val art = backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    // Held, not saved, so Back is a new visit that plays the art-in again, as
    // on the web; and above the width branch, so turning a tablet across it
    // redraws the spread without playing it again.
    val ownReveals = remember { Reveals() }
    val page = reveals ?: ownReveals
    if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
        WideTitleSpread(art, title, facts, overview, tagline, actions, page, modifier.bleed(bleed))
        return
    }
```

Replace `:87`:

```kotlin
                    HeroArtwork(path = art, modifier = Modifier.matchParentSize())
```

with:

```kotlin
                    // Clipped as `.spread-art` is (`title-page.css:62`), so the
                    // picture settling from 1.04 never spills past its block.
                    HeroArtwork(path = art, modifier = Modifier.matchParentSize().clipToBounds().artIn(page.of(HERO_ART)))
```

`WideTitleSpread.kt`. Add `import androidx.compose.ui.draw.clipToBounds`.

In the KDoc (`:54-66`), after "…so the art starts beneath that instead.", add:

```kotlin
 *
 * [reveals] is [TitleSpread]'s holder, so both widths share one played flag.
```

Replace the signature `:68-76`:

```kotlin
internal fun WideTitleSpread(
    art: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
```

with:

```kotlin
internal fun WideTitleSpread(
    art: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    actions: @Composable () -> Unit,
    reveals: Reveals,
    modifier: Modifier = Modifier,
) {
```

Replace `:90`:

```kotlin
                HeroArtwork(path = art, modifier = strip)
```

with:

```kotlin
                // `.spread-art img` alone settles in (`title-page.css:63`), clipped by its
                // block (`:62`); the scrims below are the still `::after` and never move.
                HeroArtwork(path = art, modifier = strip.clipToBounds().artIn(reveals.of(HERO_ART)))
```

In the quote block (`:138-158`), replace:

```kotlin
        if (art != null && !tagline.isNullOrBlank()) {
            // `bottom: 22%` of the spread's own final height — read once it
            // has one, not guessed from the floor.
            BoxWithConstraints(Modifier.matchParentSize()) {
```

with:

```kotlin
        if (art != null && !tagline.isNullOrBlank()) {
            // `.spread-quote`'s art-in, 200ms behind its picture (`title-page.css:115`).
            // Its flag is made here, as late as the quote: a tagline that arrives
            // after the page is drawn plays it then (`title-spread.js:44-46`).
            val quoteIn = Modifier.artIn(reveals.of(HERO_QUOTE), delayMs = QUOTE_IN_DELAY_MS)
            // `bottom: 22%` of the spread's own final height — read once it
            // has one, not guessed from the floor.
            BoxWithConstraints(Modifier.matchParentSize()) {
```

and the quote `Text`'s modifier:

```kotlin
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = gutter, bottom = maxHeight * 0.22f)
                            .widthIn(max = SPREAD_QUOTE_MAX_WIDTH),
```

with:

```kotlin
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = gutter, bottom = maxHeight * 0.22f)
                            .widthIn(max = SPREAD_QUOTE_MAX_WIDTH)
                            .then(quoteIn),
```

- [ ] **Step 5: The department hero takes its page's holder**

`DepartmentHero.kt`. In the KDoc (`:31-59`), after the paragraph ending "…where `renderFranchise` appends `.franchise-overview`.", add:

```kotlin
 *
 * [reveals] is the department page's own holder, from `rememberReveals()`
 * above its lazy list. The hero's art-in is kept there, so the hero scrolled
 * away and back stays at rest, while any new visit, Back included, plays it.
```

Replace the signature `:61-70`:

```kotlin
internal fun DepartmentHero(
    title: String,
    line: String,
    lead: MediaSet?,
    onOpenTitle: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
```

with:

```kotlin
internal fun DepartmentHero(
    title: String,
    line: String,
    lead: MediaSet?,
    onOpenTitle: ((String) -> Unit)?,
    reveals: Reveals,
    modifier: Modifier = Modifier,
```

Replace `:100`:

```kotlin
            CompactDeptHero(title, line, art, width, franchiseTitle, overview)
```

with:

```kotlin
            CompactDeptHero(title, line, art, width, franchiseTitle, overview, reveals)
```

Replace `:107`, the `WideDeptHero` call below the `quote`/`onOpenLead` lines, which stay:

```kotlin
            WideDeptHero(title, line, art, hasBackdropData, quote, onOpenLead, width, topChrome, heroMinHeight, artFraction, franchiseTitle, overview)
```

with:

```kotlin
            WideDeptHero(
                title, line, art, hasBackdropData, quote, onOpenLead, width, topChrome, heroMinHeight, artFraction,
                franchiseTitle, overview, reveals,
            )
```

`DepartmentHeroLayouts.kt`. Add `import androidx.compose.ui.draw.clipToBounds`.

In `WideDeptHero`'s parameters (`:31-44`), after `overview: String?,` add `reveals: Reveals,`. Then replace `:64`:

```kotlin
            HeroArtwork(path = art, modifier = artModifier)
```

with:

```kotlin
            // `.dept-art img` alone settles in, clipped by `.dept-art` (`departments.css:16-17`);
            // the scrims below are its still `::after`. One flag for both widths: the page's.
            HeroArtwork(path = art, modifier = artModifier.clipToBounds().artIn(reveals.of(HERO_ART)))
```

In the `DeptQuote(` call (`:107-111`), after `onOpen = onOpenLead,` add:

```kotlin
            reveals = reveals,
```

In `CompactDeptHero`'s parameters (`:141-148`), after `overview: String?,` add `reveals: Reveals,`. Then replace `:156`:

```kotlin
                HeroArtwork(path = art, modifier = Modifier.matchParentSize())
```

with:

```kotlin
                HeroArtwork(path = art, modifier = Modifier.matchParentSize().clipToBounds().artIn(reveals.of(HERO_ART)))
```

The file stays under 200 lines (about 190).

- [ ] **Step 6: Move `DeptQuote` into its own file and give it the quote's art-in**

`DeptHeroText.kt` is 205 lines, and this edit would grow it. Create `ui-mobile/src/main/kotlin/ui/catalog/DeptQuote.kt` with this header:

```kotlin
package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.Eyebrow
import designsystem.Spacing
import ui.catalog.home.OnImage
import ui.catalog.home.OnImage2
import ui.catalog.home.fluid
```

Below the header, move these from `DeptHeroText.kt` unchanged, in this order:
- `:40-44`: `DEPT_HERO_QUOTE_TEST_TAG` and `DEPT_HERO_QUOTE_TAGLINE_TEST_TAG`, with their KDoc;
- `:117-205`: `DeptQuote`, with its KDoc.

Delete them from `DeptHeroText.kt`. Then remove the imports only `DeptQuote` used:
- `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Column`, `androidx.compose.ui.draw.drawBehind`
- `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.graphics.Brush`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.graphics.Shadow`
- `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.text.font.FontStyle`
- `designsystem.Eyebrow`, `designsystem.Spacing`, `ui.catalog.home.OnImage`, `ui.catalog.home.OnImage2`

`DeptHeroText.kt` keeps `DEPT_HERO_TITLE_TEST_TAG` and `DeptHeroWords`, at about 110 lines.

In `DeptQuote.kt`, add a sentence to the end of `DeptQuote`'s KDoc:

```kotlin
 * Its words settle in 200ms behind the picture, on a flag kept in the
 * hero's [reveals] and made when the quote first draws.
```

Replace its signature:

```kotlin
internal fun DeptQuote(
    tagline: String,
    leadName: String,
    width: Dp,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
```

with:

```kotlin
internal fun DeptQuote(
    tagline: String,
    leadName: String,
    width: Dp,
    onOpen: (() -> Unit)?,
    reveals: Reveals,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // `.dept-quote`'s art-in, 200ms behind the picture (`departments.css:45`).
    val quoteIn = Modifier.artIn(reveals.of(HERO_QUOTE), delayMs = QUOTE_IN_DELAY_MS)
```

and the end of the `Column`'s modifier chain:

```kotlin
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(Color(0x9E080809), Color(0x00080809)), radius = size.maxDimension * 0.6f))
                }
                .padding(Spacing.medium),
```

with:

```kotlin
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(Color(0x9E080809), Color(0x00080809)), radius = size.maxDimension * 0.6f))
                }
                .padding(Spacing.medium)
                // Inside the patch: the web's patch belongs to the art's still
                // `::after` (`departments.css:27`), so only the words settle in.
                .then(quoteIn),
```

- [ ] **Step 7: Each department screen hands its hero the page's holder**

Add `reveals = reveals,` as the line after `onOpenTitle = …,` inside each `DepartmentHero(` call:

| File | Holder from | Line it follows (main) |
|---|---|---|
| `MoviesDepartmentScreen.kt` | Task 3.3 Step 4 | `onOpenTitle = onOpenTitle,` (`:83`, in Task 3.3's rewritten block) |
| `ShowsDepartmentScreen.kt` | Task 3.3 Step 5 | `onOpenTitle = leadKey?.let { key -> { onOpenCollection(key) } },` (`:88`) |
| `AnimeDepartmentScreen.kt` | Task 3.3 Step 6 | `onOpenTitle = onOpenTitle,` (`:106`) |
| `DocumentariesDepartmentScreen.kt` | Task 3.3 Step 7 | `onOpenTitle = null,` (`:114`) |
| `CollectionsScreen.kt` | Task 3.3 Step 8 | `onOpenTitle = onOpenTitle,` (`:62`) |
| `FranchiseScreen.kt` | none: add one, below | `onOpenTitle = onOpenTitle,` (`:49`) |

In `FranchiseScreen.kt`, after `val lead = franchise.films.find { it.backdropPath != null }` (`:35`), add:

```kotlin
    // The hero's art-in, held by the page above its grid, so a scroll away
    // and back finds it played and a new visit plays it. The rows don't
    // reveal: the web never arms this page's (`collections-page.js:115`).
    val reveals = rememberReveals()
```

- [ ] **Step 8: The series page — move it out, give it a holder and a hoisted list**

`CollectionScreen.kt` is 245 lines. Create `ui-mobile/src/main/kotlin/ui/catalog/SeriesPage.kt` with this header:

```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.Shelf
import catalog.extentOf
import catalog.firstItemOf
import catalog.resumeWordsOf
import catalog.seriesAboutFacts
import catalog.seriesFactsLine
import catalog.seriesResumeFor
import catalog.showsOf
import catalog.similarShows
import catalog.summarize
import catalog.walk
import data.PortraitRequestLog
import designsystem.Spacing
import model.TitleCredits
import model.WatchSnapshot
import ui.common.catalog.rememberTitleCredits
import uniffi.mediagram_core.TitleInfo
```

Move `CollectionScreen.kt:108-245` below it unchanged: `SeriesPage` with its KDoc, and `SeriesSimilarTab`. `private fun SeriesPage` becomes `internal fun SeriesPage`. Then make three edits in the moved code.

1. After `onSelectSeason: (String) -> Unit,` (last parameter, `:142`), add:

```kotlin
    state: LazyListState,
```

2. After `val (shownTab, onSelectTab) = rememberChosenTab(labels)` (`:166`), add:

```kotlin
    // The spread is a lazy item here, so its art-in is held by the page above
    // the list: a scroll away and back finds it played, a new visit plays it.
    val reveals = rememberReveals()
```

3. Replace (`:177-189`):

```kotlin
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "spread") {
            TitleSpread(
                backdropPath = firstEpisode?.backdropPath ?: firstEpisode?.posterPath ?: collection.posterPath,
                title = collection.name,
                facts = seriesFactsLine(facts, info, firstEpisode?.genres.orEmpty()),
                overview = info?.overview,
                tagline = info?.tagline,
                bleed = Spacing.large,
            ) {
```

with:

```kotlin
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "spread") {
            TitleSpread(
                backdropPath = firstEpisode?.backdropPath ?: firstEpisode?.posterPath ?: collection.posterPath,
                title = collection.name,
                facts = seriesFactsLine(facts, info, firstEpisode?.genres.orEmpty()),
                overview = info?.overview,
                tagline = info?.tagline,
                bleed = Spacing.large,
                reveals = reveals,
            ) {
```

In `CollectionScreen.kt`, after deleting `:108-245`:
- Imports. Add `androidx.compose.foundation.lazy.LazyListState` and `androidx.compose.foundation.lazy.rememberLazyListState`. Remove the ones only the moved code used:
  - `androidx.compose.foundation.layout.padding`
  - from `catalog`: `extentOf`, `firstItemOf`, `resumeWordsOf`, `seriesAboutFacts`, `seriesFactsLine`, `seriesResumeFor`, `showsOf`, `similarShows`, `summarize`, `walk`
  - `ui.common.catalog.rememberTitleCredits`
- KDoc (`:39-42`). After "…the same rule as [TitleDetailScreen]'s on the film side.", add:

```kotlin
 * [state] is the page's list, hoisted as a department page's is.
```

- Signature. After `onSelectSeason: (String) -> Unit = {},` (`:62`), add:

```kotlin
    state: LazyListState = rememberLazyListState(),
```

- Calls. Replace `:64-72`:

```kotlin
    if (collection.kind == CollectionKind.SHOW) {
        SeriesPage(
            collection, info, watch, heldIds, onOpenTitle, onOpenGenre, shelves, onOpenCollection,
            onOpenPerson, onPlay, editorsChoice, onToggleEditorsChoice, onToggleWatchlist,
            titleCredits, fetchPortrait, portraits, season, onSelectSeason,
        )
        return
    }
    CoursePage(collection, watch, heldIds, onPlay)
```

with:

```kotlin
    if (collection.kind == CollectionKind.SHOW) {
        SeriesPage(
            collection, info, watch, heldIds, onOpenTitle, onOpenGenre, shelves, onOpenCollection,
            onOpenPerson, onPlay, editorsChoice, onToggleEditorsChoice, onToggleWatchlist,
            titleCredits, fetchPortrait, portraits, season, onSelectSeason, state,
        )
        return
    }
    CoursePage(collection, watch, heldIds, onPlay, state)
```

- `CoursePage`. After `onPlay: (setId: String) -> Unit,` (`:89`), add `state: LazyListState,`, and change `LazyColumn(` (`:98`) to open with `state = state,` as its first argument.

`CollectionScreen.kt` drops to about 108 lines and `SeriesPage.kt` is about 172.

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.HeroArtInTest'`
Expected: PASS, 10 tests.

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. These suites wait for idle on an auto-advancing clock, so they read the art at rest:
- `WideTitleSpreadTest`, `DepartmentHeroTest`, `HeroBackdropModesTest`;
- `SeriesPageTest`, `CoursePageTest`;
- phase 03's `DepartmentRowRevealTest`, whose hero title doesn't move (only the art and the quote do).

- **If a "Back plays it again" test reads rest**, a flag is being restored from saved state. Check that no `rememberSaveable` holds it.
- **If the lazy tests read 1.04 after scrolling back**, the flag was made inside the lazy item. Check that the holder comes from the screen.
- **If `requestScrollToItem` is unresolved**, the foundation version is older than this plan assumes. Report it as NEEDS_CONTEXT rather than scrolling by gesture.

- [ ] **Step 10: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): title and department art settles in once a visit, its pull-quote 200ms behind"
```

## Task 5.2: A film's tab content rises in each time its tab is shown

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/TitleTabs.kt` (imports, `:68-85`). Not `TitleTabRow`, which is phase 07's.
- Test: `ui-mobile/src/test/kotlin/ui/catalog/title/TabPanelRiseTest.kt`

**Interfaces:**
- **Consumes:** `Modifier.entrance` (phase 01).
- **Produces:** `internal const val PANEL_IN_MS = 520` and `internal val PANEL_IN_RISE = 8.dp`, which Task 5.3 uses. `TitleTabs(labels, modifier, content)` keeps its signature.

No `seen`: `key(shown)` inside the film page's `Column` gives each showing a fresh node, which plays. A redraw keeps the node and the same key. Back composes the frame anew, so it plays again, as the web's un-hide does.

- [ ] **Step 1: Write the failing test**

Create `ui-mobile/src/test/kotlin/ui/catalog/title/TabPanelRiseTest.kt`:

```kotlin
package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.TitleTabs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `.tab-panel` rises 8px into place each time its tab is shown
 * (`title-page.css:133-134`, `tabs.js:85-97`): on arrival, on every switch,
 * and on a page come back to; never on a redraw. The layer's translation
 * shows in the body's bounds; its fade is the device walk's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabPanelRiseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top.value

    @Test fun eachTabsContentRisesInWhenShownAndARedrawLeavesItAtRest() {
        var unrelated by mutableStateOf(0)
        show {
            Column {
                Text("unrelated: $unrelated")
                TitleTabs(listOf("Overview", "Similar")) { tab -> Text("body: $tab") }
            }
        }
        val arriving = top("body: Overview")
        compose.mainClock.advanceTimeBy(600)
        val rest = top("body: Overview")
        assertTrue(arriving > rest + 5f, "the first panel rises in with the page: $arriving against $rest")

        compose.onNodeWithText("Similar").performClick()
        frames(2)
        assertTrue(top("body: Similar") > rest + 5f, "a newly chosen tab's content rises in")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest, top("body: Similar"), 0.5f)

        compose.onNodeWithText("Overview").performClick()
        frames(2)
        assertTrue(top("body: Overview") > rest + 5f, "and again on going back to a tab already seen")
        compose.mainClock.advanceTimeBy(600)

        compose.runOnUiThread { unrelated = 1 }
        frames(2)
        assertEquals(rest, top("body: Overview"), 0.5f, "a redraw never replays it")
    }

    /** The frame's saved slot keeps the chosen tab across Back; the rise must play again regardless. */
    @Test fun aPageComeBackToWithBackRisesItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("title") { TitleTabs(listOf("Overview", "Similar")) { tab -> Text("body: $tab") } }
        }
        compose.mainClock.advanceTimeBy(600)
        val rest = top("body: Overview")
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(top("body: Overview") > rest + 5f, "Back is a new visit, and the panel rises, as on the web")
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.TabPanelRiseTest'`
Expected: both tests fail at their first `assertTrue`; the body is at rest from the first frame.

- [ ] **Step 3: Wrap the content in a keyed entrance**

`TitleTabs.kt`. Add `androidx.compose.foundation.layout.Box`, `androidx.compose.runtime.key` and `designsystem.entrance`; `dp` is already imported.

Replace `:68-85` (as read on main):

```kotlin
/**
 * A title page's own tab row and body together — Overview/Cast/Similar/
 * Details on a film, whose bodies are all short enough to sit inside the
 * page's own scroll without needing a lazily-scrolled list of their own. A
 * Compose port of `tabs.js`'s own `tabbed`.
 */
@Composable
internal fun TitleTabs(
    labels: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val (shown, onSelect) = rememberChosenTab(labels)
    Column(modifier = modifier) {
        TitleTabRow(labels, shown, onSelect)
        content(shown)
    }
}
```

with:

```kotlin
/** `.tab-panel { animation: panel-in 520ms var(--ease) }`, from 8px below (`title-page.css:133-134`). */
internal const val PANEL_IN_MS = 520
internal val PANEL_IN_RISE = 8.dp

/**
 * A title page's own tab row and body together — Overview/Cast/Similar/
 * Details on a film, whose bodies are all short enough to sit inside the
 * page's own scroll without needing a lazily-scrolled list of their own. A
 * Compose port of `tabs.js`'s own `tabbed`.
 */
@Composable
internal fun TitleTabs(
    labels: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val (shown, onSelect) = rememberChosenTab(labels)
    Column(modifier = modifier) {
        TitleTabRow(labels, shown, onSelect)
        // The web replays the rise whenever a panel is shown (`tabs.js:85-97`
        // un-hides it). A new tab is a new key and so a new node, which plays;
        // a redraw keeps the key and the node; a page come back to is
        // composed anew and plays it again, as on the web.
        key(shown) {
            Box(Modifier.entrance(PANEL_IN_MS, fromY = PANEL_IN_RISE)) { content(shown) }
        }
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.*'`
Expected: PASS: `TabPanelRiseTest` (2), plus the existing `TitleTabsTest`, `FilmPageTest`, `SeriesPageTest` and `WideTitleSpreadTest`.

- [ ] **Step 5: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): a film's tab content rises in each time its tab is shown"
```

## Task 5.3: The series page's tab content rises too

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/SeriesPage.kt` (from Task 5.1: after the `reveals` line; the `when (shownTab)` block, moved from `CollectionScreen.kt:204-218`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/SeasonWall.kt` (`seriesEpisodes`, `:44-65`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/CollectionRows.kt` (`items`, `:29-50`)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/title/SeriesTabRiseTest.kt`

**Interfaces:**
- **Consumes:** `entrance` (phase 01), `PANEL_IN_MS` and `PANEL_IN_RISE` (Task 5.2), and `CollectionScreen`'s `state` (Task 5.1).
- **Produces:**
```kotlin
internal fun LazyListScope.seriesEpisodes(..., onPlay: (setId: String) -> Unit, modifier: Modifier = Modifier)
internal fun LazyListScope.items(rows, positions, watchedIds, heldIds, onPlay, modifier: Modifier = Modifier)
```
- `modifier` goes on every item each function adds. `CoursePage`'s call keeps the default.

**How the panel rises as one.** The web's `.tab-panel` is one block, but the series page builds it as lazy items: the season picker and hundreds of rows. So:
- **One flag per showing.** Every body item shares one flag, made anew each time a tab is shown: `remember(shownTab) { mutableStateOf(false) }`, above the list.
- **Together, then already in.** The items composed with the panel read it unplayed and rise together; the first one placed marks it played. A row scrolled to later is created after that and arrives at rest, as the rest of the web's risen panel already is.
- **Every switch.** Phase 01's `EntranceNode` starts over when handed a different flag, so a switch restarts even a node the list's pool hands back. That is what makes every switch rise; keying the items by tab wouldn't, since the pool hands nodes across keys.
- **Every visit.** The flag is held, not saved, so Back replays too.

- [ ] **Step 1: Write the failing test**

Create `ui-mobile/src/test/kotlin/ui/catalog/title/SeriesTabRiseTest.kt`:

```kotlin
package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.CollectionScreen
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The series page's tab panel rises 8px as one block each time its tab is
 * shown (`title-page.css:133-134`, `series-page.js:101`). Rows that arrive
 * with it rise together; rows scrolled to later are already in, and nothing
 * rises again on a scroll back. Lazy list: spread 0, tab row 1, season
 * heading 2, then the episodes, so "31. Ep-031" is item 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class SeriesTabRiseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private val series =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 40, chapters = 1,
            divisions =
                listOf(
                    Division(
                        "Season 1", 1,
                        (1..40).map { n ->
                            MediaSet(
                                setId = "s1e$n", kind = Kind.EPISODE, title = "Ep-%03d".format(n), show = "Show", chapter = null,
                                path = null, season = 1, episodeFirst = n, episodeLast = null, year = 2020, durationSecs = 1800,
                                posterPath = null, totalBytes = 10, backdropPath = null,
                            )
                        },
                        emptyList(),
                    ),
                ),
        )

    private val info = TitleInfo(overview = "Spice.", tagline = null, genres = null, rating = null, network = null, status = null)

    private fun page(list: LazyListState = LazyListState()) =
        show {
            CollectionScreen(
                collection = series, info = info, watch = WatchSnapshot.Empty, heldIds = emptySet(),
                onOpenTitle = {}, onOpenGenre = {}, state = list,
            )
        }

    @Test fun theEpisodesPanelRisesAsOneAndRowsScrolledToLaterAreAlreadyIn() {
        val list = LazyListState()
        page(list)
        val first = top("1. Ep-001")
        val second = top("2. Ep-002")
        compose.mainClock.advanceTimeBy(600)
        assertTrue(first > top("1. Ep-001") + 5f && second > top("2. Ep-002") + 5f, "the panel's rows rise together")

        compose.runOnUiThread { list.requestScrollToItem(33) }
        frames(2)
        val arrived = top("31. Ep-031")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrived, top("31. Ep-031"), 0.5f, "a row scrolled to later is already in")

        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        val back = top("1. Ep-001")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(back, top("1. Ep-001"), 0.5f, "scrolled back, nothing rises again")
    }

    @Test fun everySwitchToATabRisesItsPanelAgain() {
        page()
        compose.mainClock.advanceTimeBy(600)
        val rest = top("1. Ep-001")
        compose.onNodeWithText("About").performClick()
        frames(2)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Episodes").performClick()
        frames(2)
        assertTrue(top("1. Ep-001") > rest + 5f, "back on Episodes, its panel rises again")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest, top("1. Ep-001"), 0.5f)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.SeriesTabRiseTest'`
Expected: both tests fail at their first `assertTrue`; the rows are at rest from the first frame.

- [ ] **Step 3: `items` and `seriesEpisodes` take a modifier for every item they add**

`CollectionRows.kt`. Add `import androidx.compose.foundation.layout.Box`. Replace `:29-50`:

```kotlin
internal fun LazyListScope.items(
    rows: List<CollectionRow>,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
) {
    items(
        count = rows.size,
        key = { index -> keyOf(rows[index], index) },
    ) { index ->
        when (val row = rows[index]) {
            is CollectionRow.Heading -> Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = indentOf(row.depth), top = Spacing.medium),
            )

            is CollectionRow.Item -> ItemRow(row, positions[row.set.setId], row.set.setId in watchedIds, row.set.setId in heldIds, onPlay)
        }
    }
}
```

with:

```kotlin
/** [modifier] goes on every row, so a show's Episodes panel rises as one. */
internal fun LazyListScope.items(
    rows: List<CollectionRow>,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    items(
        count = rows.size,
        key = { index -> keyOf(rows[index], index) },
    ) { index ->
        Box(modifier) {
            when (val row = rows[index]) {
                is CollectionRow.Heading -> Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = indentOf(row.depth), top = Spacing.medium),
                )

                is CollectionRow.Item -> ItemRow(row, positions[row.set.setId], row.set.setId in watchedIds, row.set.setId in heldIds, onPlay)
            }
        }
    }
}
```

`SeasonWall.kt`. Add `import androidx.compose.foundation.layout.Box`. In the KDoc, after the paragraph ending "…as the chosen tab is ([rememberChosenTab]).", add:

```kotlin
 *
 * [modifier] goes on every item this adds: the Episodes panel's rise.
```

Replace the function `:49-65`:

```kotlin
internal fun LazyListScope.seriesEpisodes(
    collection: Entry.Collection,
    season: String?,
    onSelectSeason: (String) -> Unit,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
) {
    val shown = collection.divisions.find { it.title == season } ?: collection.divisions.firstOrNull() ?: return
    if (collection.divisions.size > 1) {
        item(key = "season-picker") { SeasonPicker(collection.divisions, shown, onSelectSeason) }
    }
    val rows = rowsOf(listOf(shown))
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    items(rows, positions, watchedIds, heldIds, onPlay)
}
```

with:

```kotlin
internal fun LazyListScope.seriesEpisodes(
    collection: Entry.Collection,
    season: String?,
    onSelectSeason: (String) -> Unit,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = collection.divisions.find { it.title == season } ?: collection.divisions.firstOrNull() ?: return
    if (collection.divisions.size > 1) {
        item(key = "season-picker") { Box(modifier) { SeasonPicker(collection.divisions, shown, onSelectSeason) } }
    }
    val rows = rowsOf(listOf(shown))
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    items(rows, positions, watchedIds, heldIds, onPlay, modifier)
}
```

`SeasonPicker` itself is untouched; phase 07 replaces its menu, finding it by text.

- [ ] **Step 4: The series page gives the shown tab's items one shared entrance**

`SeriesPage.kt`. Add these imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.runtime.mutableStateOf` and `designsystem.entrance`. After Task 5.1's `val reveals = rememberReveals()`, add:

```kotlin
    // The shown tab's panel, as the web's one `.tab-panel` (`title-page.css:133-134`):
    // its items share one flag, made anew each time a tab is shown. The items
    // that arrive with the panel rise together; rows scrolled to later are
    // already in. A new flag restarts even a node the list hands back, so every
    // switch rises again (`tabs.js:85-97`); held, not saved, so Back does too.
    val panelIn = remember(shownTab) { mutableStateOf(false) }
    val panel = Modifier.entrance(PANEL_IN_MS, fromY = PANEL_IN_RISE, seen = panelIn)
```

Replace the `when (shownTab)` block (moved from `CollectionScreen.kt:204-218`):

```kotlin
        when (shownTab) {
            "Episodes" -> seriesEpisodes(collection, shownSeason, onSelectSeason, watch, heldIds, onPlay)
            "Cast" ->
                item(key = "cast") {
                    CastPanel(credits, onOpenPerson, fetchPortrait, portraits)
                }
            "Similar" ->
                item(key = "similar") {
                    SeriesSimilarTab(collection, watch, shelves, onOpenCollection)
                }
            else ->
                item(key = "about") {
                    FactSheet(factRows(seriesAboutFacts(facts, info, firstEpisode?.genres.orEmpty()), onOpenGenre))
                }
        }
```

with:

```kotlin
        when (shownTab) {
            "Episodes" -> seriesEpisodes(collection, shownSeason, onSelectSeason, watch, heldIds, onPlay, modifier = panel)
            "Cast" ->
                item(key = "cast") {
                    Box(panel) { CastPanel(credits, onOpenPerson, fetchPortrait, portraits) }
                }
            "Similar" ->
                item(key = "similar") {
                    Box(panel) { SeriesSimilarTab(collection, watch, shelves, onOpenCollection) }
                }
            else ->
                item(key = "about") {
                    Box(panel) { FactSheet(factRows(seriesAboutFacts(facts, info, firstEpisode?.genres.orEmpty()), onOpenGenre)) }
                }
        }
```

`SeriesPage.kt` ends at about 184 lines.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.*' --tests 'ui.catalog.CoursePageTest'`
Expected: PASS. That includes `SeriesTabRiseTest` (2), plus `SeriesPageTest`, `WideTitleSpreadTest` and `CoursePageTest`. A course's rows take the default `modifier`, so they don't move.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): a show's tab content rises as one panel each time its tab is shown"
```

## Success criteria

- **Tests.** `HeroArtInTest` (10), `TabPanelRiseTest` (2) and `SeriesTabRiseTest` (2) pass, and `:ui-mobile:testDebugUnitTest` stays green.
- **What moves.**
  - At the four hero call sites, the picture alone settles from 1.04 over 1400 ms, clipped.
  - The two pull-quotes follow 200 ms later.
  - A tab's content rises 8dp over 520 ms, on the film page and the series page.
  - The Home cover is untouched.
- **When it moves:**

| Plays | Never plays |
|---|---|
| Any page visit: a newly opened title or department, a tab switch, Back. | A redraw. |
| | A sync. |
| | A lazy hero, or a series row, scrolled away and back. |
| | Rows scrolled to after their panel rose. |
| | A rotation across the wide breakpoint. |

- **Storage.** No flag in this phase is in saved state: `git diff main -- ui-mobile/src/main | grep -n rememberSaveable` shows nothing added.
- **Line counts.** Every file this phase creates or edits is under 200 lines.
- **Device walk** (phase 09, tablet `caad49da`), for the fades that can't be tested here:
  - the picture fades up from the page ground and the quote follows;
  - Blurred art settles without a stutter;
  - Back replays the hero and the tab panel;
  - rotating the tablet replays nothing;
  - with "Remove animations" on, everything shows at once at rest.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| A `RenderEffect` blur re-renders under the scaling layer for 1.4 s on each page open (Blurred). | M×L | It is a one-shot, not continuous. Phase 09's `gfxinfo` run on the release build covers it. If it janks, the art-in skips scale under Blurred and that gets written down. |
| Hundreds of Episodes rows each get an `EntranceNode` and a `Box`. | L×L | Only composed rows exist, and once the panel has risen each is at rest from creation, with no animation running. |
| Phase 07 edits `TitleTabs.kt` and `SeasonWall.kt` too. | L×L | It runs after this phase and finds its lines by text (`phase-07-…md:3`). The functions differ: `TitleTabRow`, and `SeasonPicker`'s menu. |

**Rollback:** each task is one commit.
- Reverting 5.1 restores `DeptQuote` and `SeriesPage` to their old files, along with the snap.
- Reverting 5.2 removes the keyed `Box`.
- Reverting 5.3 removes the shared flag and the two `modifier` parameters.
