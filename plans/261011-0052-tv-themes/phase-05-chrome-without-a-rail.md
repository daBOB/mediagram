# Phase 05 — Chrome without a rail

## Context
- Spec: § Chrome.
- `ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` (249 lines): content box, `TvDepartmentsBar` and `TvLibraryRail` in one `Box`; three `BackHandler`s (content → selected pill or rail row; bar → rail; a mount-gap absorber); one outer `focusProperties.onExit` (Left → rail, Up → pill or rail).
- Rail rows are asked for focus in three more places: `catalog/TvCatalogNav.kt:139` (Back from Latest/Genres/Stats/Settings/System), `catalog/TvCatalogBody.kt:118` (an empty kept wall's tab), and `TvLibraryChrome`'s `railArrivalTarget()`. In a rail-less style those requesters are attached to nothing, and asking one for focus fails.
- `system/TvMenuPage.kt:30-40` leaves out the rail's seven rows only because the rail carries them. It is shown by `TvLibrary.kt:147` while `menuOpen`; Settings and System open over it (`TvMenuBranches.kt:39-45`) and Back returns to it; My List, Continue, Latest, Genres and Stats belong to the catalogue (`TvCatalogScreen.kt:138-148`, `onRailSelect`).
- `chrome/TvDepartmentsBar.kt` (183): the pill row (requesters, scroll, edge fades, ring room) is what all three bars share.

## Task 5.1: One rail target, a rail-less chrome, and the rail's rows in the menu

**Files:**
- Create: `ui-tv/src/main/kotlin/ui/tv/chrome/TvChromeFocus.kt` (moved out of `TvLibraryChrome.kt:44-85`, plus the new parts)
- Modify: `ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt`, `catalog/TvCatalogNav.kt:137-142`, `catalog/TvCatalogBody.kt:36,118`, `catalog/TvCatalogScreen.kt`, `system/TvMenuPage.kt`, `TvLibrary.kt`, `TvLibraryBranches.kt:59-120`
- Test: `ui-tv/src/test/kotlin/ui/tv/chrome/TvLibraryChromeRaillessTest.kt` (create), `ui-tv/src/test/kotlin/ui/tv/system/TvMenuPageRailRowsTest.kt` (create), `ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt`

**Interfaces:**
- Consumes: `LocalTvStyle` (1.1), `TvStyle.hasRail` (1.2).
- Produces: `TvChromeFocus.settingsFocus: FocusRequester`; `TvChromeFocus.railTarget(item: RailItem): FocusRequester`; `TvCatalogBody(railTarget: (RailItem) -> FocusRequester, …)` replacing `railRowFocus`; `TvMenuPage(…, railItems: List<RailItem> = emptyList(), onRailItem: (RailItem) -> Unit = {}, onBack)`; `TvCatalogScreen(…, pendingRail: RailItem? = null, onPendingRailHandled: () -> Unit = {})`; `TvLibraryHomeFrame(…, pendingRail: RailItem?, onPendingRailHandled: () -> Unit, onOpenMenu)`.

- [ ] **Step 1: Write the failing chrome test**

```kotlin
package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/**
 * Apple TV and Google TV styles draw no rail: the page starts at the
 * screen's own edge, Left at its start goes nowhere, Back and Up from the
 * page reach the selected pill — or, on a kept wall with none selected, the
 * avatar whose menu holds the rail's rows — and Back from the bar leaves the
 * app, the way Back at the top of any TV app does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryChromeRaillessTest : TvScreenStateTest() {
    @Test
    fun thePageStartsAtTheScreensEdgeWithNoRail() {
        for (style in listOf(TvStyle.APPLE, TvStyle.GOOGLE)) {
            show(style) { chrome() }
            assertEquals(0.dp, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left, "$style")
            close()
        }
        show(TvStyle.MEDIAGRAM) { chrome() }
        assertEquals(TvContentStart, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left)
    }

    @Test
    fun leftAtThePagesStartStaysOnThePage() {
        show(TvStyle.APPLE) { chrome() }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        content.performKeyInput { pressKey(Key.DirectionLeft) }
        content.assertIsFocused()
    }

    @Test
    fun backFromThePageReachesTheSelectedPillThenLeavesTheApp() {
        show(TvStyle.GOOGLE) { chrome() }
        compose.onNodeWithTag(CONTENT).performSemanticsAction(SemanticsActions.RequestFocus)
        back()
        compose.onNodeWithText("Home").assertIsFocused()
        assertFalse(pageTakesBack(), "the bar has nothing behind it: Back closes the app")
    }

    @Test
    fun upFromThePageReachesTheSelectedPill() {
        show(TvStyle.APPLE) { chrome(selectedPill = 1) }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        content.performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Movies").assertIsFocused()
    }

    @Test
    fun backFromAKeptWallReachesTheAvatar() {
        show(TvStyle.APPLE) { chrome(selectedPill = -1) }
        compose.onNodeWithTag(CONTENT).performSemanticsAction(SemanticsActions.RequestFocus)
        back()
        compose.onNodeWithContentDescription(AVATAR).assertIsFocused()
    }

    private fun chrome(selectedPill: Int = 0) =
        TvLibraryChrome(
            pills = DEPARTMENTS,
            selectedPill = selectedPill,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = {},
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = {},
            focus = rememberTvChromeFocus(),
        ) {
            Box(Modifier.size(200.dp).testTag(CONTENT).focusable())
        }

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        const val AVATAR = "Menu, watching as Ada"
        const val CONTENT = "content"
    }
}
```

(`chrome()` is called from the `show { }` lambda, which is `@Composable`; mark `chrome` `@Composable` — `import androidx.compose.runtime.Composable`.)

- [ ] **Step 2: Write the failing menu and catalogue tests**

Create `TvMenuPageRailRowsTest.kt`:

```kotlin
package ui.tv.system

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.MenuScreen
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.MenuActions
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest

/** With no rail on screen the menu lists the rail's seven rows; with the rail it lists none of them, as before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvMenuPageRailRowsTest : TvScreenStateTest() {
    private var chosen: RailItem? = null

    private fun menu(
        railItems: List<RailItem>,
        restoreKey: String? = null,
    ) = show {
        TvMenuPage(
            menu = MenuActions(onSystem = {}, onSettings = {}, onUpdate = {}, onTmdbKey = {}, onStartOver = {}),
            restoreKey = restoreKey,
            watchingAs = "Ada",
            onSwitchProfile = {},
            railItems = railItems,
            onRailItem = { chosen = it },
            onBack = {},
        )
    }

    @Test
    fun withTheRailOnScreenTheMenuListsNoneOfItsRows() {
        menu(railItems = emptyList())
        compose.onNodeWithText("Latest").assertDoesNotExist()
    }

    @Test
    fun withNoRailEachOfItsRowsIsChosenFromTheMenu() {
        menu(railItems = RailItem.entries)
        for (item in RailItem.entries) {
            compose.onNodeWithText(item.label).performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(item, chosen)
        }
    }

    @Test
    fun backFromSettingsLandsOnItsOwnRow() {
        menu(railItems = RailItem.entries, restoreKey = menuRestoreKey(MenuScreen.Settings))
        compose.onNodeWithText("Settings").assertIsFocused()
    }
}
```

In `TvCatalogScreenStateTest`: give the private `show` three more parameters — `style: TvStyle = TvStyle.Default`, `pendingRail: RailItem? = null`, `onPendingRailHandled: () -> Unit = {}`, `onOpenLatest: () -> Unit = {}` — pass `style` to `TvTheme(style = style)` and the other three to `TvCatalogScreen`. Add (imports `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `designsystem.TvStyle`, `ui.common.RailItem`, `androidx.compose.ui.test.assertIsFocused`):

```kotlin
    /** A rail row chosen from the menu waits for the library, then runs once. */
    @Test
    fun aRailRowChosenFromTheMenuRunsOnceTheLibraryIsReady() {
        var latest = 0
        // Read in composition below, so it is state: the screen sees the hand-off and drops the request.
        var handled by mutableIntStateOf(0)
        var state by mutableStateOf<CatalogUiState>(CatalogUiState.Loading)
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme(style = TvStyle.APPLE) {
                    TvCatalogScreen(
                        state = state,
                        profile = TvChosenProfile(name = "Ada", onChoose = {}),
                        onOpenMenu = {},
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onOpenList = {},
                        onCreateList = {},
                        onOpenLatest = { latest++ },
                        pendingRail = RailItem.LATEST.takeIf { handled == 0 },
                        onPendingRailHandled = { handled++ },
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0, latest, "nothing to open Latest over yet")

        state = ready(films(2))
        compose.waitForIdle()

        assertEquals(1, latest)
        assertEquals(1, handled)
    }

    /** An empty My List in a style with no rail hands the remote to the avatar, the rail's stand-in. */
    @Test
    fun anEmptyMyListWithNoRailRestsOnTheAvatar() {
        show(ready(films(2)), style = TvStyle.APPLE, pendingRail = RailItem.MY_LIST)
        compose.onNodeWithContentDescription("Menu, watching as Ada").assertIsFocused()
    }
```

(`controller` is the class's existing `private var controller`; also import `androidx.compose.runtime.mutableIntStateOf`.)

- [ ] **Step 3: Run them to see them fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.chrome.TvLibraryChromeRaillessTest' --tests 'ui.tv.system.TvMenuPageRailRowsTest' --tests 'ui.tv.catalog.TvCatalogScreenStateTest'`
Expected: compilation fails — `railItems`, `onRailItem`, `pendingRail`, `onPendingRailHandled` are not parameters yet.

- [ ] **Step 4: `TvChromeFocus.kt`**

Move `TvPagePadding`, `LocalTvPagePadding`, `asPaddingValues`, `TvChromeFocus` and `rememberTvChromeFocus` (with their KDocs) out of `TvLibraryChrome.kt` into the new file, then change the last two to:

```kotlin
internal class TvChromeFocus(
    val selectedPillFocus: FocusRequester,
    val searchFocus: FocusRequester,
    val avatarFocus: FocusRequester,
    val settingsFocus: FocusRequester,
    val railRowFocus: Map<RailItem, FocusRequester>,
    val contentFocus: FocusRequester,
    private val style: TvStyle,
) {
    /**
     * Where the remote goes for [item]: its own row on the rail, in the one
     * style that draws a rail; otherwise whatever stands for the rail now —
     * Google TV style's gear for Settings, the avatar (whose menu lists the
     * rail's rows) for the rest. A rail row's requester has nothing attached
     * to it in a rail-less style, so it must never be asked directly.
     */
    fun railTarget(item: RailItem): FocusRequester =
        when {
            style.hasRail -> railRowFocus.getValue(item)
            style == TvStyle.GOOGLE && item == RailItem.SETTINGS -> settingsFocus
            else -> avatarFocus
        }
}

@Composable
internal fun rememberTvChromeFocus(): TvChromeFocus =
    TvChromeFocus(
        selectedPillFocus = remember { FocusRequester() },
        searchFocus = remember { FocusRequester() },
        avatarFocus = remember { FocusRequester() },
        settingsFocus = remember { FocusRequester() },
        railRowFocus = remember { RailItem.entries.associateWith { FocusRequester() } },
        contentFocus = remember { FocusRequester() },
        style = LocalTvStyle.current,
    )
```

(imports `designsystem.LocalTvStyle`, `designsystem.TvStyle`, `ui.tv.hasRail`, plus what the moved code used.)

- [ ] **Step 5: The rail-less chrome**

In `TvLibraryChrome.kt`:

```kotlin
    val hasRail = LocalTvStyle.current.hasRail

    fun railArrivalTarget(): FocusRequester = focus.railTarget(railActive ?: RailItem.MY_LIST)
```

- the bar's `BackHandler` becomes `BackHandler(enabled = barHasFocus && hasRail) { railArrivalTarget().requestFocus() }` — with no rail, Back from the bar falls through and closes the app, as the rail's Back does today;
- `padding` becomes
  ```kotlin
      val padding =
          remember(hasRail) {
              val top = TvDepartmentsBarHeight + Overscan.vertical
              // With no rail to stand off, the page keeps the screen's own overscan on both sides.
              if (hasRail) {
                  TvPagePadding(start = TvContentGutter, top = top, end = TvContentGutter, bottom = Overscan.vertical)
              } else {
                  TvPagePadding(start = Overscan.horizontal, top = top, end = Overscan.horizontal, bottom = Overscan.vertical)
              }
          }
  ```
- the inner `Box`'s `.padding(start = TvContentStart)` becomes `.padding(start = if (hasRail) TvContentStart else 0.dp)`;
- `onExit`'s Left branch becomes `FocusDirection.Left -> if (hasRail) railArrivalTarget().requestFocus() else cancelFocusChange()`;
- `TvLibraryRail(…)` is drawn only `if (hasRail)`;
- the class KDoc gains a paragraph: "Apple TV and Google TV styles draw no rail ([TvStyle.hasRail]): the page starts at the screen's overscan, Left at its start goes nowhere, and [TvChromeFocus.railTarget] stands in for every rail row — the avatar, whose menu lists them, or Google TV's gear for Settings. Back from the bar then closes the app, as Back from the rail does here."
(imports `androidx.compose.ui.unit.dp`, `designsystem.LocalTvStyle`, `ui.tv.hasRail`.)

`TvCatalogNav.kt:139`: `chromeFocus.railTarget(railTarget).requestFocus()`.

`TvCatalogBody.kt`: the parameter `railRowFocus: Map<RailItem, FocusRequester>` becomes `railTarget: (RailItem) -> FocusRequester`, and line 118 `tabFocus = railTarget(railActive ?: RailItem.MY_LIST)`; its comment's "falls back to its own rail row" becomes "falls back to its own rail row (or whatever stands for it: [ui.tv.chrome.TvChromeFocus.railTarget])". In `TvCatalogScreen.kt:235`: `railTarget = nav.chromeFocus::railTarget,`. Any test that builds `TvCatalogBody` directly (`git grep -n "railRowFocus" -- ui-tv/src/test`) passes `railTarget = { requester }` the same way.

- [ ] **Step 6: The rail's rows in the menu**

In `TvMenuPage.kt`, the signature gains `railItems: List<RailItem> = emptyList()` and `onRailItem: (RailItem) -> Unit = {}` before `onBack`. Inside:

```kotlin
    val railRows = remember { RailItem.entries.associateWith { FocusRequester() } }
    // Back from Settings or System — which open over this page — lands on its own row.
    val railLanding =
        when (restoreKey) {
            menuRestoreKey(MenuScreen.Settings) -> RailItem.SETTINGS
            menuRestoreKey(MenuScreen.System) -> RailItem.SYSTEM
            else -> null
        }?.takeIf { it in railItems }
    LaunchedEffect(Unit) { (railLanding?.let(railRows::getValue) ?: rows.getValue(landing)).requestFocus() }
```

replacing the existing `LaunchedEffect(Unit) { rows.getValue(landing).requestFocus() }`; and after the Switch profile `Column`:

```kotlin
        for (item in railItems) {
            TvTextRow(text = item.label, onClick = { onRailItem(item) }, focusRequester = railRows.getValue(item))
        }
```

KDoc: after "…so this page never offers them either." add "A style that draws no rail ([railItems] non-empty) lists those seven here instead, under the profile switch."

In `TvLibrary.kt`:

```kotlin
    // A rail row chosen from the menu in a style with no rail, for the
    // catalogue to run the way the rail would have once it is composed again.
    var pendingRail by remember { mutableStateOf<RailItem?>(null) }
    val hasRail = LocalTvStyle.current.hasRail
```

and the `TvMenuPage(…)` call gains

```kotlin
                railItems = if (hasRail) emptyList() else RailItem.entries,
                onRailItem = { item ->
                    when (item) {
                        // Opened over this page, so Back comes back to it, as from the rail.
                        RailItem.SETTINGS -> menu.onSettings()
                        RailItem.SYSTEM -> menu.onSystem()
                        else -> {
                            menuOpen = false
                            pendingRail = item
                        }
                    }
                },
```

and the `null ->` branch passes `pendingRail = pendingRail, onPendingRailHandled = { pendingRail = null }` to `TvLibraryHomeFrame` (before its trailing `onOpenMenu` lambda).

In `TvLibraryBranches.kt`, `TvLibraryHomeFrame` gains `pendingRail: RailItem?` and `onPendingRailHandled: () -> Unit` before `onOpenMenu`, passed through to `TvCatalogScreen`.

In `TvCatalogScreen.kt`, the signature gains `pendingRail: RailItem? = null` and `onPendingRailHandled: () -> Unit = {}`, and after `onRailSelect` is defined:

```kotlin
    // A rail row chosen from the avatar's menu in a style with no rail: run
    // it as the rail would have, once there is a library to run it over.
    LaunchedEffect(pendingRail, ready != null) {
        val item = pendingRail ?: return@LaunchedEffect
        if (ready == null) return@LaunchedEffect
        onRailSelect(item)
        onPendingRailHandled()
    }
```

- [ ] **Step 7: Run the ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS — the new tests, and every existing chrome, rail, nav and catalogue test under mediagram (`TvLibraryChromeUpTest`, `TvLibraryRailSizesTest`, `TvDepartmentsBarTest`, `TvHomeReturnTest`).

- [ ] **Step 8: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): a chrome with no rail, its rows behind the avatar"
```

## Task 5.2: Apple TV style's centred tab bar

**Files:**
- Create: `ui-tv/src/main/kotlin/ui/tv/chrome/TvPillRow.kt` (the shared pill row, moved out of `TvDepartmentsBar.kt:94-135,156-183`)
- Create: `ui-tv/src/main/kotlin/ui/tv/chrome/TvCentredTabBar.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt`, `TvLibraryChrome.kt:218-234`
- Test: `ui-tv/src/test/kotlin/ui/tv/chrome/TvCentredTabBarTest.kt` (create)

**Interfaces:**
- Consumes: `TvChromeFocus` (5.1), `TvFocus.pillColors`/`pillBorder` (3.1).
- Produces: `TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf: (Int) -> FocusRequester, modifier)`; `rememberPillRequesters(pills, selected, selectedPillFocus): (Int) -> FocusRequester`; `barBackground(blend: Float): Color` and `barInk(blend: Float): Color` (`@Composable @ReadOnlyComposable`); `internal val PillRingRoom`; `TvCentredTabBar(pills, selected, onSelect, onSearch, profile, onMenu, downTarget, selectedPillFocus, searchFocus, avatarFocus, modifier, blend)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/** Apple TV style's bar: search, the pills and the avatar in one pill, centred; on a narrow screen the pills scroll inside it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvCentredTabBarTest : TvScreenStateTest() {
    @Test
    fun searchThePillsAndTheAvatarSitCentredInOneRow() {
        show(TvStyle.APPLE) { chrome() }
        val search = compose.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        val avatar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        val home = compose.onNodeWithText("Home").getUnclippedBoundsInRoot()
        val width = compose.onRoot().getUnclippedBoundsInRoot().right
        assertTrue(search.right <= home.left && home.right <= avatar.left, "search, then the pills, then the avatar")
        assertTrue(abs(((search.left + avatar.right) / 2 - width / 2).value) < 2f, "centred: $search … $avatar in $width")
    }

    @Test
    fun theEndsOfTheRowReachThePillsAndDownReachesThePage() {
        show(TvStyle.APPLE) { chrome() }
        compose.onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Home").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithContentDescription("Search").assertIsFocused()
        compose.onNodeWithContentDescription("Search").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Home").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w640dp-h540dp")
    fun onANarrowScreenEveryPillAndBothEndsStayReachable() {
        show(TvStyle.APPLE) { chrome() }
        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Collections").assertIsFocused()
        compose.onNodeWithText("Collections").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription(AVATAR).assertIsFocused()
        val fades = compose.onNode(SemanticsMatcher.keyIsDefined(EdgeFadesKey)).fetchSemanticsNode().config[EdgeFadesKey]
        assertEquals(EdgeFades(start = true, end = false), fades)
        val bar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        assertTrue(bar.right <= 640.dp, "the avatar stays on screen: $bar")
    }

    @Composable
    private fun chrome() =
        TvLibraryChrome(
            pills = DEPARTMENTS,
            selectedPill = 0,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = {},
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = {},
            focus = rememberTvChromeFocus(),
        ) {}

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        const val AVATAR = "Menu, watching as Ada"
    }
}
```

(`EdgeFadesKey` and `EdgeFades` are the semantics key and value `TvDepartmentsBarTest.fades()` reads the same way.)

- [ ] **Step 2: Run it to see it fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.chrome.TvCentredTabBarTest'`
Expected: FAIL — under Apple the chrome still draws `TvDepartmentsBar`, so search sits after the pills.

- [ ] **Step 3: Share the pill row**

Create `TvPillRow.kt` and move into it from `TvDepartmentsBar.kt`, unchanged: `PillRingRoom` (now `internal`), `RoomOnBothSidesBringIntoViewSpec`, `rememberRoomOnBothSides`, `TvOverCoverInk`, `TvBarOverCoverBg`. Add:

```kotlin
/** The bar's ground at [blend]: translucent over a hero (`0f`), the page's own ground once opaque (`1f`). Every bar style shares it. */
@Composable
@ReadOnlyComposable
internal fun barBackground(blend: Float): Color = lerp(TvBarOverCoverBg, MaterialTheme.colorScheme.background, blend)

/** The bar's ink at [blend], over a hero or over the page. */
@Composable
@ReadOnlyComposable
internal fun barInk(blend: Float): Color = lerp(TvOverCoverInk, MaterialTheme.colorScheme.onBackground, blend)

/** Each pill's own requester, held by the bar so its end controls can name the last pill; the selected one's is [selectedPillFocus]. */
@Composable
internal fun rememberPillRequesters(
    pills: List<String>,
    selected: Int,
    selectedPillFocus: FocusRequester,
): (Int) -> FocusRequester {
    val held = remember { HashMap<String, FocusRequester>() }
    return { index -> if (index == selected) selectedPillFocus else held.getOrPut(pills[index]) { FocusRequester() } }
}

/**
 * The bar's pills, in every style: one requester each from [requesterOf],
 * Down to [downTarget] from each (focus properties set on a container are
 * not inherited by its children), and — should they run past [modifier]'s
 * width — a scroll that fades only an end that clips something, with
 * [PillRingRoom] kept at both ends for a focused pill's grown ring.
 */
@Composable
internal fun TvPillRow(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    ink: Color,
    downTarget: FocusRequester,
    requesterOf: (Int) -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    val downModifier = Modifier.focusProperties { down = downTarget }
    val scroll = rememberScrollState()
    val fades by remember(scroll) { derivedStateOf { EdgeFades(start = scroll.canScrollBackward, end = scroll.canScrollForward) } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRoomOnBothSides(EdgeFadeWidth + PillRingRoom)) {
        Row(
            modifier = modifier.fadeClippedEdges(fades).horizontalScroll(scroll).padding(PillRingRoom),
            // `.departments` gap 4 (`shell.css:89`).
            horizontalArrangement = Arrangement.spacedBy(4.tvDp),
        ) {
            pills.forEachIndexed { index, pill ->
                key(pill) {
                    // One requester on every pill, every recomposition: [ui.tv.rememberStableRequester]'s KDoc says why.
                    TvPill(title = pill, active = index == selected, ink = ink, onClick = { onSelect(index) }, modifier = downModifier.focusRequester(requesterOf(index)))
                }
            }
        }
    }
}
```

In `TvDepartmentsBar.kt`, the body becomes:

```kotlin
    val ink = barInk(blend)
    val downModifier = Modifier.focusProperties { down = downTarget }
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(color = barBackground(blend))
                .padding(top = Overscan.vertical, start = TvContentGutter - PillRingRoom, end = TvContentGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filling, so search and the avatar stand at the bar's far end, as the
        // web's `.library-toolbar { margin-left: auto }` does (`shell.css:91`).
        TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.tvDp), verticalAlignment = Alignment.CenterVertically) {
            …the existing search and avatar block, unchanged…
        }
    }
