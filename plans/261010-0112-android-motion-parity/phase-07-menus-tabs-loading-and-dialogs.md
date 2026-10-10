# Phase 07 — Menus, tabs, loading and dialogs

**Spec:** `../spec.md` §2 (Everywhere: ⋯ menus, Tabs, Loading, Dialogs). **Status:** pending. **Depends on:** 02. **Runs after:** 04, 05 and 06 — it edits lines in `FeaturedReel.kt` and `HomeCover.kt` (04), `TitleTabs.kt` (05) and `TitlePills.kt` (06) that those phases leave alone; if they moved, find them by the text quoted below.

## Context

- **Web, the reference:**
  - ⋯ menu: `animation: menu-in 320ms var(--ease) both` (`web/public/styles/title-page.css:46`), `@keyframes menu-in { from { opacity: 0; transform: translateY(-4px); } }` (`:50`). An entrance only: the list is removed at once when it closes.
  - Player menus: plain `hidden` toggles (`player-card.css:89-119`, `player-menus.js:31,43`).
  - Tabs: `.tab` has `box-shadow: inset 0 -2px 0 transparent` and `transition: color var(--hover), box-shadow var(--hover)`; `.tab.on` gets `inset 0 -2px 0 var(--ink)` (`title-page.css:121-132`). Each tab's own underline fades; nothing slides.
  - Loading: `<div class="loading-state" role="status"><p>Loading your library…</p></div>` (`index.html:98-99`): a block the cover's height on `#0a0a0b`, words in `--on-image-2` near its foot, 96px foot room, 104px and a 16px edge on a phone (`shell.css:173-177,184`). The channel list says "Reading the account's channels…" (`settings-telegram-forms.js:15`). Nothing on the web spins.
  - Dialogs: `showModal()` (`featured-reel.js:51`, `pin-prompt.js:124`, `settings-telegram-forms.js:102`), no transition on the dialog or its `::backdrop` (`playback.css:19`, `library-controls.css:63`).
- **Android today** (all paths under `ui-mobile/src/main/kotlin/`):
  - 5 `DropdownMenu` calls: `ui/catalog/TitlePills.kt:80`, `ui/chrome/OverflowMenu.kt:60,107`, `ui/catalog/SeasonWall.kt:87`, `ui/player/PlayerMarks.kt:83`.
  - 15 `DropdownMenuItem` calls: `TitlePills.kt:82`; `OverflowMenu.kt:61,68,75,76,77,78,79,120,131,138,162`; `SeasonWall.kt:89`; `PlayerMarks.kt:85`; `ui/catalog/TitlePreload.kt:64`.
  - Verified in material3 1.4.0 bytecode (`AndroidMenu_androidKt`, `MenuKt`, `MenuDefaults`):
    - `DropdownMenu` = a `Popup` with `PopupProperties(focusable = true)`, `internal DropdownMenuPositionProvider`, then a `Surface(MenuDefaults.shape, MenuDefaults.containerColor, TonalElevation, ShadowElevation)` around a `Column(padding(vertical = 8.dp).width(IntrinsicSize.Max).verticalScroll)`, with a scale-and-fade transition both ways.
    - `DropdownMenuItem` = `clickable(interactionSource, ripple(true), role = null)`, min 112dp / max 280dp wide, 48dp tall. Its signature takes an `interactionSource`, so wrapping it keeps what TalkBack hears exactly.
    - `MenuVerticalMargin` = 48dp.
  - Tabs: `ScrollableTabRow` + `Tab` (`ui/catalog/TitleTabs.kt:51-65`). The default indicator is `SecondaryIndicator(Modifier.tabIndicatorOffset(...))`, which animates offset and width (the slide), 3dp, `primary`. The row's default content colour is also `primary`, so today's labels and indicator are both the accent.
  - Spinners: `ui/MobileApp.kt:175` (the **setup check**, `SetupUiState.Checking` at `:81-83`, not the library load), `ui/player/PlayerScreenParts.kt:34` (`PlayerUiState.Preparing`, `PlayerScreen.kt:176`), `ui/profile/ProfilePickerScreen.kt:72` (`ProfileUiState.Loading`), `ui/setup/LibraryScreen.kt:54` (`LibraryPrompt.Waiting`).
  - The library load proper is `ui/catalog/CatalogScreen.kt:86`, already still text: `CenteredMessage("Loading your library…")`. `CenteredMessage` (`ui/catalog/CenteredMessage.kt:23`) is the app's still-text style: `bodyLarge`, `onSurfaceVariant`. Strings are hard-coded; ui-mobile has no `strings.xml`.
  - Dialogs: 8 `AlertDialog`s and 1 `Dialog` (sites in Task 7.4). Every `AlertDialog` here has a `title` slot; `RemoveProfileDialog` has no `text`, so the helper goes in `title`.
- After phase 02: the M3 buttons at these sites are `CatalogueTextButton`/`CatalogueIconButton` etc. `LocalRippleConfiguration` is null, so `DropdownMenuItem` and `Tab` are silent until this phase.

