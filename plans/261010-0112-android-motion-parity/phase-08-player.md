# Phase 08 — Player

**Spec:** `../spec.md` §2 (Player, "Stays still"), §3 (Speed). **Status:** pending. **Depends on:** 02, 07.

## Context

- **Web, the reference:**
  - HUD: `.hud { pointer-events: none; transition: opacity 260ms ease; }`, with controls opting back in (`playback.css:40-46`).
  - Resting: `dialog.resting .hud { opacity: 0 }`, and every control inside is `pointer-events: none` from the moment `resting` is set (`playback.css:63-70`, `player-card.css:32`). A tap during the fade therefore falls through to the stage. The dialog's `pointerdown` calls `show()`, which brings the HUD back (`player-hud.js:23-27,39-41`); no control acts.
  - Rest timer 2600ms (`player-hud.js:9`).
  - Tools: `transition: color var(--hover), background-color var(--hover)`; `.tool:hover` is `color: var(--stage-accent); background: rgba(255,255,255,0.08)`; disabled tools don't change (`player-card.css:79-84`). Transport: the same, `.tsp:hover` (`playback.css:197-202`).
  - Player menu rows go to `--stage-ink`, not the accent, on hover (`player-card.css:116-118`).
  - Focus inside the player rings in `--stage-accent` (`playback.css:20`). `--stage-accent` is `#e57a61` in every accent and theme (`theme.css:97`; `appearance.css` only overrides `--accent`).
  - Inventory §2b and §7: nothing else over the video moves, and no continuous animation goes on the player screen.