```

keeping the existing comments on the search button. Run `./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.chrome.*'`: the existing bar tests must pass before the next step.

- [ ] **Step 4: `TvCentredTabBar.kt`**

```kotlin
package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.tvDp
import ui.tv.TvFocus
import ui.tv.profile.TvChosenProfile

/**
 * Apple TV style's bar: search, the pills and the avatar in one dark
 * translucent pill centred over the page, where mediagram has the pills at
 * the start and the rail beside them. No blur behind it — blur measured too
 * slow on the box. The band it sits in fades from translucent over a hero to
 * the page's ground ([barBackground]), as every bar style's does, so a page
 * scrolled under it never shows through.
 */
@Composable
internal fun TvCentredTabBar(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    downTarget: FocusRequester,
    selectedPillFocus: FocusRequester,
    searchFocus: FocusRequester,
    avatarFocus: FocusRequester,
    modifier: Modifier = Modifier,
    blend: Float = 1f,
) {
    val ink = barInk(blend)
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(barBackground(blend))
                .padding(top = Overscan.vertical, start = Overscan.horizontal, end = Overscan.horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.background(TabBarFill, TvFocus.PillShape).padding(horizontal = 6.tvDp, vertical = 4.tvDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvRoundIconButton(
                icon = painterResource(R.drawable.core_designsystem_ic_search),
                description = "Search",
                ink = ink,
                onClick = onSearch,
                modifier =
                    Modifier
                        .focusProperties {
                            down = downTarget
                            if (pills.isNotEmpty()) right = requesterOf(0)
                        }.focusRequester(searchFocus),
            )
            TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f, fill = false))
            TvAvatar(
                profile = profile,
                onClick = onMenu,
                modifier =
                    Modifier
                        .focusProperties {
                            down = downTarget
                            // By name, not by where it is drawn: a pill scrolled out of view is no candidate for a geometric search.
                            if (pills.isNotEmpty()) left = requesterOf(pills.lastIndex)
                        }.focusRequester(avatarFocus),
            )
        }
    }
}

/** The tab bar's own pill: Apple's dark translucent material, without the blur. */
private val TabBarFill = Color(0xB31C1C1E)
```