## Rulings made here

1. **Wrap, don't rebuild, the items.** `CatalogueMenuItem` is Material's `DropdownMenuItem` with the catalogue indication on its own interaction source, the same pattern as phase 02's buttons. Size, colours and semantics (no role, click action, inside a `popup()` node) stay what they were by construction. Only the list container is new.
2. **The underline is the label's colour.** The web's underline is the tab's text colour (`--ink` for both). Here the labels are the accent today, so the underline takes `LocalContentColor`, which keeps today's look. It is 2dp, the web's, not Material's 3dp. The label colour gap (accent here, ink-3/ink on the web) is out of scope; see Unresolved.
3. **The cover-shaped block goes wherever the library is being opened, not only `MobileApp`.** The spec says "the library load in `MobileApp.kt`", but that spinner is the setup check. A cold start runs three waits back to back: the setup check (`MobileApp`), then the profile gate's read (`ProfilePickerScreen`), then the library itself (`CatalogScreen`). Giving the block to only one of them would flash block → centred text → block. So `LibraryLoading()` replaces all three, and the cold start is one still block, as the web's first paint is. The channel picker and the player get still text in the app's style. **The ProfilePicker part deviates from the spec's "plain still text"; confirm with the user.** It is isolated in Task 7.3.
4. **The player's wait says "Loading…".** The web shows nothing over its stage while a title opens, so there is no wording to copy. The text is set in `OnImage2`, because `onSurfaceVariant` is dark grey in the light theme and would vanish on the black stage.
5. **The dialog helper runs before the enter animation, on current Android.** Verified in the framework bytecode Robolectric ships (`android-all-instrumented` 15):
   - `ViewRootImpl.performTraversals` calls `dispatchAttachedToWindow` (offset 415). That attach is when Compose runs a `Dialog`'s first composition: `DialogLayout.setContent` → `createComposition`, which waits for `AndroidComposeView.onAttachedToWindow`, then runs synchronously. Our `SideEffect` therefore runs inside the attach.
   - Only then does `performTraversals` read `mWindowAttributesChanged` (offset 1237). `setWindowAnimations(0)` → `Dialog.onWindowAttributesChanged` → `updateViewLayout` sets that flag.
   - The first `relayoutWindow(params)` follows (offset 1563). So `windowAnimations = 0` reaches the window manager before the first draw, which is when it picks the enter animation.
   - In the 6.0.1 jar the flag is read **before** the attach (offset 73 against 372). There the first relayout carries the theme's animation: the open still fades, and only the close is still.
   - The tablet walk checks the open; see the walk section for the fallback.

## Task 7.1: One shared menu replaces Material's

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/chrome/CatalogueMenu.kt`
- Modify: `ui/catalog/TitlePills.kt` (imports `:7-8`, `:80-91`), `ui/chrome/OverflowMenu.kt` (imports `:6-7`, `:60-81`, `:107`, `:120`, `:131`, `:138`, `:162`), `ui/catalog/SeasonWall.kt` (imports `:10-11`, `:87`, `:89`), `ui/player/PlayerMarks.kt` (imports `:5-6`, `:83`, `:85`), `ui/catalog/TitlePreload.kt` (import `:7`, `:64`)
- Test: `ui-mobile/src/test/kotlin/ui/chrome/CatalogueMenuTest.kt`

**Interfaces (produced; phase 08 wraps the surface in `IndicationInk`):**
```kotlin
@Composable internal fun CatalogueMenu(expanded: Boolean, onDismissRequest: () -> Unit, still: Boolean = false,
                                       content: @Composable ColumnScope.() -> Unit)
@Composable internal fun CatalogueMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean = true)
```
**Consumes:** `designsystem.entrance` (phase 01), `LocalIndication` = `catalogueIndication()` (phase 02).

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/chrome/CatalogueMenuTest.kt`:

```kotlin
package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The menu's drop is a layer translation, which shows in its item's bounds
 * inside the popup's root on a paused clock. Its fade is alpha, which nothing
 * here can capture (see PageGroundColorTest); the device walk checks it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueMenuTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var expanded by mutableStateOf(false)
    private var picked = 0

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(still: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box {
                        Text("⋯")
                        CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }, still = still) {
                            CatalogueMenuItem(
                                text = { Text("Pin") },
                                onClick = {
                                    expanded = false
                                    picked++
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Opens the menu and answers where its item stands on the entrance's first frame. */
    private fun open(): Float {
        compose.runOnUiThread { expanded = true }
        compose.mainClock.advanceTimeByFrame() // recomposes: the popup comes in
        compose.waitForIdle() // its window attaches, composes and lays out
        compose.mainClock.advanceTimeByFrame() // the entrance's first frame
        return top()
    }

    private fun top() = compose.onNodeWithText("Pin").getUnclippedBoundsInRoot().top.value

    @Test
    fun inTheCatalogueTheMenuDropsIntoPlaceAndClosesAtOnce() {
        show(still = false)
        val opening = open()
        compose.mainClock.advanceTimeBy(400)
        val rest = top()
        assertTrue(rest - opening > 1f, "starts above its rest: $opening, then $rest")

        compose.onNodeWithText("Pin").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(1, picked)
        compose.onNodeWithText("Pin").assertDoesNotExist()
    }

    @Test
    fun inThePlayerTheMenuOpensWhereItRests() {
        show(still = true)
        val opening = open()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(top(), opening, 0.1f)
    }

    @Test
    fun theListIsStillAPopupAndItsItemsStillTakeAClick() {
        show(still = false)
        open()
        compose.onNode(isPopup()).assertExists()
        compose.onNodeWithText("Pin").assert(hasClickAction())
    }

    /** Material's menu scales and fades both ways; one slipping back in would move where the web does not. */
    @Test
    fun noScreenUsesMaterialsAnimatedMenu() {
        val menu = Regex("""^import androidx\.compose\.material3\.DropdownMenu$""", RegexOption.MULTILINE)
        val item = Regex("""^import androidx\.compose\.material3\.DropdownMenuItem$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    val text = file.readText()
                    menu.containsMatchIn(text) || (file.name != "CatalogueMenu.kt" && item.containsMatchIn(text))
                }.map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "use ui.chrome's CatalogueMenu and CatalogueMenuItem instead in: $offenders")
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.chrome.CatalogueMenuTest'`
Expected: compilation fails, `CatalogueMenu` and `CatalogueMenuItem` unresolved.

- [ ] **Step 3: Write `CatalogueMenu.kt`**

```kotlin
package ui.chrome

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import designsystem.entrance

/** The web's menu-in: 320ms on `--ease`, from 4px up (`title-page.css:46,50`). */
private const val MENU_IN_MS = 320
private val MENU_IN_FROM = (-4).dp

/** Material's own list padding and window margin, kept so each menu sits and measures where it did. */
private val LIST_PADDING = 8.dp
private val WINDOW_MARGIN = 48.dp

/**
 * A ⋯ menu's list, in place of Material's `DropdownMenu`, which scales and
 * fades both ways. The web's catalogue menu fades in and drops 4px over
 * 320ms, then closes at once (`title-page.css:46,50`: an animation on the way
 * in, none on the way out). A [still] menu opens and closes with no motion at
 * all: the web's player menus are shown and hidden (`player-card.css:89-119`).
 *
 * Everything else is what `DropdownMenu` gave, so only the motion changes:
 * placed against the layout it is called in ([UnderAnchor]); Material's menu
 * surface, shape, shadow, padding and intrinsic width; a focusable popup that
 * an outside tap or Back dismisses; and [CatalogueMenuItem] rows, which are
 * Material's own items, so a screen reader hears what it heard before.
 */
@Composable
internal fun CatalogueMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    still: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val margin = with(LocalDensity.current) { WINDOW_MARGIN.roundToPx() }
    Popup(
        popupPositionProvider = remember(margin) { UnderAnchor(margin) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = if (still) Modifier else Modifier.entrance(MENU_IN_MS, fromY = MENU_IN_FROM),
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            Column(
                modifier = Modifier.padding(vertical = LIST_PADDING).width(IntrinsicSize.Max).verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/**
 * One row of a [CatalogueMenu]: Material's own item, so its size, colours and
 * what a screen reader hears are unchanged. It draws the theme's indication on
 * its own interaction source, because Material's items hard-code a ripple the
 * theme has turned off.
 */
@Composable
internal fun CatalogueMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = Modifier.indication(source, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
    )
}

/**
 * Where `DropdownMenu` puts its list:
 * - across: start edges lined up with the anchor, else end edges, else
 *   against the nearer side of the window;
 * - down: below the anchor, else above it, else centred on its top, else
 *   against the nearer end of the window, always [margin] clear of the
 *   window's top and bottom.
 *
 * The anchor is the layout the menu is called in, as before.
 */
private data class UnderAnchor(val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val ltr = layoutDirection == LayoutDirection.Ltr
        val xs =
            listOf(
                if (ltr) anchorBounds.left else anchorBounds.right - width,
                if (ltr) anchorBounds.right - width else anchorBounds.left,
                if (anchorBounds.center.x < windowSize.width / 2) 0 else windowSize.width - width,
            )
        val ys =
            listOf(
                anchorBounds.bottom,
                anchorBounds.top - height,
                anchorBounds.top - height / 2,
                if (anchorBounds.center.y < windowSize.height / 2) margin else windowSize.height - height - margin,
            )
        val x = xs.firstOrNull { it >= 0 && it + width <= windowSize.width } ?: xs.last()
        val y = ys.firstOrNull { it >= margin && it + height <= windowSize.height - margin } ?: ys.last()
        return IntOffset(x, y)
    }
}
```

- [ ] **Step 4: Swap every site**

