# Phase 03 — Page turns and section reveals

**Spec:** `../spec.md` §2 (Changing page; Home: Sections). **Inventory:** rows #1, #2; §2b (redraw, list changes). **Status:** pending.
**Depends on:** 01. Also runs after 02: its button rewrite touches `chrome/AppChrome.kt` and `catalog/MoviesDepartmentScreen.kt`, and the code below shows those files as 02 leaves them (`CatalogueTextButton`).

## Context

Web sources: page turn `web/public/styles/shell.css:162-163`, `web/public/lib/page-turn.js:33-45`, `web/public/app.js:629-634`; reveal `web/public/styles/theme.css:208-209`, `web/public/lib/reveal.js:15-18,27-45`; reveal call sites `home-view.js:47,85,98`, `home-features.js:32`, `department-hero.js:46`, `collections-page.js:79,87,90`.

**Key insights (each verified against the code):**

1. **The web turns `main` and nothing else.** The rail and masthead sit outside it (`web/public/index.html:54,72` against `<main>` at `:97`). So the turn goes on the page slot under the chrome:
   - `LibraryScaffold`'s content box (`chrome/AppChrome.kt:149`), which every pushed frame but Settings/System renders through (`AppChrome.kt:52-55`);
   - the three `SettingsScreen` branches (`LibraryMenuBranch.kt:38-62`), which draw no bar;
   - the shelves' tab content (`catalog/CatalogScreen.kt:153-204`).

   It doesn't go around `when (at.top)` (`LibraryFlowBranches.kt:135`), which is the inventory's proposed site: that would lift the rail and the bar with the page.
2. **A fresh node per page is already there.**
   - `keyedFrame` keys every pushed frame (`LibraryBranchSupport.kt:128`).
   - Search keys on depth and kind, never on the query (`ui-common/.../LibraryPositions.kt:173`). Typing never re-keys, which is the web's "refining" rule (`app.js:631`).
   - The player renders no `LibraryScaffold` (`LibraryFlowBranches.kt:144-155`): still, like the web's `<dialog>`.
   - Picking a season re-keys the collection frame, because its payload carries the season (`LibraryPositions.kt:173,256-262`). The web navigates there too (`series-page.js:56` → `open(…)`), so it turns on both surfaces.
3. **The tab switch can't be keyed in `LibraryHome`.** Keying `content()` (`chrome/LibraryHome.kt:171`) would dispose `CatalogScreen` and its `SaveableStateProvider(SHELVES_KEY)` (`LibraryFlowBranches.kt:235`): every `remember` under the shelves is lost and the saved slot is re-entered mid-frame.
   - Each tab is already its own `when` branch group (`CatalogScreen.kt:153-204`, `DepartmentTab.kt:38-62`). `key(chosenTab.key)` around that `when` therefore loses nothing it doesn't already lose.
   - The key is the `String` (`feature/catalog/.../CatalogTabs.kt:17`), because `CatalogTab.Dept` hashes an enum, which isn't stable across a restored process.
   - `CatalogScreen.kt` is already 206 lines, so `Shelves` moves to its own file.
4. **Loading-to-loaded swaps would turn the page twice.** `ResolvedBranch` (`LibraryResolvedBranch.kt:34-43`) and `PersonFrame` (`LibraryBrowseBranches.kt:210-227`) call `LibraryBranch` from different `when` branches. When the content resolves, a new scaffold, and so a new turn, replaces the loading one. A person page does this on every visit. Each gets one call site.
5. **"Once per page visit" means a page-held flag, not `rememberSaveable` in the item.**
   - The web re-arms the reveal on every arrival and skips it only on a redraw of the same hash (`reveal.js:32-45`).
   - A `rememberSaveable` inside a Home lazy item sits under the `SHELVES_KEY` provider. That provider saves it when a title is pushed over Home (`LibraryFlowBranches.kt:235`), so Back would reveal nothing, while a tab switch would reveal everything (`HomeScreen` leaves composition). That inverts the web.
   - So each screen keeps a `Reveals` map in a plain `remember`. Scrolling back and a sync both leave the screen composed, so neither replays.
6. **Phase 01's `EntranceNode` sticks half-faded when a lazy list reuses it.** Verified in the ui-android 1.11.4 bytecode:
   - `LayoutNode.onDeactivate` → `resetModifierState` → `NodeChain.resetState` calls `Node.reset()`, then detaches;
   - `onReuse` re-attaches the same node instances (`markAsAttached`, `runAttachLifecycle`, `rescheduleRemeasureOrRelayout`).

   A section flung off screen mid-rise keeps its cancelled `Animatable`s and `started = true`, so it comes back stuck. A node handed to another key never reveals that item. Phase 01's `Entrance.kt` and its `EntranceReuseTest` now carry the fix (moved there from this phase).
7. **Lazy keys.** Home's are stable (`HomeScreen.kt:89-130`). Movies (`MoviesDepartmentScreen.kt:78-110`) and Documentaries (`DocumentariesDepartmentScreen.kt:106,118-128,142`) have unkeyed items. Every revealed item gets a key here.
8. **Reveal granularity.** `home-view.js` reveals the resume section and the quote apart (`:81,85`), and Recently Added and This month apart (`:92,98`). Android draws each pair as one lazy item (`ContinueBand`, `RecentBand`), and a lazy list places an item whole, so each pair rises as one. On a wide window the web's halves arrive together anyway.
9. **The web's franchise page carries `.reveal` but never arms it.** `deptRow` is at `collections-page.js:115`, and `renderFranchise` has no `revealWithin`, so a page reached after one that armed `.motion` hides that row (a web bug). `FranchiseScreen` gets no reveal here.

**Rulings:**
- **Turn placement:** insight 1.
- **Reveal flag:** a page-held flag, not a saved one (insight 5). This changes the mechanism phase 01 suggested for `seen`, not the spec's behaviour.
- **Collections tab:** included. It is the same `deptRow` on the web (`collections-page.js:79,87,90`).
- **Rotation replays nothing:** `MainActivity` handles orientation itself (`app/src/main/AndroidManifest.xml:59`), so turning the tablet keeps the window and every node. A window rebuilt from saved state (the process restored, a profile chosen, the first load) does draw its first page anew. Marked `ponytail:` in code.