In `TvLibraryChrome.kt`, the `TvDepartmentsBar(…)` call becomes a `when (LocalTvStyle.current)` whose `TvStyle.APPLE` branch calls `TvCentredTabBar` with the same arguments and the same `modifier` (the `onFocusChanged` that sets `barHasFocus`/`lastInBar`), and whose `else` branch is today's `TvDepartmentsBar` call (Task 5.3 adds Google's).

- [ ] **Step 5: Run the ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): Apple TV style's centred tab bar"
```

## Task 5.3: Google TV style's top bar

**Files:**
- Create: `ui-tv/src/main/kotlin/ui/tv/chrome/TvTopBar.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` (the `when` from 5.2)
- Test: `ui-tv/src/test/kotlin/ui/tv/chrome/TvTopBarTest.kt` (create)

**Interfaces:**
- Consumes: `TvPillRow`, `rememberPillRequesters`, `barBackground`, `barInk` (5.2); `TvChromeFocus.settingsFocus` (5.1).
- Produces: `TvTopBar(pills, selected, onSelect, onSearch, profile, onMenu, onSettings: () -> Unit, downTarget, selectedPillFocus, searchFocus, avatarFocus, settingsFocus, modifier, blend)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/** Google TV style's bar: the avatar and search first, the pills, then a gear that opens Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTopBarTest : TvScreenStateTest() {
    private var chosen: RailItem? = null

    @Test
    fun theAvatarAndSearchLeadThePillsAndTheGearEndsTheBar() {
        show(TvStyle.GOOGLE) { chrome() }
        val avatar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        val search = compose.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        val home = compose.onNodeWithText("Home").getUnclippedBoundsInRoot()
        val gear = compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot()
        assertTrue(avatar.right <= search.left && search.right <= home.left && home.right <= gear.left)
    }

    @Test
    fun theGearOpensSettingsTheWayTheRailDid() {
        show(TvStyle.GOOGLE) { chrome() }
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(RailItem.SETTINGS, chosen)
    }

    @Composable
    private fun chrome() =
        TvLibraryChrome(
            pills = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections"),
            selectedPill = 0,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = { chosen = it },
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = {},
            focus = rememberTvChromeFocus(),
        ) {}

    private companion object {
        const val AVATAR = "Menu, watching as Ada"
    }
}
```

In `TvCatalogScreenStateTest`, add (Review Focus 1: Back from Settings with no rail lands on the gear, not a rail row):

```kotlin
    @Test
    fun backFromSettingsWithNoRailLandsOnTheGear() {
        show(ready(films(2)), style = TvStyle.GOOGLE, restoreKey = menuRestoreKey(MenuScreen.Settings))
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
    }

    @Test
    fun backFromLatestWithNoRailLandsOnTheAvatar() {
        show(ready(films(2)), style = TvStyle.APPLE, restoreKey = TvLatestRailKey)
        compose.onNodeWithContentDescription("Menu, watching as Ada").assertIsFocused()
    }
```

(imports `catalog.MenuScreen`, `ui.tv.system.menuRestoreKey`.)

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.chrome.TvTopBarTest' --tests 'ui.tv.catalog.TvCatalogScreenStateTest'`
Expected: FAIL — no "Settings" gear exists under Google yet.

- [ ] **Step 3: `TvTopBar.kt`**

```kotlin
package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.tvDp
import ui.common.RailItem
import ui.tv.profile.TvChosenProfile

/**
 * Google TV style's bar: the viewer's avatar and search at the start, the
 * pills, and a gear at the end that opens Settings — that home screen's own
 * order. The rest of the rail's rows are in the avatar's menu.
 */