- **Android today** (paths under `ui-mobile/src/main/kotlin/ui/player/`):
  - The card: `PlayerScreen.kt:144-158` composes `PlayerCardLayer` only `if (cardShown)`; `PlayerCardLayer.kt:30-60` places it and `CardMenuOverStage` above it.
  - The top bar: `PlayerTopBar.kt:60-83`. The back arrow stays up regardless (Android's own way back, `:41-43`). The gradient and the title column follow `showTitle` (`:64`, `:72`).
  - The statistics hide with the bar (`PlayerScreenParts.kt:66-70`), a recorded Android choice; they keep snapping.
  - `cardShown = barShown && !sidebarFull` (`PlayerScreen.kt:121`), and `barShown` includes `!isInPip` (`:108`).
  - The precedent for blocking taps mid-fade is the cover's shield (`ui/catalog/home/HomeCover.kt:177-187`). It swallows the tap; this phase lets it through instead (Ruling 1).
  - The picture's gesture layer is the **ancestor** of everything over the video (`PlayerGestures.kt:46-61`). Its tap detector sees, in the Main pass, any tap no child consumed.
  - Linger: `CONTROLS_LINGER_MS = 4_000L` (`feature/player/src/main/kotlin/ControlsVisibility.kt:13`). It stays (user decision 5), and its comment says why.
  - The three tool wrappers `GlyphButton`, `TransportButton` and `LabelButton` (`PlayerControlParts.kt:40-108`) are `CatalogueTextButton`s after phase 02, with white labels.
  - `PlayerScreen.kt` is 210 lines before this phase; Task 8.1 brings it to about 193.
- **Surfaces drawn on the page's colours that open from over the video:**
  - the Kids menu, `CatalogueMenu`'s Material surface (`surfaceContainer`), light in the light theme;
  - `AddToListDialog` (`surfaceContainerHigh`);
  - the notice `Snackbar` (`PlayerActionNotice.kt:19`, `inverseSurface`), cream in the dark theme.

  A white tint vanishes on all three in one theme or the other.

## Rulings made here

1. **Taps fall through, they aren't swallowed.** While the controls fade out, a sibling over them is hit first and consumes nothing:
   - Compose stops hit-testing siblings at the first one hit, so the control under it never gets the tap.
   - Ancestors still get the event, so the picture's own tap detector does: the controls come back, and a double tap still seeks.
   - That is `pointer-events: none` exactly. The cover's swallowing shield would make a viewer tap twice to get the controls back.
2. **The ring over the video is the stage accent.** It is `Accent.Default.resolve(dark = true)`, the web's `--stage-accent`, not the page's accent. The same colour is the tools' hover and press colour.
3. **Menu rows don't change colour.** The web turns its player rows to `--stage-ink`, and ours are already white. Their 8% white fill is the indication's tint in the over-video tone.
4. **Colour fades are read in composition.** A tool's label (`Text`) and icon (`TransportIcon`, ui-common, shared with the TV) take a `Color`, so a 160ms fade recomposes that one leaf for about ten frames on a press. The draw-phase rule covers motion (alpha, translation, scale), and the opacity fade here is draw-phase (`AnimatedVisibility`, the scrim in `drawBehind`). Routing a colour producer through Material's `Text` and ui-common's icon would mean forking both.
5. **Picture-in-picture still snaps.** In PiP the card layer leaves composition at once, as it did, rather than fading inside the shrinking window.

## Task 8.1: The controls fade, and taps go through them as they go

**Files:**
- Modify (whole files given): `ui-mobile/src/main/kotlin/ui/player/PlayerCardLayer.kt`, `ui-mobile/src/main/kotlin/ui/player/PlayerTopBar.kt`
- Modify: `ui/player/PlayerScreen.kt` (imports `:32,34-36`, `:144-158`, `:193-205`), `ui/player/PlayerCardBridge.kt` (after `:51`), `feature/player/src/main/kotlin/ControlsVisibility.kt` (`:12`)
- Test: `ui-mobile/src/test/kotlin/ui/player/PlayerHudFadeTest.kt`

**Interfaces (produced):**
```kotlin
internal val HudFade: TweenSpec<Float>                               // 260ms, Motion.CssEase
@Composable internal fun BoxScope.TapsReachThePicture()
@Composable internal fun BoxScope.PlayerCardLayer(shown: Boolean, player: Player, …)   // was gated by `if (cardShown)`
internal fun PlayerViewModel.marksActions(): PlayerMarksActions
```

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/player/PlayerHudFadeTest.kt`:

```kotlin
package ui.player

import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The controls fade as the web's do. From the moment they start to go, a tap
 * meant for one of them reaches the picture instead (`pointer-events: none`,
 * `playback.css:63-70`): the control does nothing, and the picture brings
 * the controls back. A tablet's width, as in PlayerCardScreenTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerHudFadeTest : PlayerCardScreenBase() {
    /** Taps the picture to put the controls away and stops 100ms into their 260ms fade. */
    private fun tapAwayAndStopMidFade() {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height * 0.4f)) }
        // The picture holds a single tap back for the double-tap timeout before acting on it.
        compose.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout() + 100L)
    }

    /** Past the picture's double-tap wait and past any fade, either way. */
    private fun settle() = compose.mainClock.advanceTimeBy(1_000)

    @Test
    fun theCardFadesOutRatherThanVanishing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        settle()
        compose.onNodeWithTag(PlayerCardTag).assertDoesNotExist()
    }

    @Test
    fun aTapOnAFadingToolReachesThePictureAndTheToolDoesNothing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithContentDescription("Speed").performTouchInput { click() }
        settle()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun aTapOnAFadingMarkReachesThePictureAndTheMarkDoesNothing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithText("Add to list").performTouchInput { click() }
        settle()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Add to list").assertExists()
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerHudFadeTest'`
Expected: 3 FAIL. The card snaps away when the tap lands, so mid-fade there is no card to find, no Speed and no Add to list.

- [ ] **Step 3: Replace `PlayerCardLayer.kt`**

The whole file, as read at `:1-78`:

```kotlin
// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import designsystem.Motion
import player.EpisodeList
import player.PlayerChoices
import player.PlayerViewModel
import player.UpNextUiState
import player.playFromRun

/** The web's controls fade: opacity over 260ms on CSS `ease` (`playback.css:41`), shared by the card and the top bar. */
internal val HudFade: TweenSpec<Float> = tween(260, easing = Motion.CssEase)

/**
 * The card and the menu that opens above it, as the screen lays them out.
 * While the episode sidebar stands beside the picture the card is
 * laid out in the width it leaves, so the two never overlap: it ends short of
 * the sidebar by its own margin, centred in what remains, and goes back to
 * the window's width when the sidebar closes.
 *
 * [shown] fades the card in and out ([HudFade]). While it fades out, taps
 * meant for it go to the picture instead ([TapsReachThePicture]). Its menus
 * are shown and hidden, as the web's are. In picture-in-picture it is not
 * drawn at all: that window has no touch surface of this app's own.
 */
@Composable
internal fun BoxScope.PlayerCardLayer(
    shown: Boolean,
    player: Player,
    viewModel: PlayerViewModel,
    card: PlayerCardState,
    choices: PlayerChoices,
    upNext: UpNextUiState,
    statsShown: Boolean,
    hasEpisodes: Boolean,
    catalogedDurationSecs: Int?,
    onToggleStats: () -> Unit,
    onEnterPip: (() -> Unit)?,
    onScrubbingChanged: (Boolean) -> Unit,
) {
    if (LocalIsInPictureInPicture.current) return
    val clearance = if (card.sidebarOpen && !sidebarCoversScreen()) SIDEBAR_WIDTH else 0.dp
    AnimatedVisibility(
        visible = shown,
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = clearance),
        enter = fadeIn(HudFade),
        exit = fadeOut(HudFade),
        label = "player card",
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerControlCard(
                player = player,
                view =
                    PlayerCardView(
                        choices, upNext, statsShown, hasEpisodes = hasEpisodes, catalogedDurationSecs = catalogedDurationSecs,
                        openMenu = card.menu, sidebarOpen = card.sidebarOpen,
                    ),
                actions = playerCardActions(viewModel, card, onToggleStats = onToggleStats, onEnterPip = onEnterPip),
                onScrubbingChanged = onScrubbingChanged,
                onBounds = { card.bounds = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (!shown) TapsReachThePicture()
        }
    }
    if (shown) CardMenuOverStage(card, choices, viewModel.cardMenuActions())
}

/**
 * Laid over controls while they fade out. It is hit first, so the
 * half-hidden control under it never takes the tap; it consumes nothing, so
 * the tap goes on to the picture behind, which brings the controls back. The
 * web's `pointer-events: none` on a resting HUD (`playback.css:63-70`,
 * `player-card.css:32`).
 */
@Composable
internal fun BoxScope.TapsReachThePicture() {
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    )
}

/** The run's sidebar over the picture, which keeps playing beside it; a pick plays that title and puts the sidebar away. */
@Composable
internal fun BoxScope.PlayerEpisodeSidebar(
    list: EpisodeList,
    card: PlayerCardState,
    viewModel: PlayerViewModel,
) {
    EpisodeSidebar(
        list = list,
        onPick = { id ->
            card.sidebarOpen = false
            viewModel.playFromRun(id)
        },
        onClose = { card.sidebarOpen = false },
        modifier = Modifier.align(Alignment.CenterEnd),
    )
}
```

- [ ] **Step 4: Replace `PlayerTopBar.kt`**

The whole file, as phase 02 left it (`:1-84` as read, with its `IconButton`/`TextButton` already `CatalogueIconButton`/`CatalogueTextButton`):