**Tests:** Robolectric with a paused `mainClock`. Motion is read from layer-mapped bounds: `getBoundsInRoot`, or `getUnclippedBoundsInRoot` where an ancestor clips. Alpha is drawn, and this setup can't capture pixels (`ui-mobile/src/test/kotlin/ui/PageGroundColorTest.kt`), so fades are left to the phase 09 walk.

## Task 3.1: The page turns when the viewer goes somewhere

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/chrome/PageTurn.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/AppChrome.kt` (the `LibraryScaffold` content box)
- Modify: `ui-mobile/src/main/kotlin/ui/LibraryMenuBranch.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt` (`Shelves` moves out)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/CatalogShelves.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/LibraryResolvedBranch.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt` (`PersonFrame` only)
- Test: `ui-mobile/src/test/kotlin/ui/PageTurnFlowTest.kt`

**Interfaces:**
- **Consumes:** `Modifier.entrance(durationMs, fromY = …)` (phase 01).
- **Produces:** `internal fun Modifier.pageTurn(): Modifier` and `internal const val PAGE_TURN_TEST_TAG = "page-turn"`, in package `ui.chrome`.

- [ ] **Step 1: Write the failing test**

Create `ui-mobile/src/test/kotlin/ui/PageTurnFlowTest.kt`:

```kotlin
package ui

import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.PAGE_TURN_TEST_TAG

/**
 * The page under the chrome rises 10dp into place when the viewer goes
 * somewhere (`shell.css:162-163`), and only then: not while a search is
 * typed, not for the player, not a second time when a page's catalog
 * arrives after it opened. Read from the page's layer-mapped bounds on a
 * paused clock; its fade is drawn, and nothing here captures pixels (see
 * PageGroundColorTest), so the fade is the device walk's to check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class PageTurnFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before fun open() {
        // The ViewModels are held in the fixture's store; only Hilt's
        // generated-Activity factory lookup needs replacing in Robolectric.
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    private fun pageTop() = compose.onNodeWithTag(PAGE_TURN_TEST_TAG).getBoundsInRoot().top.value

    /** One frame composes and places the new page; the next starts its entrance. */
    private fun twoFrames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun choosingADepartmentTurnsThePage() {
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        twoFrames()
        val rising = pageTop()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(rising - pageTop() > 5f, "the tab should start below its place, was ${rising - pageTop()}dp")
    }

    @Test fun openingSearchTurnsThePageButTypingInItDoesNot() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search").performClick()
        twoFrames()
        val rising = pageTop()
        compose.mainClock.advanceTimeBy(500)
        val rest = pageTop()
        assertTrue(rising - rest > 5f, "search should rise in, was ${rising - rest}dp")
        compose.onNode(hasSetTextAction()).performTextInput("Exa")
        twoFrames()
        assertEquals(rest, pageTop(), 0.5f)
    }

    @Test fun thePlayerOpensWithNoPageTurn() {
        title()
        compose.onNodeWithText("▶ Play").performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        compose.onNodeWithTag(PAGE_TURN_TEST_TAG).assertDoesNotExist()
    }

    /** A restored title shows "Loading…" until the catalog arrives; the page it then shows is the same page, already turned. */
    @Test fun aTitleWhoseCatalogArrivesAfterItOpenedTurnsOnlyOnce() {
        title()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        val rest = pageTop()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("▶ Play").assertExists()
        assertEquals(rest, pageTop(), 0.5f)
    }

    /** As LibraryFlowTest's own: a poster tap on the Movies tab opens the film's page. */
    private fun title() {
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        compose.onAllNodes(hasText("Example Film") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    /** As LibraryFlowTest's own: the process dies with the stack saved, and comes back before the catalog has loaded. */
    private fun restoreWhileLoading() {
        compose.runOnUiThread {
            val saved = Bundle()
            controller.saveInstanceState(saved).pause().stop().destroy()
            fixture.close()
            fixture = LibraryFlowFixture(loading = true)
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).create(saved).start().resume().visible()
        }
        compose.waitForIdle()
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.PageTurnFlowTest'`
Expected: compilation fails, `PAGE_TURN_TEST_TAG` unresolved.

- [ ] **Step 3: Write `ui-mobile/src/main/kotlin/ui/chrome/PageTurn.kt`**

```kotlin
package ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import designsystem.entrance

/** The page on screen, for a test to read its turn from: one at a time, none under the player. */
internal const val PAGE_TURN_TEST_TAG = "page-turn"

/** `main.turning { animation: page-in 420ms var(--ease) }` (`shell.css:162`). */
private const val PAGE_TURN_MS = 420

/** `@keyframes page-in { from { opacity: 0; transform: translateY(10px) } }` (`shell.css:163`). */
private val PAGE_TURN_RISE = 10.dp

/**
 * A page gone to rises into place and fades in, as the web's `main` does on
 * every navigation (`page-turn.js:33-45`). It goes on the page under the
 * chrome, never on the rail or the bar: the web's rail and masthead sit
 * outside `main` (`index.html:54,72,97`) and stay put while it turns.
 *
 * The node is new with each page, because every pushed frame and every tab
 * is keyed, so it plays once per arrival and never on a redraw of the page
 * showing. The old page goes at once with its key, so nothing cross-fades.
 * Search is keyed by its frame, not its query, so typing is never a turn
 * (`app.js:631`). The player has none: the web's player `<dialog>` opens still.
 */
internal fun Modifier.pageTurn(): Modifier {
    // ponytail: a window rebuilt from saved state (the process restored, a
    // profile chosen, the first load) composes a new node, so its first page
    // rises too; the web's first draw turns nothing. A latch in
    // LibraryBranches that marks the first page still is the upgrade.
    return entrance(PAGE_TURN_MS, fromY = PAGE_TURN_RISE).testTag(PAGE_TURN_TEST_TAG)
}
```

- [ ] **Step 4: Turn the pushed frame's page, not its bar**

In `ui-mobile/src/main/kotlin/ui/chrome/AppChrome.kt` (line 149 as read, two lines higher after phase 02 drops two material3 imports), replace:

```kotlin
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { content() }
```

with:

```kotlin
                // The page turns under the bar, never the bar or the rail, as
                // the web's turn moves `main` and leaves the masthead put.
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).pageTurn()) { content() }
```

`pageTurn` is in the same package, so no import. The file goes from 193 to 195 lines.

- [ ] **Step 5: Turn Settings and System, which have no bar**