@Composable
internal fun TvTopBar(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    downTarget: FocusRequester,
    selectedPillFocus: FocusRequester,
    searchFocus: FocusRequester,
    avatarFocus: FocusRequester,
    settingsFocus: FocusRequester,
    modifier: Modifier = Modifier,
    blend: Float = 1f,
) {
    val ink = barInk(blend)
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(barBackground(blend))
                .padding(top = Overscan.vertical, start = Overscan.horizontal, end = Overscan.horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.tvDp),
    ) {
        TvAvatar(profile = profile, onClick = onMenu, modifier = Modifier.focusProperties { down = downTarget }.focusRequester(avatarFocus))
        TvRoundIconButton(
            icon = painterResource(R.drawable.core_designsystem_ic_search),
            description = "Search",
            ink = ink,
            onClick = onSearch,
            modifier =
                Modifier
                    .focusProperties {
                        down = downTarget
                        if (pills.isNotEmpty()) right = requesterOf(0)
                    }.focusRequester(searchFocus),
        )
        TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f))
        TvRoundIconButton(
            icon = painterResource(RailItem.SETTINGS.icon),
            description = RailItem.SETTINGS.label,
            ink = ink,
            onClick = onSettings,
            modifier =
                Modifier
                    .focusProperties {
                        down = downTarget
                        if (pills.isNotEmpty()) left = requesterOf(pills.lastIndex)
                    }.focusRequester(settingsFocus),
        )
    }
}
```

In `TvLibraryChrome.kt`'s `when`, add `TvStyle.GOOGLE -> TvTopBar(…same arguments…, onSettings = { onRailSelect(RailItem.SETTINGS) }, settingsFocus = focus.settingsFocus, …)`, leaving `TvStyle.MEDIAGRAM -> TvDepartmentsBar(…)` as the last branch (make the `when` exhaustive over the three styles, no `else`). If `TvLibraryChrome.kt` now passes 200 lines, move the bar `when` into `@Composable internal fun TvChromeBar(…)` in a new file `chrome/TvChromeBar.kt`, taking the same arguments the three bars take plus `style: TvStyle`.

- [ ] **Step 4: Run the ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): Google TV style's top bar with the gear for Settings"
```

## Success criteria
- Apple/Google: no rail; page at the overscan; Left at the start goes nowhere; Back/Up reach the selected pill, or the avatar on a kept wall; Back from the bar leaves the app.
- No rail-row requester is ever asked for focus outside mediagram (every request goes through `railTarget`).
- The menu lists the rail's seven rows only when there is no rail; each one runs exactly as the rail ran it, after the library is ready.
- mediagram's chrome tests all pass unchanged.