```kotlin
package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import designsystem.Spacing
import kotlin.math.roundToInt
import ui.chrome.CatalogueIconButton
import ui.chrome.CatalogueTextButton
import ui.common.player.SCRIM_ALPHA

/** The top gradient the web keeps behind its own bar: [SCRIM_ALPHA] black at the top edge, clear by the bottom of the bar. */
private val TOP_GRADIENT = Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent))

/**
 * The slim bar along the top: back, what is playing, and the controls the
 * web keeps in its own top bar — My List, Kids and Add to list ([marks]) and
 * Notes ([onNotes], when the open title has notes). Picture-in-picture rides
 * in the card instead.
 *
 * The arrow stays up regardless of the card's fade — Android's own way
 * back, not something the web has. Everything else fades with [showTitle],
 * on the card's own clock ([HudFade]), and lets taps through to the picture
 * while it fades out. Beside the arrow, the title over a row of the marks and
 * Notes that wraps rather than squeezes on a narrow phone. Inset by the
 * status bar's band even while it is hidden, so the bar does not jump on
 * fullscreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    onNotes: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    marks: @Composable () -> Unit = {},
    /** Where the bar ends in the root, so a menu knows how far up it may reach. */
    onBottom: (Int) -> Unit = {},
) {
    // Read when drawn, so the scrim's fade redraws the bar and recomposes nothing.
    val scrim by animateFloatAsState(if (showTitle) 1f else 0f, HudFade, label = "top scrim")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind { drawRect(TOP_GRADIENT, alpha = scrim) }
                .onGloballyPositioned { onBottom(it.boundsInRoot().bottom.roundToInt()) }
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        verticalAlignment = Alignment.Top,
    ) {
        CatalogueIconButton(onClick = onBack, modifier = Modifier.padding(Spacing.small)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        AnimatedVisibility(
            visible = showTitle,
            modifier = Modifier.weight(1f),
            enter = fadeIn(HudFade),
            exit = fadeOut(HudFade),
            label = "top bar",
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(top = Spacing.medium, end = Spacing.small)) {
                    if (title.isNotEmpty()) {
                        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), itemVerticalAlignment = Alignment.CenterVertically) {
                        marks()
                        if (onNotes != null) CatalogueTextButton(onClick = onNotes) { Text(text = "Notes", color = Color.White) }
                    }
                }
                if (!showTitle) TapsReachThePicture()
            }
        }
    }
}
```

- [ ] **Step 5: Pass `shown` from the screen, and move the marks' wiring beside the card's**

`PlayerScreen.kt:144-158`. Before:
```kotlin
                if (cardShown) {
                    PlayerCardLayer(
                        player = current,
                        viewModel = viewModel,
                        card = card,
                        choices = choices,
                        upNext = upNext,
                        statsShown = statsShown,
                        hasEpisodes = episodes != null,
                        catalogedDurationSecs = openSet?.durationSecs,
                        onToggleStats = { statsShown = !statsShown },
                        onEnterPip = pip.enterPip.takeIf { pip.supported },
                        onScrubbingChanged = { scrubbing = it },
                    )
                }
```
After:
```kotlin
                PlayerCardLayer(
                    shown = cardShown,
                    player = current,
                    viewModel = viewModel,
                    card = card,
                    choices = choices,
                    upNext = upNext,
                    statsShown = statsShown,
                    hasEpisodes = episodes != null,
                    catalogedDurationSecs = openSet?.durationSecs,
                    onToggleStats = { statsShown = !statsShown },
                    onEnterPip = pip.enterPip.takeIf { pip.supported },
                    onScrubbingChanged = { scrubbing = it },
                )
```

`PlayerScreen.kt:193-205`. Before:
```kotlin
                marks = {
                    PlayerMarks(
                        marks = marks,
                        notice = actionNotice,
                        actions =
                            PlayerMarksActions(
                                onToggleWatchlist = viewModel::toggleWatchlist,
                                onKidsMark = viewModel::setKidsMark,
                                onSetInList = viewModel::setInList,
                                onCreateList = viewModel::createListAndAdd,
                            ),
                    )
                },
```
After:
```kotlin
                marks = { PlayerMarks(marks = marks, notice = actionNotice, actions = viewModel.marksActions()) },
```

Delete these `PlayerScreen.kt` imports; nothing else there uses them: `player.createListAndAdd` (`:32`), `player.setInList` (`:34`), `player.setKidsMark` (`:35`) and `player.toggleWatchlist` (`:36`). The file drops from 210 lines to about 193.

In `PlayerCardBridge.kt`, after `cardMenuActions` (`:51`), add:

```kotlin
/** The marks' writes for the top bar's [PlayerMarks], beside the card's, so the screen hands over one call. */
internal fun PlayerViewModel.marksActions(): PlayerMarksActions =
    PlayerMarksActions(
        onToggleWatchlist = this::toggleWatchlist,
        onKidsMark = this::setKidsMark,
        onSetInList = this::setInList,
        onCreateList = this::createListAndAdd,
    )
```
and the imports `player.createListAndAdd`, `player.setInList`, `player.setKidsMark` and `player.toggleWatchlist`.