In `ui-mobile/src/main/kotlin/ui/LibraryMenuBranch.kt`:
- add the imports `androidx.compose.foundation.layout.Box`, `androidx.compose.ui.Modifier` and `ui.chrome.pageTurn`;
- replace the `when (menuScreen) { … }` block (lines 37-68) with:

```kotlin
    when (menuScreen) {
        // Settings and System draw no bar of their own, so the whole screen
        // is the page, and it turns as the web's settings page does.
        MenuScreen.System ->
            Box(Modifier.pageTurn()) {
                SettingsScreen(
                    initial = SettingsSection.SYSTEM,
                    leavesFromSection = true,
                    tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                    onLeave = at::pop,
                )
            }

        MenuScreen.Settings ->
            Box(Modifier.pageTurn()) {
                SettingsScreen(
                    initial = null,
                    leavesFromSection = false,
                    tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                    onLeave = at::pop,
                )
            }

        // A film page's own "Raise the cache budget" link — the
        // same direct-section shape MenuScreen.System already uses.
        MenuScreen.Storage ->
            Box(Modifier.pageTurn()) {
                SettingsScreen(
                    initial = SettingsSection.STORAGE,
                    leavesFromSection = true,
                    tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                    onLeave = at::pop,
                )
            }

        // Through LibraryBranch's scaffold, which turns its own page.
        MenuScreen.TmdbKey ->
            LibraryBranch(destinationOf(menuScreen), menuActions, profileBar, browse, at, at::pop) {
                TmdbKeyScreen(hasKey = fetchState.hasKey, onSave = fetchViewModel::saveKey)
            }
    }
```

- [ ] **Step 6: Move `Shelves` into its own file, and turn the page per tab**

In `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt`:
- replace lines 1-30 (the package and imports) with the block below;
- keep lines 31-99 (the `CatalogScreen` KDoc and function) exactly as they are;
- delete lines 100-206 (the blank line, then `Shelves` and its KDoc). The file ends at the `}` of `CatalogScreen`.

```kotlin
package ui.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.magazineHomeOf
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo
```

`magazineHomeOf` stays imported for the `[magazineHomeOf]` KDoc link on `homeListState`.

Create `ui-mobile/src/main/kotlin/ui/catalog/CatalogShelves.kt`. It is `Shelves` exactly as it was (now `internal`), plus the keyed page at its foot:

```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.HOME_POSTER_ROW_LIMIT
import catalog.KeptKind
import catalog.allSetsById
import catalog.continueWall
import catalog.everyFilm
import catalog.franchisesIn
import catalog.hasContent
import catalog.latestOf
import catalog.magazineHomeOf
import catalog.watchlistWall
import designsystem.Spacing
import ui.chrome.LocalTopChrome
import ui.chrome.pageTurn
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo

/**
 * One tab on screen, chosen from the bar above it — Home, then each
 * department as its own page ([DepartmentTab]), then Collections (see
 * [CollectionsScreen]). Continue and My List draw with [KeptWall], reached
 * from the rail's own rows (`ui.chrome.BrowseActions`) rather than a visible pill.
 */
@Composable
internal fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    chosenTab: CatalogTab,
    onTabChange: (CatalogTab) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onOpenMoviesPage: () -> Unit,
    onOpenFranchise: (Long) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    onFinish: (setId: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    homeListState: LazyListState,
    deptScroll: DepartmentScrollStates,
    now: Long,
    titleInfo: suspend (String) -> TitleInfo?,
) {
    val shelves = state.shelves
    if (!shelves.hasContent()) {
        CenteredMessage("The library is empty.")
        return
    }
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    // Home, and a department whose own hero drew lead art, both draw under
    // the bar — everything else, and a hero with nothing to lead with, is
    // padded clear of it, the same seam `ui.chrome.LibraryHome` reads to decide which.
    val topChrome = LocalTopChrome.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = topChrome))
        }
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small).padding(top = topChrome),
            )
        }
        // Each tab is a page of its own on the web (`#/movies`, `#/continue`),
        // so choosing one turns the page. Keyed by the tab's saved key, the
        // one a restored window files its state under; a tab is already its
        // own group below, so the key costs no state. The progress line and
        // notice above belong to no page and stay put.
        key(chosenTab.key) {
            Box(Modifier.pageTurn()) {
                when (chosenTab) {
                    CatalogTab.Home -> HomeScreen(
                        magazine = remember(shelves, state.watch, state.heldIds, now) {
                            magazineHomeOf(
                                shelves, state.watch, editorsChoice = state.watch.editorsChoice, now = now, heldIds = state.heldIds,
                                recentLimit = HOME_POSTER_ROW_LIMIT,
                            )
                        },
                        latest = remember(shelves) { latestOf(shelves) },
                        watch = state.watch,
                        listState = homeListState,
                        onPlay = { id -> onPlayRun(id, emptyList()) },
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onToggleWatchlist = onToggleWatchlist,
                        onSeeAll = onTabChange,
                    )

                    is CatalogTab.Kept -> when (chosenTab.kind) {
                        KeptKind.CONTINUE -> KeptWall(KeptKind.CONTINUE, continueWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds, onFinish)
                        KeptKind.WATCHLIST -> KeptWall(KeptKind.WATCHLIST, watchlistWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds)
                        KeptKind.COLLECTIONS -> {
                            val movies = remember(shelves) { everyFilm(shelves) }
                            CollectionsScreen(
                                franchises = remember(movies) { franchisesIn(movies) },
                                lists = state.watch.collections,
                                setsById = remember(shelves) { allSetsById(shelves) },
                                onOpenFranchise = onOpenFranchise,
                                onOpenList = onOpenList,
                                onCreateList = onCreateList,
                                onOpenTitle = onOpenTitle,
                            )
                        }
                    }

                    is CatalogTab.Dept -> shelves.firstOrNull { it.department == chosenTab.department }?.let { shelf ->
                        DepartmentTab(
                            shelf = shelf,
                            state = state,
                            columns = columns,
                            deptScroll = deptScroll,
                            onOpenTitle = onOpenTitle,
                            onOpenCollection = onOpenCollection,
                            onOpenGenre = onOpenGenre,
                            onOpenGenresIndex = onOpenGenresIndex,
                            onOpenLatest = onOpenLatest,
                            onOpenMoviesPage = onOpenMoviesPage,
                            onPlay = { id -> onPlayRun(id, emptyList()) },
                            titleInfo = titleInfo,
                        )
                    }
                }
            }
        }
    }
}
```

The two files come to about 100 and 140 lines. `LibraryHome.kt` is untouched by this phase.

- [ ] **Step 7: One scaffold per frame, so a page that resolves late doesn't turn twice**

Replace the whole body of `ResolvedBranch` in `ui-mobile/src/main/kotlin/ui/LibraryResolvedBranch.kt` (lines 34-43, the `when (val outcome = …) { … }`) with:

```kotlin
    val outcome = resolveFrame(resolved, catalogState is CatalogUiState.Ready)
    if (outcome == FrameResolution.Stale) {
        LaunchedEffect(Unit) { at.pop() }
        return
    }
    // One scaffold whichever the frame shows, so a page whose catalog
    // arrives after it opened keeps the turn it opened with rather than
    // rising in a second time.
    LibraryBranch(if (outcome is FrameResolution.Resolved) destinationOf(outcome.value) else loading, menu, profile, browse, at, at::pop) {
        if (outcome is FrameResolution.Resolved) content(outcome.value) else CenteredMessage("Loading your library…")
    }