Every change below is a callee rename, except the player's `still = true`. Lines are as read before phase 02; phase 02 changed none of them.

| Site | Before | After |
|---|---|---|
| `ui/catalog/TitlePills.kt:80` | `DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {` | `CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {` |
| `ui/catalog/TitlePills.kt:82` | `DropdownMenuItem(` | `CatalogueMenuItem(` |
| `ui/chrome/OverflowMenu.kt:60` | `DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {` | `CatalogueMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {` |
| `ui/chrome/OverflowMenu.kt:61,68,131,138,162` | `DropdownMenuItem(` | `CatalogueMenuItem(` |
| `ui/chrome/OverflowMenu.kt:75-79` | `DropdownMenuItem(text = { Text("My List") }, …)` and the four like it (Continue, Latest, Genres, Stats) | `CatalogueMenuItem(text = { Text("My List") }, …)`, the rest of each line unchanged |
| `ui/chrome/OverflowMenu.kt:107` | `DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {` | `CatalogueMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {` |
| `ui/chrome/OverflowMenu.kt:120` | `DropdownMenuItem(text = { Text("Preloads · ${menu.preloadCount}") }, onClick = { close(); onPreloads() })` | `CatalogueMenuItem(text = { Text("Preloads · ${menu.preloadCount}") }, onClick = { close(); onPreloads() })` |
| `ui/catalog/SeasonWall.kt:87` | `DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {` | `CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {` |
| `ui/catalog/SeasonWall.kt:89` | `DropdownMenuItem(` | `CatalogueMenuItem(` |
| `ui/catalog/TitlePreload.kt:64` | `DropdownMenuItem(text = { Text("Remove preload") }, onClick = { onDismiss(); onRemove() })` | `CatalogueMenuItem(text = { Text("Remove preload") }, onClick = { onDismiss(); onRemove() })` |
| `ui/player/PlayerMarks.kt:85` | `DropdownMenuItem(` | `CatalogueMenuItem(` |

`OverflowMenu.kt:162` is `MenuItem`'s item, which passes `text`, `enabled` and `onClick` by name. `CatalogueMenuItem` takes all three.

The player's menu, `ui/player/PlayerMarks.kt:83`. Before:
```kotlin
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
```
After:
```kotlin
        // The web's player menus are shown and hidden, never animated (player-card.css:89-119).
        CatalogueMenu(expanded = open, onDismissRequest = { open = false }, still = true) {
```

Apply the renames from `android/`, then make the `PlayerMarks.kt:83` edit by hand:

```bash
cd ui-mobile/src/main/kotlin
for f in ui/catalog/TitlePills.kt ui/chrome/OverflowMenu.kt ui/catalog/SeasonWall.kt ui/player/PlayerMarks.kt ui/catalog/TitlePreload.kt; do
  perl -pi -e 's/(?<![A-Za-z.])DropdownMenuItem\(/CatalogueMenuItem(/g; s/(?<![A-Za-z.])DropdownMenu\(/CatalogueMenu(/g' "$f"
  perl -ni -e 'print unless /^import androidx\.compose\.material3\.DropdownMenu(Item)?$/' "$f"
done
cd -
```

Imports, by hand:
- `TitlePills.kt`, `SeasonWall.kt` and `PlayerMarks.kt` gain `import ui.chrome.CatalogueMenu` and `import ui.chrome.CatalogueMenuItem`.
- `TitlePreload.kt` gains only `import ui.chrome.CatalogueMenuItem`.
- `OverflowMenu.kt` is in `ui.chrome` and needs neither.

Check: `git grep -n -E 'DropdownMenu(Item)?\(' -- 'ui-mobile/src/main/*.kt'` prints only the `DropdownMenuItem(` inside `CatalogueMenu.kt`.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest`
Expected: PASS. That includes the four new tests and the existing menu drivers, which open menus and click items by text: `PlayerKidsChoiceTest`, `OverflowUtilitiesTest`, `TitlePreloadTest`, `PreloadsMenuFlowTest` and `LibraryFlowTest`.

- **If `opening` already equals `rest`:** the popup ran its first frames inside `waitForIdle`. Read `top()` straight after the `waitForIdle()` in `open()`, before the frame.
- **If it still reads rest, or `getUnclippedBoundsInRoot` ignores the layer:** report BLOCKED. Don't weaken the assertion or move the drop to `Modifier.offset`.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): menus drop in as the web's do and close at once"
```