- [ ] **Step 6: Say why the linger differs from the web's**

`feature/player/src/main/kotlin/ControlsVisibility.kt:12`. Before:
```kotlin
/** How long the bar stays after a tap, while the film is running. */
```
After:
```kotlin
/**
 * How long the bar stays after a tap, while the film is running. Longer than
 * the web's 2.6s (`player-hud.js:9`) on purpose: a finger has to travel to the
 * controls, where a mouse is already over them.
 */
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :feature:player:compileDebugKotlin :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest`
Expected: PASS. That includes the three new tests and every existing `ui.player` test:
- `PlayerCardScreenTest`'s `lingerPast()` waits 4.5s, and the card is gone by 4.26s.
- `PlayerCardNarrowSidebarTest`'s top bar still leaves at once under a full-width sidebar.
- `aMenuDoesNotComeBackAfterAnErrorTookTheCardAway` still passes, because the menu is gated on `shown`.

**If `aTapOnAFading…` finds the menu open,** the overlay is not being hit first. Check that `TapsReachThePicture()` is the last child of the `Box`. Don't make it consume the tap.

- [ ] **Step 8: Lint and commit**

```bash
./gradlew :feature:player:spotlessApply :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile feature/player
git commit -m "feat(motion): the player's controls fade over 260ms and let taps through as they go"
```

## Task 8.2: Presses over the video tint white, and tools turn to the stage accent

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/chrome/IndicationInk.kt`
- Modify (whole files given): `ui/player/PlayerControlParts.kt`, `ui/player/PlayerActionNotice.kt`
- Modify:
  - `ui/chrome/CatalogueButtons.kt` (`CatalogueTextButton`, phase 02)
  - `ui/player/PlayerGestures.kt` (`:58-61`, imports)
  - `ui/chrome/CatalogueMenu.kt` (the `Popup` content, phase 07)
  - `ui/player/AddToListDialog.kt` (`:46-91`)
- Test: `ui-mobile/src/test/kotlin/ui/player/PlayerOverVideoInkTest.kt`

**Interfaces (produced):**
```kotlin
internal val StageAccent: Color                       // Accent.Default.resolve(dark = true), the web's --stage-accent
internal val OverVideoTone: IndicationTone            // IndicationTone(ink = Color.White, ring = StageAccent)
@Composable internal fun IndicationInk(ink: Color, content: @Composable () -> Unit)   // ui.chrome
@Composable fun CatalogueTextButton(onClick, modifier, enabled, interactionSource: MutableInteractionSource? = null, content)
```
**Consumes:** `IndicationTone`, `LocalIndicationTone`, `Motion` (phase 01).

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/player/PlayerOverVideoInkTest.kt`:

```kotlin
package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import designsystem.Appearance
import designsystem.IndicationTone
import designsystem.LocalIndicationTone
import designsystem.MediagramTheme
import designsystem.Palette
import designsystem.ThemeChoice
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.CatalogueMenu

/**
 * A press over the video in the light theme, where the page's ink is
 * near-black and so is the picture: the tint has to be white there, and the
 * page's ink again on what opens from it. Tints are drawn, and nothing here
 * captures pixels (PageGroundColorTest), so these read the tone the
 * indication draws in and the colour a tool's label is laid out in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerOverVideoInkTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MediagramTheme(appearance = Appearance(theme = ThemeChoice.LIGHT)) { content() } }
        }
        compose.waitForIdle()
    }

    private fun inkOf(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun overThePictureAPressTintsWhiteAndFocusRingsInTheStageAccent() {
        var page: IndicationTone? = null
        var stage: IndicationTone? = null
        show {
            page = LocalIndicationTone.current
            PlayerGestureLayer(player = null, onToggleControls = {}, onPinchFraming = {}) { stage = LocalIndicationTone.current }
        }
        assertEquals(Palette.LightText, page?.ink)
        assertEquals(IndicationTone(ink = Color.White, ring = StageAccent), stage)
    }

    @Test
    fun aMenuOpenedFromOverThePictureTintsInThePagesInkAgain() {
        var menu: IndicationTone? = null
        show {
            PlayerGestureLayer(player = null, onToggleControls = {}, onPinchFraming = {}) {
                CatalogueMenu(expanded = true, onDismissRequest = {}, still = true) { menu = LocalIndicationTone.current }
            }
        }
        assertEquals(Palette.LightText, menu?.ink)
    }

    @Test
    fun aToolTurnsToTheStageAccentWhilePressedAndBackOnRelease() {
        show { LabelButton(label = "Fit", description = "Framing", onClick = {}) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Framing").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(StageAccent, inkOf("Fit"))
        compose.onNodeWithContentDescription("Framing").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(Color.White, inkOf("Fit"))
    }

    @Test
    fun aDimmedToolLightsTheSameWayAndGoesBackToDim() {
        show { GlyphButton(glyph = "CC", description = "Subtitles", enabled = true, onClick = {}, dimmed = true) }
        val dim = inkOf("CC")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Subtitles").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(StageAccent, inkOf("CC"))
        compose.onNodeWithContentDescription("Subtitles").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(dim, inkOf("CC"))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerOverVideoInkTest'`