```

In `ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt`, `PersonFrame`, replace lines 210-227 (from `when {` to its closing `}`) with the block below. The file is already 232 lines and this keeps it there, at 18 lines for 18.

```kotlin
    val loading = lookup.loading || shelves == null
    val page = remember(person, shelves, loading) { if (loading || shelves == null) null else personPageOf(person, shelves) }
    val destination = when {
        loading -> Destination.Person(LOADING)
        page == null -> Destination.Person("Person")
        else -> Destination.Person(page.person.name)
    }
    // One scaffold whichever it shows, so the page keeps its turn when the lookup answers.
    LibraryBranch(destination, menuActions, profileBar, browse, at, at::pop) {
        when {
            loading -> CenteredMessage("Loading your library…")
            page == null -> CenteredMessage("Nobody by that number is credited on anything in your library.")
            else -> {
                val portrait = rememberPortrait(id, page.person.portraitPath, browseViewModel.portraits, browseViewModel::fetchPortrait)
                PersonScreen(page, portrait, watch, columns, at::openTitle, at::openCollection)
            }
        }
    }
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. That covers the 4 new tests, plus `LibraryFlowTest` (the person and restored-title paths), `WidthClassStateTest` (the `LibraryScaffold` content is still one call site) and `HomeScreenTest`.

**If `aTitleWhoseCatalogArrivesAfterItOpenedTurnsOnlyOnce` can't find "▶ Play":** the catalog read needs more frames to land. Raise the `repeat`; never add a sleep.

- [ ] **Step 9: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): a page gone to rises into place under the chrome, as the web's does"
```

## Task 3.2: Home's sections rise into view, once a visit

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/catalog/SectionReveal.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt` (lines 81-138)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/HomeSectionRevealTest.kt`

**Interfaces:**
- **Consumes:** `entrance(…, seen = …)` (phase 01).
- **Produces** (package `ui.catalog`):

```kotlin
internal class Reveals { fun of(key: String): MutableState<Boolean> }
@Composable internal fun rememberReveals(): Reveals
internal fun Modifier.reveal(reveals: Reveals, key: String): Modifier
internal fun LazyListScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit)
```

- [ ] **Step 1: Write the failing Home tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/HomeSectionRevealTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.CollectionKind
import catalog.EditorialPicks
import catalog.Entry
import catalog.Feature
import catalog.FeatureKind
import catalog.Latest
import catalog.MagazineHome
import catalog.SetCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import ui.catalog.home.HOME_COVER_TEST_TAG
import ui.catalog.home.HOME_FEATURES_TEST_TAG

/**
 * Home's sections rise 28dp into place the first time each is placed on a
 * visit, and never again on that visit: not when flung away and back, not
 * when a sync redraws them with the same keys. The cover carries none.
 *
 * A phone window, where the cover fills most of the first screen and the
 * features start just under it. The features block rests 12dp below the
 * cover's foot (its own top padding in HomeScreen), so its offset from rest
 * is read against the cover rather than a remembered number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeSectionRevealTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val list = LazyListState()

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String, tagline: String? = null) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 3_600, posterPath = "p-$id.jpg",
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    private val magazine = MagazineHome(
        editorial = EditorialPicks(
            cover = listOf(film("cover")),
            features = listOf(Feature(FeatureKind.EDITOR, film("feature"))),
            quote = film("quote", tagline = "A quotable line."),
            thisMonth = listOf(film("month")),
        ),
        resumeCards = listOf(SetCard(film("resume"), "1h left", 0.4f, false, false)),
        recentlyAdded = listOf(film("recent")),
        recentlyAddedTotal = 1,
    )
    private val latest = Latest(
        movies = emptyList(),
        series = listOf(Entry.Collection("s1", CollectionKind.SHOW, "Show One", null, null, count = 10, chapters = 2, divisions = emptyList())),
        courses = listOf(Entry.Collection("c1", CollectionKind.COURSE, "Course One", null, null, count = 8, chapters = 1, divisions = emptyList())),
        moviesTotal = 0,
        seriesTotal = 1,
        coursesTotal = 1,
    )
    private val shown = mutableStateOf(magazine)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    HomeScreen(
                        magazine = shown.value, latest = latest, watch = WatchSnapshot.Empty, listState = list,
                        onPlay = {}, onOpenTitle = {}, onOpenCollection = {}, onToggleWatchlist = { _, _ -> }, onSeeAll = {},
                    )
                }
            }
        }
        twoFrames()
    }

    private fun twoFrames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun scrollTo(index: Int) {
        compose.runOnUiThread { list.requestScrollToItem(index) }
        twoFrames()
    }

    private fun coverTop() = compose.onNodeWithTag(HOME_COVER_TEST_TAG).getUnclippedBoundsInRoot().top.value
    private fun coverBottom() = compose.onNodeWithTag(HOME_COVER_TEST_TAG).getUnclippedBoundsInRoot().bottom.value
    private fun featuresTop() = compose.onNodeWithTag(HOME_FEATURES_TEST_TAG).getUnclippedBoundsInRoot().top.value
    private fun topOf(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    /** How far the features block sits below its rest, 12dp under the cover. */
    private fun featuresOffset() = featuresTop() - (coverBottom() + 12f)

    @Test
    fun aSectionRisesAsItArrivesButTheCoverDoesNot() {
        show()
        assertEquals(0f, coverTop(), 0.5f)
        assertTrue(featuresOffset() > 20f, "the features should start well below their place, were ${featuresOffset()}dp")
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(0f, featuresOffset(), 0.5f)
    }

    @Test
    fun aSectionScrolledToLaterRisesWhenItArrivesNotWhenItWasComposed() {
        show()
        compose.mainClock.advanceTimeBy(1_500)
        scrollTo(1)
        val arriving = topOf("Continue Watching")
        compose.mainClock.advanceTimeBy(1_200)
        assertTrue(arriving - topOf("Continue Watching") > 20f, "Continue should rise as it arrives")
    }

    /** A fast fling down Home and back up: the section left mid-rise is at rest on the way back, neither replayed nor stuck. */
    @Test
    fun aFlingDownAndBackLeavesTheSectionsAtRest() {
        show()
        compose.mainClock.advanceTimeBy(200)
        assertTrue(featuresOffset() > 5f, "still rising when flung away")
        scrollTo(5)
        scrollTo(0)
        assertEquals(0f, featuresOffset(), 0.5f)
    }

    /** A sync that lands with the same sections redraws them where they are. */
    @Test
    @Config(qualifiers = "w1164dp-h4000dp")
    fun aSyncThatKeepsTheSectionsReplaysNone() {
        show()
        compose.mainClock.advanceTimeBy(1_500)
        val before = listOf(featuresTop(), topOf("Continue Watching"), topOf("Latest series"))
        compose.runOnUiThread {
            shown.value = magazine.copy(editorial = magazine.editorial.copy(quote = film("quote", tagline = "Another line.")))
        }
        twoFrames()
        compose.onNodeWithText("Another line.", substring = true, useUnmergedTree = true).assertExists()
        val after = listOf(featuresTop(), topOf("Continue Watching"), topOf("Latest series"))
        before.zip(after).forEach { (was, now) -> assertEquals(was, now, 0.5f) }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.HomeSectionRevealTest'`