## Task 7.2: Tab underlines fade in place

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/TitleTabs.kt` (imports `:3-16`, `TitleTabRow` `:39-66`). The tab **content** rise at `:74-85` belongs to phase 05; leave it alone.
- Test: `ui-mobile/src/test/kotlin/ui/catalog/title/TitleTabsTest.kt` (one new test)

- [ ] **Step 1: Write the failing test**

Add to `TitleTabsTest`, with the imports `java.io.File` and `kotlin.test.assertTrue`:

```kotlin
    /**
     * Material's tab rows slide an indicator from tab to tab unless handed
     * `indicator = {}`; the web's underline fades in place under each tab
     * (`title-page.css:121-132`). A tab row added without it would slide again.
     */
    @Test fun noTabRowSlidesAnIndicator() {
        val rows = Regex("""(?<![A-Za-z.])(Primary|Secondary)?(Scrollable)?TabRow\(""")
        val still = Regex("""indicator = \{\}""")
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> file.readText().let { rows.findAll(it).count() > still.findAll(it).count() } }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "pass indicator = {} and draw each tab's own underline in: $offenders")
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.TitleTabsTest'`
Expected: FAIL, naming `ui/catalog/TitleTabs.kt`.

- [ ] **Step 3: Replace `TitleTabRow` (`:39-66`) with the row and its tab**

```kotlin
@Composable
internal fun TitleTabRow(
    labels: List<String>,
    shown: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No `containerColor`, so `ScrollableTabRow` painted its M3 default
    // (`surface`) — invisible while the page beneath it was `surface` too,
    // a full-width band once the page moved to `background` (`ui.pageGround`).
    // `Transparent` lets the page's own ground show through instead, the
    // way `.tab-list` draws no fill of its own (`title-page.css:120`).
    ScrollableTabRow(
        selectedTabIndex = labels.indexOf(shown),
        modifier = modifier,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        // Nothing slides between tabs on the web: each tab carries its own
        // underline (`title-page.css:121-132`), drawn by [TitleTab].
        indicator = {},
    ) {
        labels.forEach { label ->
            key(label) { TitleTab(label, selected = label == shown, onClick = { onSelect(label) }) }
        }
    }
}

/**
 * One tab, with its own underline:
 * - the label's colour while chosen and clear otherwise, crossing over
 *   `--hover` as the web's inset shadow does (`title-page.css:128-132`);
 * - read when drawn, so the fade redraws the tab and recomposes nothing;
 * - keyed by label in the row, so Cast arriving ahead of the chosen tab moves
 *   that tab's underline with it, instead of fading one out and another in.
 *
 * Material's tab hard-codes a ripple the theme has turned off, so this one
 * draws the theme's indication on its own interaction source.
 */
@Composable
private fun TitleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val underline by animateColorAsState(if (selected) LocalContentColor.current else Color.Transparent, Motion.hover(), label = "tab underline")
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(label) },
        interactionSource = source,
        modifier =
            Modifier
                .semantics { contentDescription = label }
                .indication(source, LocalIndication.current)
                .drawBehind {
                    val thickness = UNDERLINE.toPx()
                    drawRect(underline, topLeft = Offset(0f, size.height - thickness), size = Size(size.width, thickness))
                },
    )
}

/** The web's `inset 0 -2px 0` (`title-page.css:132`). */
private val UNDERLINE = 2.dp
```

Add these imports:
- `androidx.compose.animation.animateColorAsState`
- `androidx.compose.foundation.LocalIndication`, `androidx.compose.foundation.indication`
- `androidx.compose.foundation.interaction.MutableInteractionSource`
- `androidx.compose.material3.LocalContentColor`
- `androidx.compose.runtime.key`, `androidx.compose.runtime.remember`
- `androidx.compose.ui.draw.drawBehind`
- `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.geometry.Size`
- `designsystem.Motion`

`getValue`, `Color`, `semantics`, `contentDescription` and `dp` are already imported.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest --tests 'ui.catalog.title.*'`
Expected: PASS, including the three existing `TitleTabsTest` choices, which still select by label. Then run the whole `:ui-mobile:testDebugUnitTest`.

- [ ] **Step 5: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): tab underlines fade in place instead of sliding"
```

## Task 7.3: Loading is still words

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/catalog/home/LibraryLoading.kt`
- Modify: `ui/catalog/home/HomeType.kt` (after `:26`), `ui/catalog/home/HomeCover.kt` (`:126-127`), `ui/MobileApp.kt` (`:10`, `:81-83`, `:172-177`), `ui/profile/ProfilePickerScreen.kt` (`:17`, `:72`), `ui/catalog/CatalogScreen.kt` (`:86`), `ui/setup/LibraryScreen.kt` (`:5`, `:12`, `:18`, `:50-55`), `ui/player/PlayerScreenParts.kt` (`:12`, `:16`, `:31-36`), `ui/player/PlayerScreen.kt` (`:115`, `:176`)
- Test: `ui-mobile/src/test/kotlin/ui/StillLoadingTest.kt`

**Interfaces (produced):**
```kotlin
internal fun coverHeightFor(compact: Boolean, windowHeight: Dp): Dp   // ui.catalog.home, shared with HomeCover
@Composable internal fun LibraryLoading()                             // ui.catalog.home
@Composable internal fun PreparingNote()                              // ui.player, replaces CenteredSpinner
```

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/StillLoadingTest.kt`:

```kotlin
package ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.ProfileUiState
import java.io.File
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.home.LibraryLoading
import ui.player.PreparingNote
import ui.profile.PickerActions
import ui.profile.ProfilePickerScreen
import ui.setup.LibraryScreen

/** Every wait says what it waits for and nothing on screen turns, as on the web (`shell.css:173-177`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StillLoadingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    /** A spinner reports itself as an indeterminate progress bar; still words never do. */
    private fun assertNothingSpins() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)

    @Test fun theLibraryLoadsUnderACoverShapedBlockInTheWebsWords() {
        show { LibraryLoading() }
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun theProfileGateWaitsUnderTheSameBlock() {
        show { ProfilePickerScreen(ProfileUiState.Loading, PickerActions({}, {}, {}, {}, {}, {})) }
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun choosingALibraryWaitsInTheWebsWords() {
        show { LibraryScreen(choices = null, error = null, onChoose = {}, onLookAgain = {}) }
        compose.onNodeWithText("Reading the account's channels…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun aTitleOpensUnderStillWords() {
        show { PreparingNote() }
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun noScreenDrawsASpinner() {
        val spinner = Regex("""^import androidx\.compose\.material3\.CircularProgressIndicator$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && spinner.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "the web's loading is still words; spinners remain in: $offenders")
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.StillLoadingTest'`
Expected: compilation fails, `LibraryLoading` and `PreparingNote` unresolved.

- [ ] **Step 3: Share the cover's height**

In `ui/catalog/home/HomeType.kt`, after `CompactBreakpoint` (`:26`), add:

```kotlin
/**
 * The cover story's height: 84% of the window on a phone, the web's
 * `--cover-height` clamp beside (`theme.css:116`, `home.css:9`). The block the
 * library loads under is the same height, so the cover arrives where it stood.
 */
internal fun coverHeightFor(
    compact: Boolean,
    windowHeight: Dp,
): Dp = if (compact) (windowHeight.value * 0.84f).dp else fluid(620f, 0.635f, 705f, windowHeight.value).dp
```

In `ui/catalog/home/HomeCover.kt`, keep the comment above `coverMinHeight` and replace `:126-127`. Before:
```kotlin
    val coverMinHeight =
        if (compact) (windowHeight.value * 0.84f).dp else fluid(620f, 0.635f, 705f, windowHeight.value).dp
```
After:
```kotlin
    val coverMinHeight = coverHeightFor(compact, windowHeight)
```

- [ ] **Step 4: Write `LibraryLoading.kt`**

```kotlin
package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** The block's ground, the web's `#0a0a0b` (`shell.css:176`): the cover's black before there is a cover. */
private val LoadingGround = Color(0xFF0A0A0B)

/** The web's room under the words and at the edges on a phone (`shell.css:184`); beside, 96px under the gutter (`:175`). */
private val PHONE_EDGE = 16.dp
private val PHONE_FOOT = 104.dp
private val WIDE_FOOT = 96.dp

/**
 * The library loading, shaped like the cover story it is about to become:
 * the web's `loading-state` (`index.html:98-99`, `shell.css:173-177`). A dark
 * block the cover's height, its words near the foot, nothing moving. The
 * web's reason holds here too: the catalogue arrives into the page it is
 * loading instead of replacing another.
 *
 * Drawn for every wait that opens the library: the setup check, the profile
 * gate's read, and the library itself, so a cold start is one still block,
 * as the web's first paint is.
 */
@Composable
internal fun LibraryLoading() {
    val window = LocalConfiguration.current
    val width = window.screenWidthDp.dp
    val compact = width <= CompactBreakpoint
    val edge = if (compact) PHONE_EDGE else gutterFor(width)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(coverHeightFor(compact, window.screenHeightDp.dp))
                .background(LoadingGround)
                .padding(start = edge, end = edge, bottom = if (compact) PHONE_FOOT else WIDE_FOOT),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text = "Loading your library…", style = MaterialTheme.typography.bodyLarge, color = OnImage2)
    }
}
```

- [ ] **Step 5: Replace the four spinners**

`ui/MobileApp.kt:81-83`. Before:
```kotlin
        SetupUiState.Checking -> {
            LoadingIndicator()
        }
```
After:
```kotlin
        // The app's first paint, as the web's is (index.html:98-99): every launch
        // but the first goes on to open the library, under the same block.
        SetupUiState.Checking -> {
            LibraryLoading()
        }
```
Also in `MobileApp.kt`:
- delete `LoadingIndicator` (`:172-177`) and the import `androidx.compose.material3.CircularProgressIndicator` (`:10`);
- add `import ui.catalog.home.LibraryLoading`.

`Box` and `Alignment` stay; `CentredText` uses both.

`ui/profile/ProfilePickerScreen.kt:72`. Before:
```kotlin
        ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
```
After:
```kotlin
        // Between the setup check and the library on every cold start: the same block, so the wait never flashes.
        ProfileUiState.Loading -> LibraryLoading()
```
Delete the import at `:17` and add `import ui.catalog.home.LibraryLoading`. `Box` and `Alignment` stay; `Initial` uses them.

`ui/catalog/CatalogScreen.kt:86`. Before:
```kotlin
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
```
After:
```kotlin
        CatalogUiState.Loading -> LibraryLoading()