Expected: compilation fails, `StageAccent` unresolved.

- [ ] **Step 3: Let `CatalogueTextButton` take a caller's interaction source**

In `ui/chrome/CatalogueButtons.kt`, replace `CatalogueTextButton`'s parameters and first line. Before:
```kotlin
@Composable
fun CatalogueTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
```
After:
```kotlin
@Composable
fun CatalogueTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Material's own name and default: a caller that colours its label on a
    // press or a hover hands in the source it reads.
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
```

- [ ] **Step 4: Write `IndicationInk.kt`**

```kotlin
package ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import designsystem.LocalIndicationTone

/**
 * Presses inside [content] tint in [ink], the colour of the surface's own
 * text, so the tint shows on the surface it is drawn on. For a surface that
 * opens from over the video, where the player tints in white: a menu or a
 * dialog on the page's colours, the notice bar on their inverse.
 */
@Composable
internal fun IndicationInk(
    ink: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIndicationTone provides LocalIndicationTone.current.copy(ink = ink), content = content)
}
```

- [ ] **Step 5: Replace `PlayerControlParts.kt`**

The whole file, as phase 02 left it (`:1-113` as read):

```kotlin
package ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Accent
import designsystem.IndicationTone
import designsystem.Motion
import ui.chrome.CatalogueTextButton
import ui.common.player.TransportIcon
import ui.common.player.TransportIcons

/*
 * The pieces the card is drawn from that hold none of its state: each is
 * handed everything it shows and decides nothing.
 *
 * Apart from the card itself because they have no tie to it — a glyph
 * button is a glyph button wherever it is pressed — and because a file is
 * easier to read when the thing it is named for is the only thing in it.
 */

/** The smallest a control over the picture is ever drawn: a thumb's width, however narrow the window it wraps in. */
internal val MIN_TARGET: Dp = 48.dp

/** Faint enough to read as off, or unavailable, beside a row of white controls. */
private const val DIM_ALPHA = 0.5f

/**
 * The web's `--stage-accent` (`theme.css:97`): the default accent's dark
 * value, whatever accent the page wears, because the stage is always dark.
 */
internal val StageAccent: Color = Accent.Default.resolve(dark = true)

/**
 * How a press looks over the video. White ink, as the web fills its tools
 * with 8% white (`player-card.css:82`, `playback.css:200`), where the page's
 * ink would vanish into a dark frame in the light theme. The focus ring is in
 * the stage's accent (`playback.css:20`).
 */
internal val OverVideoTone = IndicationTone(ink = Color.White, ring = StageAccent)

/**
 * A control drawn as a character, named for a screen reader.
 *
 * The name is not decoration: a glyph has no accessible text of its own, so
 * without this the button announces itself as nothing at all.
 */
@Composable
internal fun GlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    // A toggle's off state: drawn faint, where a row of white glyphs has no other way to show it.
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    /** What a screen reader says of a toggle or an opener — "On", "Expanded" — beyond its name; null for a plain button. */
    state: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description; state?.let { stateDescription = it } },
    ) {
        Text(
            text = glyph,
            color = toolInk(source, enabled, rest = if (dimmed || !enabled) Color.White.copy(alpha = DIM_ALPHA) else Color.White),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/**
 * [GlyphButton] for a shape with no reliable character: the transport's
 * play, pause, previous and next, which as text fall back to colour emoji
 * (see [TransportIcons]). Named for a screen reader the same way.
 */
@Composable
internal fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        TransportIcon(
            icon = icon,
            tint = toolInk(source, enabled, rest = if (enabled) Color.White else Color.White.copy(alpha = DIM_ALPHA)),
            size = MaterialTheme.typography.headlineMedium.fontSize,
        )
    }
}

/** A tool that names what is chosen — `1×`, `Fit`, `Audio` — set smaller than a glyph, since it is a word. */
@Composable
internal fun LabelButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description; state?.let { stateDescription = it } },
    ) {
        Text(text = label, color = toolInk(source, enabled, rest = Color.White), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TimeText(text: String) {
    Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
}

/**
 * A tool's colour: [rest], or the stage's accent while a finger is on it or
 * a pointer over it, crossing over `--hover` (`.tool:hover`,
 * `player-card.css:79-82`; `.tsp:hover`, `playback.css:197-200`). The 8%
 * white fill under it is the indication's own tint ([OverVideoTone]). A
 * disabled tool keeps its colour, as `:disabled:hover` does
 * (`player-card.css:84`).
 */
@Composable
private fun toolInk(
    source: InteractionSource,
    enabled: Boolean,
    rest: Color,
): Color {
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val ink by animateColorAsState(if (enabled && (hovered || pressed)) StageAccent else rest, Motion.hover(), label = "tool")
    return ink
}
```