Expected: FAIL.
- `aSectionRisesAsItArrivesButTheCoverDoesNot` and `aSectionScrolledToLaterRisesWhenItArrivesNotWhenItWasComposed` read an offset of 0: nothing rises yet.
- The fling and sync tests pass already, since nothing moves. They pin behaviour the next step must keep.

- [ ] **Step 3: Write `ui-mobile/src/main/kotlin/ui/catalog/SectionReveal.kt`**

```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.entrance

/** `.motion .reveal`: rises over 1100ms and fades in over 900ms, both on `--ease` (`theme.css:208`). */
private const val REVEAL_RISE_MS = 1_100
private const val REVEAL_FADE_MS = 900
private val REVEAL_FROM = 28.dp

/**
 * The sections of one page that have come into view on this visit, by key:
 * the web's `.reveal.in` (`reveal.js:38-45`).
 *
 * Held by the page's own composition, not by its lazy items and not in
 * saved state. A lazy item scrolled away and back is composed anew and finds
 * its section here, so it comes back at rest. A page left and come back to
 * is a new visit, which the web reveals afresh (`reveal.js:32`); the shelves'
 * saved state outlives a title opened over them, so a saved flag would not.
 */
internal class Reveals {
    private val seen = HashMap<String, MutableState<Boolean>>()

    fun of(key: String): MutableState<Boolean> = seen.getOrPut(key) { mutableStateOf(false) }
}

/** One [Reveals] for as long as the page is on screen. */
@Composable
internal fun rememberReveals(): Reveals {
    // ponytail: a window rebuilt from saved state (the process restored)
    // rebuilds this with it, so the sections in view rise again. Rotation
    // doesn't: MainActivity handles orientation itself.
    return remember { Reveals() }
}

/**
 * The web's scroll reveal (`theme.css:208-209`): a section rises 28dp and
 * fades in the first time it comes into view on a visit. It starts when the
 * lazy list first places the item, never when it composes one ahead of the
 * viewport. Scrolled back to, or redrawn by a sync with the same keys, it is
 * already in. Under "Remove animations" it simply shows, as the web's
 * reduced motion does (`reveal.js:15-18`).
 *
 * Items that together make one web section (a grid's cells, a run of tile
 * lines) share one [key]: those placed with the first rise with it, and the
 * ones scrolled to after are already in, as the rest of a revealed web
 * section is.
 */
internal fun Modifier.reveal(reveals: Reveals, key: String): Modifier {
    // ponytail: a section that a refresh adds while the page shows rises as
    // it is placed; the web's redraw shows it at once (`reveal.js:32-36`).
    return entrance(REVEAL_RISE_MS, fromY = REVEAL_FROM, fadeMs = REVEAL_FADE_MS, seen = reveals.of(key))
}

/** A full-width section of a lazy column that reveals as it comes into view, [key] naming it both as an item and as a section. */
internal fun LazyListScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.reveal(reveals, key)) { content() } }
}
```

- [ ] **Step 4: Reveal Home's sections**

In `ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt`, replace lines 81-138 (from `BoxWithConstraints(` to its closing `}`) with the block below. The lazy keys are unchanged, so scroll positions saved by the hoisted `listState` still restore. The file grows by 5 lines, to 144.