```
Add `import ui.catalog.home.LibraryLoading`.

`ui/setup/LibraryScreen.kt:50-55`. Before:
```kotlin
        LibraryPrompt.Waiting -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
```
After:
```kotlin
        // The web's words for the same wait (settings-telegram-forms.js:15), still.
        LibraryPrompt.Waiting -> CenteredMessage("Reading the account's channels…")
```
In `LibraryScreen.kt`:
- delete the imports `androidx.compose.foundation.layout.Box` (`:5`), `androidx.compose.material3.CircularProgressIndicator` (`:12`) and `androidx.compose.ui.Alignment` (`:18`); nothing else in the file uses them;
- add `import ui.catalog.CenteredMessage`.

`ui/player/PlayerScreenParts.kt:31-36`. Before:
```kotlin
@Composable
internal fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}
```
After:
```kotlin
/**
 * What the stage says while the title opens: still words where a spinner was,
 * as every wait on the web is still (`shell.css:173-177`). The library's own
 * loading colour, which reads on the black stage in either theme.
 */
@Composable
internal fun PreparingNote() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Loading…", style = MaterialTheme.typography.bodyLarge, color = OnImage2)
    }
}
```
In `PlayerScreenParts.kt`:
- delete the imports `androidx.compose.material3.CircularProgressIndicator` (`:12`) and `androidx.compose.ui.graphics.Color` (`:16`, used only by the spinner);
- add `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.Text` and `ui.catalog.home.OnImage2`.

`ui/player/PlayerScreen.kt`:
- `:176`: `PlayerUiState.Preparing -> CenteredSpinner()` becomes `PlayerUiState.Preparing -> PreparingNote()`.
- `:115`: `// error, the preparing spinner) Back must not close something unseen.` becomes `// error, the title still opening) Back must not close something unseen.`

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest`
Expected: PASS, including the five new tests and `LibraryFlowTest`'s three "Loading your library…" checks. Those are frames that keep `CenteredMessage`.

- [ ] **Step 7: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): loading is still words, the library's under a cover-shaped block"
```

## Task 7.4: Dialogs open and close without the window animation

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/chrome/NoDialogAnimation.kt`
- Modify (each also gains `import ui.chrome.NoDialogAnimation`):
  - `ui/catalog/FetchResultDialog.kt:18`
  - `ui/catalog/ListNameDialog.kt:30`
  - `ui/catalog/ListScreen.kt:129`
  - `ui/player/AddToListDialog.kt:50`
  - `ui/profile/PinDialog.kt:60`
  - `ui/profile/RemoveProfileDialog.kt:23`
  - `ui/settings/SignOutConfirmation.kt:22`
  - `ui/setup/StartOverAction.kt:42`
  - `ui/catalog/FeaturedReel.kt:73-74`
- Test: `ui-mobile/src/test/kotlin/ui/chrome/NoDialogAnimationTest.kt`

**Interfaces (produced):** `@Composable internal fun NoDialogAnimation()`, called first inside a dialog's content.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/chrome/NoDialogAnimationTest.kt`:

```kotlin
package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import ui.catalog.FetchResultDialog

/**
 * A dialog window's open and close animations come from its attributes, which
 * Robolectric's framework keeps as the device would. Whether the open is
 * actually still is the device walk's to see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoDialogAnimationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun latestWindowAnimations() = ShadowDialog.getLatestDialog().window!!.attributes.windowAnimations

    @Test fun anAlertDialogsWindowHasNoAnimation() {
        show { FetchResultDialog(message = "Nothing new.", onDismiss = {}) }
        compose.onNodeWithText("Nothing new.").assertExists()
        assertEquals(0, latestWindowAnimations())
    }

    @Test fun aFullScreenDialogsWindowHasNone() {
        show {
            Dialog(onDismissRequest = {}) {
                NoDialogAnimation()
                Text("reel")
            }
        }
        compose.onNodeWithText("reel").assertExists()
        assertEquals(0, latestWindowAnimations())
    }

    /** Each `AlertDialog(` or `Dialog(` call is matched by a `NoDialogAnimation()` in the same file. */
    @Test fun everyDialogCallsIt() {
        val dialogs = Regex("""(?<![A-Za-z.])(AlertDialog|Dialog)\(""")
        val calls = Regex("""NoDialogAnimation\(\)""")
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> file.readText().let { dialogs.findAll(it).count() > calls.findAll(it).count() } }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "call NoDialogAnimation() first inside each dialog in: $offenders")
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.chrome.NoDialogAnimationTest'`
Expected: compilation fails, `NoDialogAnimation` unresolved.

- [ ] **Step 3: Write `NoDialogAnimation.kt`**

```kotlin
package ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Called first inside a dialog's content, so its window opens and closes with
 * no platform animation, as the web's dialogs do: `showModal()` with no
 * transition on the dialog or its backdrop (`featured-reel.js:51`,
 * `pin-prompt.js:124`, `playback.css:19`).
 *
 * A dialog's first composition runs while its window attaches. Current
 * Android reads changed window attributes after that attach and sends them
 * with the window's first layout, before the window is shown, so the open is
 * as still as the close. Android 6 sent that first layout before attaching,
 * so there the open keeps the platform's fade.
 */
@Composable
internal fun NoDialogAnimation() {
    val view = LocalView.current
    SideEffect { (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(0) }
}
```

- [ ] **Step 4: Call it at the nine dialogs**

Each `AlertDialog`'s title slot opens with the call. Lines are as read; phase 02 changed none of them.

| Site | Before | After |
|---|---|---|
| `ui/catalog/FetchResultDialog.kt:18` | `title = { Text("Update library") },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text("Update library")`<br>`},` |
| `ui/catalog/ListNameDialog.kt:30` | `title = { Text(title) },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text(title)`<br>`},` |
| `ui/catalog/ListScreen.kt:129` | `title = { Text("Delete \"$name\"?") },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text("Delete \"$name\"?")`<br>`},` |
| `ui/player/AddToListDialog.kt:50` | `title = { Text("Add to list") },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text("Add to list")`<br>`},` |
| `ui/profile/PinDialog.kt:60` | `title = { Text(prompt.heading) },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text(prompt.heading)`<br>`},` |
| `ui/profile/RemoveProfileDialog.kt:23` | `title = { Text(removeQuestion(profile)) },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text(removeQuestion(profile))`<br>`},` |
| `ui/settings/SignOutConfirmation.kt:22` | `title = { Text("Sign out?") },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text("Sign out?")`<br>`},` |
| `ui/setup/StartOverAction.kt:42` | `title = { Text("Start over?") },` | `title = {`<br>`    NoDialogAnimation()`<br>`    Text("Start over?")`<br>`},` |

`ui/catalog/FeaturedReel.kt:73-74`, the reel's own `Dialog`. Before:
```kotlin
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var index by rememberSaveable { mutableIntStateOf(0) }
```
After:
```kotlin
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        NoDialogAnimation()
        var index by rememberSaveable { mutableIntStateOf(0) }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest`
Expected: PASS, including the three new tests, `PinDialogTest` and `ProfileGateFlowTest`.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): dialogs open and close without the window animation"
```

## Device walk (phase 09)

Tablet only, `ANDROID_SERIAL=caad49da`, avatar T. Never tap "Start over", so the channel picker's wait is covered by its test alone.

- **Menus:**
  - On a title, the ⋯ menu fades in and drops about 4dp in a third of a second. It vanishes at once on a pick, an outside tap and Back. Its top edge is not clipped while it drops.
  - The same holds for the ⋮ overflow and a show's season picker.
  - In the player, Kids opens and closes with no motion.
  - The menus sit where they did, at the same width and colours.
  - TalkBack reads the items as it did.
- **Tabs:** on a film, tap Cast, then Details. No bar slides; the old underline fades out and the new one fades in.
- **Loading:**
  - From a cold start: one dark block with "Loading your library…" near its foot, until Home. Note any jump when the library chrome arrives.
  - Opening a title shows "Loading…" on the black stage, not a spinner.
- **Dialogs:**
  - Open and close Update library's result, a PIN prompt, Add to list and the Featured reel.
  - None scales or fades; record the tablet's Android version.
  - **If the open still animates:** record it in `docs/system-architecture.md` § Parity with the web player as "dialogs on Android N open with the platform's fade; they close still". It is the platform's attribute timing, verified above.
  - **If the dim behind a dialog still fades in:** the window manager animates dim layers on its own, so record that the same way.

## Success criteria

- All 13 new tests pass and `:ui-mobile:testDebugUnitTest` stays green.
- `git grep -n -E '(^|[^A-Za-z.])DropdownMenu\(|CircularProgressIndicator' -- 'ui-mobile/src/main/*.kt'` prints nothing.
- `DropdownMenuItem(` appears only in `CatalogueMenu.kt`.
- Every dialog file calls `NoDialogAnimation()`.
- Every file touched stays under 200 lines.
- Nothing new moves except the catalogue menu's 320ms drop and the tab underline's 160ms fade.

## Unresolved questions

1. **The profile gate's wait:** the spec says plain still text; this phase gives it the cover-shaped block (Ruling 3). Keep, or revert to `CenteredMessage("Loading your library…")`?
2. **The player's wait:** it says "Loading…". The web shows nothing over its stage. Different words, or nothing?
3. **Tab labels:** they are the accent here and ink-3/ink on the web (`title-page.css:126,131-132`). It is a colour gap, not motion. Fix it here or in a later batch?
4. **Tab-row scrolling:** `ScrollableTabRow` animates the chosen tab into view (Material, 250ms) when the row overflows a phone. The web's tab list never scrolls itself. Replacing the row is beyond this phase.