- [ ] **Step 6: Tint white over the picture**

`PlayerGestures.kt:58-61`. Before:
```kotlin
    ) {
        content()
        SeekRipple(flash = flash, modifier = Modifier.align(Alignment.Center))
    }
```
After:
```kotlin
    ) {
        // Everything in here is drawn over the video: presses tint white ([OverVideoTone]).
        CompositionLocalProvider(LocalIndicationTone provides OverVideoTone) {
            content()
            SeekRipple(flash = flash, modifier = Modifier.align(Alignment.Center))
        }
    }
```
Add the imports `androidx.compose.runtime.CompositionLocalProvider` and `designsystem.LocalIndicationTone`.

- [ ] **Step 7: Give the page's ink back to what opens from over the video**

`ui/chrome/CatalogueMenu.kt`, the `Popup` content (phase 07, Task 7.1 Step 3). Before:
```kotlin
    ) {
        Surface(
            modifier = if (still) Modifier else Modifier.entrance(MENU_IN_MS, fromY = MENU_IN_FROM),
```
After:
```kotlin
    ) {
        // The menu is the page's own surface, wherever it opens from: over the
        // video (the player's Kids menu) its presses still tint in the page's ink.
        IndicationInk(MaterialTheme.colorScheme.onSurface) {
            Surface(
                modifier = if (still) Modifier else Modifier.entrance(MENU_IN_MS, fromY = MENU_IN_FROM),
```
Close the new block after the `Surface`'s closing brace, then add `import androidx.compose.material3.MaterialTheme`. Run `spotlessApply` to re-indent.

`ui/player/AddToListDialog.kt:46-91`, as phases 02 and 07 left it. Replace the body of `AddToListDialog` from `var newName` on with:

```kotlin
    var newName by remember { mutableStateOf("") }

    // Opened from over the video, where presses tint white: the dialog is the
    // page's own surface, so its buttons tint in the page's ink.
    IndicationInk(MaterialTheme.colorScheme.onSurface) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                NoDialogAnimation()
                Text("Add to list")
            },
            text = {
                Column {
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (lists.isEmpty()) {
                        Text("No lists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(items = lists, key = ListOfSets::id) { list ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = list.id in memberOf,
                                        onCheckedChange = { checked -> onToggle(list.id, checked) },
                                    )
                                    Text(list.name)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text("New list") },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
                    )
                }
            },
            confirmButton = {
                CatalogueTextButton(
                    onClick = {
                        onCreate(newName.trim())
                        newName = ""
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("New list") }
            },
            dismissButton = { CatalogueTextButton(onClick = onDismiss) { Text("Done") } },
        )
    }
}
```
Add `import ui.chrome.IndicationInk`.