```kotlin
    // Every section but the cover rises into view the first time it is
    // placed on this visit (`home-view.js:47,85,98`, `home-features.js:32`).
    // The cover has a fade of its own. Continue with its quote, and Recently
    // Added with This month, are one lazy item each, so each pair rises as one.
    val reveals = rememberReveals()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = maxWidth
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (compact) 64.dp else 96.dp),
        ) {
            if (editorial.cover.isNotEmpty()) {
                item(key = "cover") {
                    HomeCover(
                        films = editorial.cover, watchlist = watchlist, width = width,
                        onPlay = { onPlay(it.setId) }, onOpenTitle = onOpenTitle, onToggleWatchlist = onToggleWatchlist,
                    )
                }
            }
            if (editorial.features.isNotEmpty()) {
                revealItem(reveals, "features") {
                    Box(Modifier.padding(top = 12.dp)) { HomeFeatures(features = editorial.features, width = width, onOpenTitle = onOpenTitle) }
                }
            }
            if (magazine.resumeCards.isNotEmpty() || editorial.quote != null) {
                revealItem(reveals, "continue") {
                    Box(Modifier.padding(top = 28.dp)) {
                        ContinueBand(
                            cards = magazine.resumeCards, quote = editorial.quote, width = width,
                            onPlay = onPlay, onOpenTitle = onOpenTitle, onSeeAllContinue = { onSeeAll(CatalogTab.Kept(KeptKind.CONTINUE)) },
                        )
                    }
                }
            }
            if (magazine.recentlyAdded.isNotEmpty() || editorial.thisMonth.isNotEmpty()) {
                revealItem(reveals, "recent") {
                    Box(Modifier.padding(top = 28.dp)) {
                        RecentBand(
                            recentlyAdded = magazine.recentlyAdded, totalFilms = magazine.recentlyAddedTotal, thisMonth = editorial.thisMonth,
                            width = width, onOpenTitle = onOpenTitle, onSeeAllMovies = { onSeeAll(CatalogTab.Dept(Department.MOVIES)) },
                        )
                    }
                }
            }
            if (series.isNotEmpty()) {
                revealItem(reveals, "series") {
                    Column(modifier = Modifier.padding(top = 36.dp, start = gutter, end = gutter)) {
                        BandHeading(title = "Latest series", count = latest.seriesTotal, onSeeAll = { onSeeAll(CatalogTab.Dept(Department.SERIES)) })
                        HomeShelfRow(shows = series, width = columnWidth - gutter * 2, compact = compact, onOpen = onOpenCollection)
                    }
                }
            }
            if (courses.isNotEmpty()) {
                revealItem(reveals, "courses") {
                    Column(modifier = Modifier.padding(top = 36.dp, start = gutter, end = gutter)) {
                        BandHeading(title = "Latest courses", count = latest.coursesTotal, onSeeAll = { onSeeAll(CatalogTab.Dept(Department.TUTORIALS)) })
                        CourseList(courses = courses, onOpen = onOpenCollection)
                    }
                }
            }
        }
    }
```

No new imports: `revealItem` and `rememberReveals` share the package, and `Box`, `Column` and `padding` are already imported.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. That is the 4 Home tests, plus `HomeScreenTest`, whose auto-advancing clock lets the reveals settle before it reads its bounds.

**If `aFlingDownAndBackLeavesTheSectionsAtRest` reads about 10dp:** Step 3 didn't land. The parked node kept its cancelled animation, so run Step 4 again.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): Home's sections rise into view once a visit, as the web's do"
```

## Task 3.3: Department and Collections rows rise into view

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/SectionReveal.kt` (the grid form of `revealItem`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/MoviesDepartmentScreen.kt` (lines 77-118)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/ShowsDepartmentScreen.kt` (lines 66-141)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/AnimeDepartmentScreen.kt` (lines 91-136)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/DocumentariesDepartmentScreen.kt` (lines 101-145)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/CollectionsScreen.kt` (lines 49-81)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/ListsScreen.kt` (`listsSection`)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/DepartmentRowRevealTest.kt`

**Interfaces:**
- **Consumes:** Task 3.2's `Reveals`, `rememberReveals`, `Modifier.reveal` and `LazyListScope.revealItem`.
- **Produces:**
  - `internal fun LazyGridScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit)`, which spans every column;
  - `listsSection(…, modifier: Modifier = Modifier)`, where the modifier goes on every item the section adds.

**The rule per page:** each `deptRow` heading and each row body is its own lazy item with its own reveal key. Each item rises as it arrives, as the web's section is still rising when its body scrolls in. A body made of many items (a grid's cells, tile lines) shares one key. The hero, and the Movies foot pill (the web's `.dept-all`, not a `.reveal`), stay still.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/DepartmentRowRevealTest.kt`:

```kotlin
package ui.catalog

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import catalog.CollectionKind
import catalog.Department
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import catalog.ShowsDepartment
import catalog.Underway
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A department's rows rise into place as they arrive (`department-hero.js:46`);
 * its hero stays still. A grid section rises once: the cells that arrive with
 * it rise, and the ones scrolled to after are already in. Read from
 * layer-mapped bounds on a paused clock; the fade is the device walk's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class DepartmentRowRevealTest : BrowsePageTest() {
    private fun showStill(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        show(content)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun heroTop() = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    @Test
    fun aMoviesRowRisesAsItArrivesButTheHeroDoesNot() {
        val films = listOf(film("a"), film("b"))
        val department = MoviesDepartment(
            filmCount = 2, hours = 3, lead = null, featured = films,
            genres = listOf(GenreIndexEntry("Drama", 2, "drama.jpg")), acclaimed = emptyList(), recentlyAdded = emptyList(),
        )
        showStill {
            MoviesDepartmentScreen(
                department = department, films = films, watch = WatchSnapshot.Empty, onOpenTitle = {}, onOpenGenre = {},
                onOpenGenresIndex = {}, onOpenLatest = {}, onSeeAllFilms = {}, onPlay = {}, titleInfo = { null },
            )
        }
        val hero = heroTop()
        val heading = boundsOf("Featured Movies").top.value
        val genres = boundsOf("Drama").top.value
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(hero, heroTop(), 0.5f)
        assertTrue(heading - boundsOf("Featured Movies").top.value > 20f, "the row's heading should rise in")
        assertTrue(genres - boundsOf("Drama").top.value > 20f, "the genre row should rise in")
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun aGridSectionRisesOnceAndItsLaterCellsAreAlreadyIn() {
        val shows = (1..20).map { collection("Show %02d".format(it), CollectionKind.SHOW) }
        val department = ShowsDepartment(
            showCount = 20, itemCount = 20, lead = null, underway = Underway(emptyList(), emptyList(), 0, 0),
            categories = emptyList(), popular = emptyList(), newEpisodes = emptyList(), all = shows,
        )
        val grid = LazyGridState()
        showStill {
            ShowsDepartmentScreen(
                dept = Department.SERIES, department = department, watch = WatchSnapshot.Empty, heldIds = emptySet(),
                columns = 2, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}, state = grid,
            )
        }
        val firstRising = boundsOf("Show 01").top.value
        compose.mainClock.advanceTimeBy(1_500)
        assertTrue(firstRising - boundsOf("Show 01").top.value > 20f, "the first cells should rise with their section")

        // Hero 0, "All shows" 1, cells from 2: index 16 is Show 15, lines below the first screen.
        compose.runOnUiThread { grid.requestScrollToItem(16) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val arrived = boundsOf("Show 15").top.value
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(arrived, boundsOf("Show 15").top.value, 0.5f)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.DepartmentRowRevealTest'`
Expected: FAIL.
- `aMoviesRowRisesAsItArrivesButTheHeroDoesNot` reads the heading at rest.
- `aGridSectionRisesOnceAndItsLaterCellsAreAlreadyIn` reads "Show 01" at rest.