`ui/player/PlayerActionNotice.kt`, the whole file (`:1-23` as read; phase 02 made its button a `CatalogueTextButton`):

```kotlin
package ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import designsystem.Spacing
import ui.chrome.CatalogueTextButton
import ui.chrome.IndicationInk

/** A mark write the player could not confirm, said until dismissed. */
@Composable
internal fun ActionNoticeBar(
    notice: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    notice ?: return
    // The bar is the page's colours inverted, over the video where presses
    // tint white: Dismiss tints in the bar's own ink, which shows in either theme.
    IndicationInk(MaterialTheme.colorScheme.inverseOnSurface) {
        Snackbar(
            modifier = modifier.padding(Spacing.medium),
            dismissAction = { CatalogueTextButton(onClick = onDismiss) { Text("Dismiss") } },
        ) { Text(notice) }
    }
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:testDebugUnitTest`
Expected: PASS. That includes the four new tests, `CatalogueButtonsTest`, `PlayerControlCardTest`, `PlayerNoticesTest` (Dismiss still clicks) and `PlayerKidsChoiceTest`.

**If a tool test reads white while pressed,** the press was never emitted: the button sits in no scrollable container here, so it should be at once. Advance one more frame after `down(center)` before reading. Don't move the colour into a draw lambda to make it pass.

- [ ] **Step 9: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): presses over the video tint white and tools turn to the stage accent"
```

## Device walk (phase 09)

Tablet only, `ANDROID_SERIAL=caad49da`. Check the avatar shows T (PIN 1357) before playing, and never tap "Start over".

**Animations on:**
- **The fade:** play a film and leave it. After 4s the top bar's title, marks and scrim, and the card, fade out over about a quarter of a second.
  - The back arrow stays.
  - The statistics, if on, go at once, as they did.
  - Watch for subtitles dropping at the fade's start, under a card still fading. That drop is Android's own lift, unchanged; note any overlap.
- **Tapping mid-fade:** tap where Speed was, mid-fade. The controls come back, and no menu opens.
- **Double tap mid-fade:** double-tap the left third mid-fade. It still seeks.
- **The light theme:**
  - Press and hold a tool. It turns coral, with a faint white fill.
  - Open Kids. Its rows tint dark on the light menu.
  - Open Add to list. Its buttons tint dark.
- **The dark theme:** the same. If a notice bar can be raised, Dismiss tints dark on the cream bar.
- **Picture-in-picture:** the card is gone at once, with no fade in the small window.

**"Remove animations" on:** the controls appear and vanish at once, and a tap on the picture still toggles them.

## Success criteria

- All 7 new tests pass and `:ui-mobile:testDebugUnitTest` stays green.
- `PlayerScreen.kt` is under 200 lines. Every other file touched is too.
- Over the video nothing moves but the 260ms opacity fade of the top bar and card, and a tool's 160ms colour on a press:
  - no `InfiniteTransition` or repeating animation is added under `ui/player`;
  - the menus, the sidebar, Up next and notices are unchanged.
- The linger stays at 4s, and its comment says why it is longer than the web's.

## Unresolved questions

1. **Expanded tools:** the web also colours a tool accent while its menu is open (`.tool[aria-expanded="true"]`, `player-card.css:82`). Android's openers report "Expanded" only to a screen reader. That is a colour state the app lacks, not a fade, so it is left out. Add it?
2. **The Kids menu and the linger:** the Kids menu doesn't hold the controls the way a card menu does (`PlayerCardState.somethingOpen`), so it vanishes when the top bar fades after 4s. The web's Kids `<select>` holds the HUD while focused (`player-hud.js:28-31`). This predates this plan.