- [ ] **Step 3: Add the grid form to `SectionReveal.kt`**

Add the imports `androidx.compose.foundation.lazy.grid.GridItemSpan` and `androidx.compose.foundation.lazy.grid.LazyGridScope`, and append:

```kotlin
/** [revealItem] for a lazy grid: one section across every column. */
internal fun LazyGridScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.reveal(reveals, key)) { content() } }
}
```

- [ ] **Step 4: Movies**

In `MoviesDepartmentScreen.kt`, add `val reveals = rememberReveals()` after `val allFilms = …` (line 75). Then replace the `LazyColumn(…) { … }` (lines 77-118) with the block below. Every row item is keyed now: they were all unkeyed. The file grows by 2 lines, to 181.

```kotlin
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.large)) {
        item {
            DepartmentHero(
                title = Department.MOVIES.label,
                line = moviesLineOf(department),
                lead = department.lead,
                onOpenTitle = onOpenTitle,
            )
        }
        if (department.featured.isNotEmpty()) {
            revealItem(reveals, "featured-heading") {
                DeptRowHeading(
                    title = "Featured Movies",
                    onSeeAll = onSeeAllFilms,
                    seeAllLabel = allFilms,
                    extra = { if (featured.isNotEmpty()) CatalogueTextButton(onClick = { reel = pickFeatured(films, watchedIds, Random) }) { Text("Featured") } },
                )
            }
            revealItem(reveals, "featured") { FilmRow(department.featured, watchedIds, onOpenTitle) }
        }
        if (department.genres.isNotEmpty()) {
            revealItem(reveals, "genres-heading") { DeptRowHeading(title = "Genres", onSeeAll = onOpenGenresIndex, seeAllLabel = "Every genre") }
            revealItem(reveals, "genres") { GenreRow(department.genres, onOpenGenre) }
        }
        if (department.acclaimed.isNotEmpty()) {
            revealItem(reveals, "acclaimed-heading") { DeptRowHeading(title = "Acclaimed, not yet seen") }
            revealItem(reveals, "acclaimed") { FilmRow(department.acclaimed, watchedIds, onOpenTitle) }
        }
        if (department.recentlyAdded.isNotEmpty()) {
            revealItem(reveals, "recent-heading") { DeptRowHeading(title = "Recently added", onSeeAll = onOpenLatest, seeAllLabel = "Latest") }
            revealItem(reveals, "recent") { FilmRow(department.recentlyAdded, watchedIds, onOpenTitle) }
        }
        // The way into the whole shelf at the page's foot, the web's `.dept-all`,
        // which is not a revealed section.
        item {
            PagePill(
                text = "$allFilms →",
                onClick = onSeeAllFilms,
                large = true,
                modifier = Modifier.padding(start = Spacing.medium, top = 56.dp),
            )
        }
    }
```

- [ ] **Step 5: Series and Tutorials**

In `ShowsDepartmentScreen.kt`, add `val reveals = rememberReveals()` after `val series = …` (line 66). Then replace the grid's content, from `if (resumeCards.isNotEmpty()) {` (line 91) through the closing `}` of the `if (series) … else …` (line 140), with:

```kotlin
        if (resumeCards.isNotEmpty()) {
            revealItem(reveals, "continue-heading") {
                DeptRowHeading(title = if (series) "Continue your series" else "Continue your courses")
            }
            revealItem(reveals, "continue") { ResumeStrip(cards = resumeCards, onOpenTitle = onPlay) }
        }
        for (row in department.categories) {
            revealItem(reveals, "category-heading-${row.title}") { DeptRowHeading(title = row.title) }
            revealItem(reveals, "category-${row.title}") { CollectionRow(row.units, onOpenCollection) }
        }
        if (department.popular.isNotEmpty()) {
            revealItem(reveals, "popular-heading") { DeptRowHeading(title = "Popular series") }
            revealItem(reveals, "popular") { CollectionRow(department.popular, onOpenCollection) }
        }
        if (department.newEpisodes.isNotEmpty()) {
            revealItem(reveals, "new-heading") { DeptRowHeading(title = "New episodes") }
            revealItem(reveals, "new") { CollectionRow(department.newEpisodes, onOpenCollection) }
        }
        revealItem(reveals, "all-heading") { DeptRowHeading(title = if (series) "All shows" else "All courses") }
        if (series) {
            // One web section, many cells: they share its key (see [reveal]).
            itemsIndexed(items = department.all, key = { _, entry -> "all/${entry.key}" }) { index, entry ->
                Box(Modifier.reveal(reveals, "all").gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        } else {
            // Courses as a list, the web's `{mode: LIST}` here: a course
            // carries no artwork to fill a plate with. One item rather than
            // one per course, so the grid's gap does not open between rows
            // the list draws edge to edge.
            revealItem(reveals, "all-courses") {
                Box(Modifier.padding(horizontal = Spacing.medium)) { CourseList(department.all, onOpenCollection) }
            }
        }
```

The hero item (lines 76-90) is unchanged. The lazy keys are the ones the file already had.

- [ ] **Step 6: Anime**

In `AnimeDepartmentScreen.kt`, add `val reveals = rememberReveals()` after the `resumeCards` `remember` (lines 89-91). Then replace from `if (resumeCards.isNotEmpty()) {` (line 109) through the closing `}` of `if (department.films.isNotEmpty())` (line 136) with:

```kotlin
        if (resumeCards.isNotEmpty()) {
            revealItem(reveals, "continue-heading") { DeptRowHeading(title = "Continue watching") }
            revealItem(reveals, "continue") { ResumeStrip(cards = resumeCards, onOpenTitle = onPlay) }
        }
        if (department.shows.isNotEmpty()) {
            revealItem(reveals, "series-heading") { DeptRowHeading(title = "Series") }
            itemsIndexed(items = department.shows, key = { _, entry -> "series/${entry.key}" }) { index, entry ->
                Box(Modifier.reveal(reveals, "series").gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        }
        if (department.films.isNotEmpty()) {
            revealItem(reveals, "films-heading") { DeptRowHeading(title = "Films") }
            itemsIndexed(items = department.films, key = { _, set -> "film/${set.setId}" }) { index, set ->
                Box(Modifier.reveal(reveals, "films").gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(Entry.Film(set), positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        }
```

- [ ] **Step 7: Documentaries**

In `DocumentariesDepartmentScreen.kt`, add `val reveals = rememberReveals()` after the `resumeCards` `remember` (line 99). Then replace from `if (resumeCards.isNotEmpty()) {` (line 118) through the closing `}` of `if (department.singles.isNotEmpty())` (line 144) with the block below. The previously unkeyed items gain keys.

```kotlin
        if (resumeCards.isNotEmpty()) {
            revealItem(reveals, "continue-heading") { DeptRowHeading(title = "Continue watching") }
            revealItem(reveals, "continue") { ResumeStrip(cards = resumeCards, onOpenTitle = onPlay) }
        }
        for (row in department.categories) {
            revealItem(reveals, "category-heading-${row.title}") { DeptRowHeading(title = row.title) }
            revealItem(reveals, "category-row-${row.title}") { DocumentaryUnitRow(row.units, watchedIds, onOpenCollection, onPlay) }
        }
        if (department.recentlyAdded.isNotEmpty()) {
            revealItem(reveals, "recent-heading") { DeptRowHeading(title = "Recently added") }
            revealItem(reveals, "recent") { DocumentaryRow(department.recentlyAdded, watchedIds, onPlay) }
        }
        for (group in department.collections) {
            val hasMore = group.collection.count > group.preview.size
            revealItem(reveals, "group-${group.collection.key}") {
                DeptRowHeading(
                    title = group.collection.name,
                    onSeeAll = { onOpenCollection(group.collection.key) }.takeIf { hasMore },
                    seeAllLabel = "All ${group.collection.count} →",
                )
            }
            revealItem(reveals, "group-row-${group.collection.key}") { DocumentaryRow(group.preview, watchedIds, onPlay) }
        }
        if (department.singles.isNotEmpty()) {
            revealItem(reveals, "singles-heading") { DeptRowHeading(title = "Standalone documentaries") }
            revealItem(reveals, "singles") { DocumentaryRow(department.singles, watchedIds, onPlay) }
        }
```

- [ ] **Step 8: Collections, whose two rows the web reveals too**

`collections-page.js:79,87,90` reveals these two rows on the web. In `ListsScreen.kt`, give `listsSection` a last parameter, `modifier: Modifier = Modifier`, and add one KDoc line before the signature:

```kotlin
 * [modifier] goes on every item this adds, so the section reveals as one (see [reveal]).
```

Then apply it to each item the section adds:
- the empty line: `modifier = modifier.fillMaxWidth().padding(Spacing.medium),` (was `Modifier.fillMaxWidth()…`, line 48);
- the tile lines: `tileLines("lists", lists, columns, ListOfSets::id, modifier.padding(horizontal = Spacing.medium)) { list, modifier -> …` (was `Modifier.padding(…)`, line 52). Rename the lambda's own parameter to `tile` (`{ list, tile -> … modifier = tile, … }`) so it doesn't shadow the new one;
- the button: `PagePill(text = "＋ New list", onClick = onNewList, modifier = modifier.padding(start = Spacing.medium, top = 20.dp))` (line 65).

In `CollectionsScreen.kt`:
- add `val reveals = rememberReveals()` after `val lead: MediaSet? = …` (line 52);
- inside the `LazyColumn`, replace lines 65-80 with:

```kotlin
            if (franchises.isNotEmpty()) {
                revealItem(reveals, "franchises-heading") { DeptRowHeading(title = "Franchises") }
                tileLines("franchises", franchises, columns, Franchise::id, Modifier.reveal(reveals, "franchises").padding(horizontal = Spacing.medium)) { franchise, modifier ->
                    ArtTile(
                        name = franchise.name,
                        meta = spelledCountOf(franchise.films.size, "film"),
                        art = franchise.art,
                        aspectRatio = DESTINATION_ASPECT,
                        onClick = { onOpenFranchise(franchise.id) },
                        modifier = modifier,
                        destination = true,
                    )
                }
            }
            revealItem(reveals, "lists-heading") { DeptRowHeading(title = "Your lists") }
            listsSection(
                lists = lists, setsById = setsById, columns = columns, onOpen = onOpenList, onNewList = { naming = true },
                modifier = Modifier.reveal(reveals, "lists"),
            )
```

`FranchiseScreen` is left as it is: the web never arms its row (insight 9).

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. That is both new tests and every existing department, Collections and LibraryFlow test, whose auto-advancing clocks let the reveals settle before they read bounds or click.

- [ ] **Step 10: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): department and Collections rows rise into view, as the web's do"
```

## Success criteria

- **Page turn:**
  - choosing a tab, opening search and opening any pushed frame starts the page 10dp low and ends at rest within 420ms;
  - typing a search, a sync and the player never move it;
  - a page whose catalog arrives late turns once.

  `PageTurnFlowTest`, 4 tests.
- **Sections:**
  - on Home, every section but the cover starts 28dp low when first placed and rests by 1100ms;
  - a section flung away mid-rise is at rest on return (Review Focus 1);
  - a sync with the same keys moves none (Review Focus 2).

  `HomeSectionRevealTest`, 4 tests.
- **Departments:**
  - Movies, Series, Tutorials, Anime and Documentaries rows, and the two Collections rows, rise on arrival; heroes stay still;
  - a grid section's later cells arrive at rest.

  `DepartmentRowRevealTest`, 2 tests.
- No `Modifier.offset`, `animateContentSize` or `animateItem()` added: `git diff main -- ui-mobile core | grep -E 'offset\(|animateContentSize|animateItem'` prints nothing new.
- Every new or touched file is under 200 lines, except the two that were over already. `LibraryBrowseBranches.kt` stays at 232. `CatalogScreen.kt` drops from 206 to about 100.
- `:core:designsystem` and `:ui-mobile` unit tests are green.
- Left to the phase 09 tablet walk: the fades, and Review Focus 1's "no dropped frames".
