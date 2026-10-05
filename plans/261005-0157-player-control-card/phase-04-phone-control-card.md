# Phase 04: Phone/tablet control card

## Context links

- [spec.md](spec.md) — binding, incl. its User decisions table (2026-10-05)
- [plan.md](plan.md) — Global Constraints, Review Focus 2 and 3
- [phase-03-android-shared-player-model.md](phase-03-android-shared-player-model.md) — the API this phase consumes verbatim
- Web reference: phase-01 / phase-02 (match the spec, not guesses about web code)
- `DESIGN.md` (repo root) :437-440, :499-504, :733, :790-794, :808-811
- Memory: `android-device-validation` (tablet `caad49da`, look and navigate only) — the walk itself is phase 06

## Overview

- **Priority:** P1 (the phone/tablet half of the card)
- **Status:** pending
- **Depends on:** phase 03 merged on `main`. Runs in parallel with phase 05 in its own worktree; the two touch no common file.
- **What:** the phone player's controls become one frosted card at the bottom of the picture (seek / tools / transport), with in-card menus, an episode sidebar, a slim top bar holding the title, the marks and Notes. The settings bottom sheet is retired; its sections are reused inside the menus unchanged.

## Key insights

- **Every skip clamp is media3's own.** −15/+15 keep media3's `rememberSeekBackButtonState`/`rememberSeekForwardButtonState` (today `PlayerControls.kt:73-74`), whose `onClick` is `Player.seekBack()`/`seekForward()` → `BasePlayer.seekToOffset`, which floors at 0 and caps at `getDuration()` when known. A −15 at 0:05 lands on 0:00; a +15 in the last 15 s lands on the end, media3 reports `STATE_ENDED`, and phase 03's up-next path runs. The double tap and the PiP buttons already go through the same two calls (`PlayerGestures.kt:83,87`, `PipActions.kt:135-136`), so 15 s follows from `SKIP_MS` with no code here.
- **Labels are read off the player**, as today: glyph `−${seekBackAmountMs/1000}`, description `Back ${…} seconds`. With `SKIP_MS = 15_000` they read "−15" / "Back 15 seconds". A button can never say one thing and do another.
- **Menus are drawn in-tree, not as `DropdownMenu`/`Popup`.** A popup's window takes Back for itself (untestable here, and it would close by its own rules); in-tree, one `BackHandler` in `PlayerScreen` answers Back, the menu is placed with phase 03's `cardMenuOffset` directly above its opener inside the card's width, and the stage's own `positionInRoot` is subtracted because `NotesLayout` can push the stage off the origin (`NotesLayout.kt:47-57`).
- **Back order (phone):** open menu → sidebar → (nothing of the card's open) the library's own Back. `PlayerScreen`'s `BackHandler` is composed after `LibraryFlowBranches`' `BackHandler(onBack = at::pop)` (`ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt:143`) and is only `enabled` while something is open, so it answers first and otherwise steps aside.
- **A tap on the picture with a menu open closes the menu** instead of toggling the card — the touch idiom for "tap outside". With the sidebar open, a tap still toggles the card; the sidebar stays until ✕, Back or a pick (the spec's three ways).
- **One state holder, `PlayerCardState`**, carries the open menu, the sidebar, the card's bounds, the stage origin and the openers' anchors. Back, auto-hide, the card and the menus all read it, so none keeps a second copy; its rules (one menu at a time, Back order, tap-dismiss) are plain JVM-testable.
- **The subtitle style panel stays open while used** (ruling): size, backing and a sync nudge are judged against the film a step at a time. Every other menu closes when a value is chosen.
- **CC is disabled, not hidden**, without a regular track (`PlayerChoices.ccVisible`, `feature/player/.../PlayerChoices.kt:42`), so the row does not shift between titles. ▾ is enabled for any title with a track that can show (`ccVisible || subtitleStyleVisible`): a forced-only title gets Style… with no language rows.
- **Row 3 is one centred `FlowRow`** (ruling): ↺ ⏮ −15 ▶/❚❚ +15 ⏭ ⓘ ☰ in that order. A Material `TextButton` is at least 58dp wide (`ButtonDefaults.MinWidth`) and a glyph like "−15" more, so nine of them cannot share a 304dp row on a 360dp phone; the row wraps rather than shrinks. ⓘ ☰ follow the transport instead of being pinned to the far end — pinning would need a weighted spacer inside a wrapping row, which reorders unpredictably on wrap.
- **Top bar is two lines beside ←** (ruling): the title, then a `FlowRow` of My List · Kids · Add to list · Notes. One line cannot hold a title plus four text buttons at 360dp; this keeps every target ≥48dp and the title ellipsizing. It sits over a top gradient (`SCRIM_ALPHA` → clear), the spec's "top gradient kept"; the marks lose their own scrim box.
- **Menu rows are the sheet's sections, with their logic unchanged** (spec: "Their contents are not rewritten" means the choices and their wiring stay; it does not mean the row height). **Ruling (lead, 2026-10-05):** the 48 dp floor in Global Constraints holds for menu rows too. In Task 3, every radio row the menus reuse (`SpeedRow`, and the row composables inside `SubtitleSection`, `AudioSection` and `FramingSection`) gets `Modifier.heightIn(min = MIN_TARGET)`. Task 3's test asserts at least 48 dp on one row of each menu.
- **File facades must not clash.** ui-common and ui-mobile share the package `ui.player`; a ui-mobile file named like a ui-common one (`PlayerCardSurface`, `TransportIcons`, `PlayerLifecycle`, `SubtitleLayer`, …) would compile to the same `ui/player/XxxKt.class` twice and fail `:app`'s duplicate-class check. Every new file name here is checked against `ls android/ui-common/src/main/kotlin/ui/player/`.
- **Sidebar width** reads `LocalConfiguration.current.screenWidthDp` (the codebase's own idiom, `ui/catalog/ArtTile.kt:94`): 320dp, or the whole width under 600dp.

## Requirements

Functional
- Card bottom-centre: full width minus 12dp each side, capped at 720dp, 12dp above the bottom edge, inside the safe-drawing insets; `Modifier.playerCard()` fill; 16dp inside.
- Row 1: position · scrub bar · `58:30 · ends 23:41`. Row 2: CC, ▾, Speed (`1×`…), Audio (hidden with one track), Framing (`Fit`…), PiP (where offered). Row 3: ↺ ⏮ −15 ▶/❚❚ +15 ⏭ ⓘ ☰.
- ⏮ ⏭ hidden with no run, disabled at the run's ends; ☰ hidden when `episodes == null`.
- Menus: one at a time, directly above their button, inside the card's width; choosing closes (style panel excepted); Back closes without choosing.
- Episode sidebar: right, full height, 320dp (full width under 600dp), `‹ Season N ›` switcher opening on the current season, rows with number/title/runtime, watched at 45% with ✓, progress line, "Now playing" not actionable; a pick plays through `playFromRun` and closes.
- Top bar: ←, title, marks, Notes. PiP moves into the card.
- Hiding: card and top bar hide on today's timer; never while paused, scrubbing, a menu open, or the sidebar open.
- Retire `PlayerSettingsSheet` and its bridge; retire `PlayerControls`.

Non-functional
- Every card target ≥48dp; rows wrap, never shrink. Files ≤ ~200 lines. No new dependencies. Accessible names exactly as the spec lists them.

## Related code files

All under `android/ui-mobile/src/` unless noted.

- **Create (main, `main/kotlin/ui/player/`):** `PlayerCardState.kt`, `PlayerControlCard.kt`, `PlayerCardTools.kt`, `PlayerCardTransport.kt`, `PlayerCardMenus.kt`, `SpeedSection.kt`, `EpisodeSidebar.kt`, `EpisodeSidebarRow.kt`, `PlayerCardBridge.kt`
- **Modify (main):** `PlayerControlParts.kt`, `PlayerScrubber.kt`, `PlayerScreen.kt`, `PlayerScreenParts.kt`, `PlayerTopBar.kt`, `PlayerMarks.kt`, `PlayerScreenLifecycle.kt`; comment-only: `AudioSection.kt`, `FramingSection.kt`, `SubtitleSection.kt`, `SubtitleStyleSection.kt`, `PlayerGestures.kt`, `ImmersiveEffect.kt`, `PipActions.kt`, `UpNextCard.kt`
- **Delete (main):** `PlayerControls.kt`, `PlayerSettingsSheet.kt`, `PlayerSettingsSheetBridge.kt`
- **Tests (`test/kotlin/ui/player/`):** create `PlayerCardStateTest.kt`, `PlayerControlCardTest.kt`, `EpisodeSidebarTest.kt`, `PlayerCardScreenTest.kt`; rename + rewrite `PlayerControlsWidthTest.kt` → `PlayerControlCardWidthTest.kt`, `PlayerSettingsSheetGatesTest.kt` → `CardMenuGatesTest.kt`; modify `PlayerGesturesTest.kt`, `PlayerTestActivity.kt`, `PlayerLifecycleFixture.kt`
- **Docs:** `DESIGN.md` (repo root) — owned by this phase only; phase 05 does not touch it.

## Success criteria

- `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest` passes (every test in this phase plus the untouched suite: notices, kids choice, notes placement, PiP, seek ripple).
- `cd android && ./gradlew -q :app:checkDebugDuplicateClasses` passes (no facade clash with ui-common).
- `grep -rn "PlayerSettingsSheet\|PlayerControls(" android/ui-mobile/src` returns nothing.
- Review Focus 2: `PlayerCardScreenTest.backWithAMenuOpenClosesOnlyTheMenu` and `backClosesTheMenuThenTheSidebar` pass. Review Focus 3: `anOpenMenuKeepsTheCardUpWhilePlaying` and `anOpenSidebarKeepsTheCardUpWhilePlaying` pass, and `withNothingOpenTheCardTakesItselfAwayWhilePlaying` proves the timer still runs.
- `DESIGN.md` names the card, its tokens and the no-blur reason; no "player has not been restyled" sentence remains.

## Risks

| Risk | L × I | Mitigation |
|---|---|---|
| A ui-mobile file name equals a ui-common one → duplicate class in `:app` | M × H | names checked above; `:app:checkDebugDuplicateClasses` in Task 5 |
| Menu placed from stale anchors (first frame, rotation) | M × L | panel drawn at alpha 0 until measured; anchors re-reported by `onGloballyPositioned` on every layout |
| `LazyColumn` key clash if a run lists one id twice | L × H | resolved in phase 03: `episodeListOf` keeps one row per distinct id (`EpisodeListTest.aTitleTheRunHoldsTwiceIsListedOnce`), so `setId` keys are unique |
| Tap on a non-clickable part of a menu/sidebar reaches the gesture layer and toggles the card | M × L | same as today's bar (tapping its clock toggles it); menus close on a picture tap anyway |
| Removing the marks' own scrim makes them faint over a bright frame | L × M | the top gradient starts at `SCRIM_ALPHA` behind the bar; check on the tablet walk (phase 06) |
| Robolectric reports no PiP feature, so PiP never shows in screen tests | H × L | PiP covered at component level (`onEnterPip` non-null) |

## Security considerations

None: no new data, no new permission, no network. The sidebar shows titles the catalogue already lists.

## Next steps

- Phase 06 walks the tablet (`ANDROID_SERIAL=caad49da`, look and navigate only — never pick a menu value or a sidebar row) and checks `DESIGN.md` against the merged values.

---

### Task 1: Card state holder

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/player/PlayerCardState.kt`
- Test: `android/ui-mobile/src/test/kotlin/ui/player/PlayerCardStateTest.kt`

**Interfaces:**
- Consumes: nothing new (Compose runtime state only).
- Produces:
  ```kotlin
  internal enum class CardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }
  @Stable internal class PlayerCardState {
      var menu: CardMenu?            // private set
      var sidebarOpen: Boolean
      var bounds: IntRect?
      var origin: IntOffset
      val somethingOpen: Boolean
      fun toggle(next: CardMenu)
      fun switchTo(next: CardMenu)
      fun closeMenu()
      fun toggleSidebar()
      fun closeTopmost()
      fun dismissMenu(): Boolean
      fun anchor(menu: CardMenu, bounds: IntRect)
      fun anchorOf(menu: CardMenu): IntRect?
  }
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.player

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the card has open, and the order Back and a tap close it in. */
class PlayerCardStateTest {

    @Test
    fun openingAMenuWhileAnotherIsOpenReplacesIt() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)
        card.toggle(CardMenu.Audio)

        assertEquals(CardMenu.Audio, card.menu)
    }

    @Test
    fun pressingTheOpenMenusButtonAgainClosesIt() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)
        card.toggle(CardMenu.Speed)

        assertNull(card.menu)
        assertFalse(card.somethingOpen)
    }

    @Test
    fun backClosesTheMenuBeforeTheSidebar() {
        val card = PlayerCardState()
        card.toggleSidebar()
        card.toggle(CardMenu.Framing)
        assertTrue(card.somethingOpen)

        card.closeTopmost()
        assertNull(card.menu)
        assertTrue(card.sidebarOpen)

        card.closeTopmost()
        assertFalse(card.sidebarOpen)
        assertFalse(card.somethingOpen)
    }

    @Test
    fun theEpisodesButtonClosesAnOpenMenuAndTogglesTheSidebar() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)

        card.toggleSidebar()
        assertNull(card.menu)
        assertTrue(card.sidebarOpen)

        card.toggleSidebar()
        assertFalse(card.sidebarOpen)
    }

    @Test
    fun aTapOnThePictureClosesAnOpenMenuRatherThanTheCard() {
        val card = PlayerCardState()
        assertFalse(card.dismissMenu(), "nothing open: the tap is the card's")

        card.toggle(CardMenu.Audio)
        assertTrue(card.dismissMenu())
        assertNull(card.menu)
    }

    @Test
    fun theStylePanelOpensInPlaceAboveTheSameButton() {
        val card = PlayerCardState()
        val dropDown = IntRect(left = 100, top = 900, right = 148, bottom = 948)
        card.anchor(CardMenu.Subtitles, dropDown)
        card.toggle(CardMenu.Subtitles)

        card.switchTo(CardMenu.SubtitleStyle)

        assertEquals(CardMenu.SubtitleStyle, card.menu)
        assertEquals(dropDown, card.anchorOf(CardMenu.SubtitleStyle))
    }
}
```

- [ ] **Step 2: Run it** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerCardStateTest'` — expected FAIL (unresolved `PlayerCardState`, `CardMenu`).

- [ ] **Step 3: Minimal implementation** — `PlayerCardState.kt`:

```kotlin
package ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/**
 * The card's small menus, one open at a time. [SubtitleStyle] has no button
 * of its own: the subtitle menu's "Style…" row opens it, above the same ▾.
 */
internal enum class CardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }

/**
 * What the card has open and where its pieces sit — one holder, read by the
 * screen, the card, its menus and Back, so none of them keeps its own copy of
 * the answer. Lives as long as the screen's composition: a rotation closes
 * whatever was open, which is what a tap away would have done.
 */
@Stable
internal class PlayerCardState {
    var menu by mutableStateOf<CardMenu?>(null)
        private set

    var sidebarOpen by mutableStateOf(false)

    /** The card's own bounds in root coordinates, once laid out — what a menu stays inside. */
    var bounds by mutableStateOf<IntRect?>(null)

    /** Where the stage sits in the root; the notes column can push it off the origin. */
    var origin by mutableStateOf(IntOffset.Zero)

    private val anchors = mutableStateMapOf<CardMenu, IntRect>()

    /** Whether Back, and the controls' fade, have something of the card's to answer to first. */
    val somethingOpen: Boolean get() = menu != null || sidebarOpen

    /** A menu's button: opens it, replacing any other, or closes it when it is already the one open. */
    fun toggle(next: CardMenu) {
        menu = if (menu == next) null else next
    }

    /** Moves the open menu to [next] in place — the subtitle menu's "Style…" row. */
    fun switchTo(next: CardMenu) {
        menu = next
    }

    /** A value was chosen: the menu has done its job. */
    fun closeMenu() {
        menu = null
    }

    /** ☰: closes any menu, so the sidebar is the one thing open, or puts the sidebar away. */
    fun toggleSidebar() {
        menu = null
        sidebarOpen = !sidebarOpen
    }

    /** Back while [somethingOpen]: the menu first, since it opened last, then the sidebar. */
    fun closeTopmost() {
        if (menu != null) menu = null else sidebarOpen = false
    }

    /** A tap on the picture with a menu open closes the menu instead of the card; true when it did. */
    fun dismissMenu(): Boolean {
        if (menu == null) return false
        menu = null
        return true
    }

    fun anchor(menu: CardMenu, bounds: IntRect) {
        anchors[menu] = bounds
    }

    /** What [menu] is drawn above: its own button, or for the style panel the ▾ that led to it. */
    fun anchorOf(menu: CardMenu): IntRect? = anchors[if (menu == CardMenu.SubtitleStyle) CardMenu.Subtitles else menu]
}
```

- [ ] **Step 4: Run** — same command — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ui/player/PlayerCardState.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerCardStateTest.kt
git commit -m "feat(player): card state for the phone player's menus and episode sidebar"
```

---

### Task 2: The card — seek, tools and transport rows

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/player/PlayerControlCard.kt`, `PlayerCardTools.kt`, `PlayerCardTransport.kt`
- Modify: `android/ui-mobile/src/main/kotlin/ui/player/PlayerControlParts.kt` (whole file, 74 lines), `PlayerScrubber.kt:15` (doc) and `:48-56` (clock row)
- Test: create `android/ui-mobile/src/test/kotlin/ui/player/PlayerControlCardTest.kt`; `git mv` `PlayerControlsWidthTest.kt` → `PlayerControlCardWidthTest.kt` and rewrite it

**Interfaces:**
- Consumes: `Modifier.playerCard()` (phase 03, ui-common `PlayerCardSurface.kt`); `TransportIcons.Previous` (phase 03), `TransportIcons.Next/Play/Pause` (`ui-common/.../TransportIcons.kt:31-83`); `UpNextUiState.run/hasPrevious/inRun` (phase 03), `UpNextUiState.hasNext` (`feature/player/.../UpNextState.kt:15`); `PlayerChoices.ccVisible/subtitlesOn/subtitleStyleVisible/audioOptions/framing/speed` (`PlayerChoices.kt:20-46`); `speedLabel` (`PlaybackSpeed.kt:10`); `endsLine` (`EndsAt.kt:57`); `READOUT_TICK_MS` (`ControlsVisibility.kt:23`); `PlayerScrubber` (`PlayerScrubber.kt:19`); `GlyphButton`/`TransportButton`/`TimeText` (`PlayerControlParts.kt:29,56,72`); `Framing.label` (`core/playback/.../Framing.kt:19`); `CardMenu` (Task 1).
- Produces:
  ```kotlin
  internal const val PlayerCardTag = "player-card"
  internal class PlayerCardActions(
      val onRestart: () -> Unit, val onPrevious: () -> Unit, val onNext: () -> Unit,
      val onToggleSubtitles: () -> Unit, val onOpenMenu: (CardMenu) -> Unit,
      val onToggleStats: () -> Unit, val onEpisodes: () -> Unit,
      val onEnterPip: (() -> Unit)?, val onAnchor: (CardMenu, IntRect) -> Unit = { _, _ -> },
  )
  internal class PlayerCardView(val choices: PlayerChoices, val upNext: UpNextUiState, val statsShown: Boolean, val hasEpisodes: Boolean, val catalogedDurationSecs: Int?)
  @Composable internal fun PlayerControlCard(player: Player, view: PlayerCardView, actions: PlayerCardActions, onScrubbingChanged: (Boolean) -> Unit, modifier: Modifier = Modifier, onBounds: (IntRect) -> Unit = {})
  @Composable internal fun CardToolsRow(choices: PlayerChoices, actions: PlayerCardActions)
  @Composable internal fun CardTransportRow(player: Player, upNext: UpNextUiState, statsShown: Boolean, hasEpisodes: Boolean, actions: PlayerCardActions)
  internal val MIN_TARGET: Dp  // 48.dp
  @Composable internal fun GlyphButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit, dimmed: Boolean = false, modifier: Modifier = Modifier)
  @Composable internal fun TransportButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)
  @Composable internal fun LabelButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
  ```

- [ ] **Step 1: Write the failing tests**

`PlayerControlCardTest.kt`:

```kotlin
package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.Framing
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.UpNextUiState
import kotlin.test.assertEquals

/**
 * What the card offers for what is open: ⏮ ⏭ and ☰ follow the run, CC is
 * disabled rather than hidden without a track, the tools name the current
 * choice, the skips read the player's own fifteen seconds, and each button
 * asks for its own thing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerControlCardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val pressed = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val subtitled =
        PlayerChoices(
            subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false)),
            subtitleStyleVisible = true,
        )

    private fun show(
        choices: PlayerChoices = PlayerChoices(),
        upNext: UpNextUiState = UpNextUiState(),
        hasEpisodes: Boolean = false,
    ) {
        val player =
            mockk<Player>(relaxed = true) {
                every { seekBackIncrement } returns 15_000L
                every { seekForwardIncrement } returns 15_000L
            }
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    PlayerControlCard(
                        player = player,
                        view = PlayerCardView(choices, upNext, statsShown = false, hasEpisodes = hasEpisodes, catalogedDurationSecs = 600),
                        actions =
                            PlayerCardActions(
                                onRestart = { pressed += "restart" },
                                onPrevious = { pressed += "previous" },
                                onNext = { pressed += "next" },
                                onToggleSubtitles = { pressed += "cc" },
                                onOpenMenu = { pressed += "menu:$it" },
                                onToggleStats = { pressed += "stats" },
                                onEpisodes = { pressed += "episodes" },
                                onEnterPip = null,
                            ),
                        onScrubbingChanged = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aFilmHasNeitherStepsNorEpisodes() {
        show()

        compose.onNodeWithContentDescription("Restart").assertExists()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
    }

    @Test
    fun theFirstTitleOfARunHasPreviousDisabledAndNextEnabled() {
        show(upNext = UpNextUiState(run = listOf("a", "b"), hasNext = true), hasEpisodes = true)

        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next").assertIsEnabled()
        compose.onNodeWithContentDescription("Episodes").assertIsEnabled()
    }

    @Test
    fun withNoSubtitleTrackCcIsDisabledNotHidden() {
        show()

        compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Subtitle options").assertIsNotEnabled()
    }

    @Test
    fun aSingleAudioTrackOffersNoAudioMenu() {
        show(subtitled)

        compose.onNodeWithContentDescription("Audio").assertDoesNotExist()
    }

    @Test
    fun speedAndFramingSayWhatIsChosen() {
        show(PlayerChoices(speed = 1.5f, framing = Framing.FILL))

        compose.onNodeWithText("1.5×").assertExists()
        compose.onNodeWithText("Fill").assertExists()
    }

    @Test
    fun theSkipsSayThePlayersOwnFifteenSeconds() {
        show()

        compose.onNodeWithText("−15").assertExists()
        compose.onNodeWithText("+15").assertExists()
        compose.onNodeWithContentDescription("Back 15 seconds").assertExists()
        compose.onNodeWithContentDescription("Forward 15 seconds").assertExists()
    }

    @Test
    fun eachButtonAsksForItsOwnThing() {
        show(subtitled, UpNextUiState(run = listOf("a", "b", "c"), hasPrevious = true, hasNext = true), hasEpisodes = true)

        for (name in listOf("Restart", "Previous", "Next", "Subtitles", "Subtitle options", "Speed", "Framing", "Stats", "Episodes")) {
            compose.onNodeWithContentDescription(name).performClick()
        }

        assertEquals(
            listOf("restart", "previous", "next", "cc", "menu:Subtitles", "menu:Speed", "menu:Framing", "stats", "episodes"),
            pressed,
        )
    }
}
```

`PlayerControlCardWidthTest.kt` (after `git mv android/ui-mobile/src/test/kotlin/ui/player/PlayerControlsWidthTest.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerControlCardWidthTest.kt`):

```kotlin
package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.AudioOption
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.UpNextUiState
import kotlin.test.assertTrue

/**
 * The card at real phone widths, measured with real text, with every control
 * it can carry: each keeps a full 48dp touch target, because a row wraps
 * rather than squeezing its last buttons. On a tablet the card stops at 720dp.
 */
abstract class PlayerControlCardWidthCases {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    protected fun show() {
        val player =
            mockk<Player>(relaxed = true) {
                every { seekBackIncrement } returns 15_000L
                every { seekForwardIncrement } returns 15_000L
            }
        val choices =
            PlayerChoices(
                speed = 1.5f,
                audioOptions = listOf(AudioOption(0, 0, "en", "English", true), AudioOption(1, 0, "de", "German", false)),
                subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false)),
                subtitleStyleVisible = true,
            )
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerControlCard(
                            player = player,
                            view =
                                PlayerCardView(
                                    choices = choices,
                                    upNext = UpNextUiState(run = listOf("a", "b", "c"), hasPrevious = true, hasNext = true),
                                    statsShown = false,
                                    hasEpisodes = true,
                                    catalogedDurationSecs = 600,
                                ),
                            actions =
                                PlayerCardActions(
                                    onRestart = {}, onPrevious = {}, onNext = {}, onToggleSubtitles = {},
                                    onOpenMenu = {}, onToggleStats = {}, onEpisodes = {}, onEnterPip = {},
                                ),
                            onScrubbingChanged = {},
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val names =
        listOf(
            "Restart", "Previous", "Back 15 seconds", "Play", "Forward 15 seconds", "Next", "Stats", "Episodes",
            "Subtitles", "Subtitle options", "Speed", "Audio", "Framing", "Picture in picture",
        )

    @Test
    fun everyControlKeepsAFullTouchTarget() {
        show()
        for (name in names) {
            compose.onNodeWithContentDescription(name).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardWidth360Test : PlayerControlCardWidthCases()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardWidth411Test : PlayerControlCardWidthCases()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardTabletTest : PlayerControlCardWidthCases() {
    @Test
    fun onATabletTheCardStopsAt720dp() {
        show()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue((card.right - card.left).value <= 720.5f, "the card is ${card.right - card.left} wide")
    }
}
```

- [ ] **Step 2: Run them** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerControlCardTest' --tests 'ui.player.PlayerControlCardWidth*' --tests 'ui.player.PlayerControlCardTabletTest'` — expected FAIL (unresolved `PlayerControlCard`, `PlayerCardView`, `PlayerCardActions`, `PlayerCardTag`).

- [ ] **Step 3: Minimal implementation**

`PlayerControlParts.kt` — replace the whole file:

```kotlin
package ui.player

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            color = if (dimmed || !enabled) Color.White.copy(alpha = DIM_ALPHA) else Color.White,
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
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        TransportIcon(
            icon = icon,
            tint = if (enabled) Color.White else Color.White.copy(alpha = DIM_ALPHA),
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
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TimeText(text: String) {
    Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
}
```

`PlayerScrubber.kt` — line 15 doc: `split out of [PlayerControls]` → `split out of [PlayerControlCard]`; replace the right-hand clock (`:48-56`, the `Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) { … }` block) with one line, so row 1 reads `58:30 · ends 23:41`:

```kotlin
        TimeText(listOf(clockTime(durationMs), endsLabel).filter(String::isNotEmpty).joinToString(" · "))
```

and drop the now-unused `designsystem.Spacing` import.

`PlayerControlCard.kt`:

```kotlin
// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import designsystem.Spacing
import player.PlayerChoices
import player.READOUT_TICK_MS
import player.UpNextUiState
import player.endsLine

/** Finds the card in a test. */
internal const val PlayerCardTag = "player-card"

/** Past this a tablet's card reads as a strip across the picture rather than a card on it. */
private val CARD_MAX_WIDTH = 720.dp

/** The card's distance from the window's sides and bottom edge. */
private val CARD_MARGIN = 12.dp

/** What the card's buttons do, to the ViewModel and to the card's own state — one bundle, so its call site hands over one thing. */
internal class PlayerCardActions(
    val onRestart: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToggleSubtitles: () -> Unit,
    val onOpenMenu: (CardMenu) -> Unit,
    val onToggleStats: () -> Unit,
    val onEpisodes: () -> Unit,
    /** Null where picture-in-picture is not offered, which leaves its button out. */
    val onEnterPip: (() -> Unit)?,
    /** Where each menu's button sits, in root coordinates — what the menu is drawn above. */
    val onAnchor: (CardMenu, IntRect) -> Unit = { _, _ -> },
)

/** What the card reads, beside the player itself. */
internal class PlayerCardView(
    val choices: PlayerChoices,
    val upNext: UpNextUiState,
    val statsShown: Boolean,
    /** Whether the open title has a run to list; ☰ is left out without one. */
    val hasEpisodes: Boolean,
    /** The catalogue's own runtime in whole seconds, trusted over media3's until it has one; see [endsLine]. */
    val catalogedDurationSecs: Int?,
)

/**
 * The player's controls as one card at the bottom of the picture: where the
 * film is (row 1), how it plays (row 2, [CardToolsRow]) and what moves it
 * (row 3, [CardTransportRow]) — the web's card, filled with a flat dark tint
 * instead of its blur (see [playerCard] for why).
 *
 * Everything about the playhead is read through media3's own state holders
 * rather than carried through the ViewModel — see
 * `feature/player/build.gradle.kts` for where that line is drawn.
 *
 * Inset by the navigation bar and any side cutout, then by its own margin,
 * so a drag meant for the scrub bar never lands on a three-button
 * navigation bar. [onBounds] reports the card itself, margin excluded: the
 * subtitles lift clear of its top, and a menu stays inside its width.
 */
@Composable
internal fun PlayerControlCard(
    player: Player,
    view: PlayerCardView,
    actions: PlayerCardActions,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBounds: (IntRect) -> Unit = {},
) {
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)

    // Null except mid-drag, when it holds where the thumb is rather than
    // where the film is. A slider snapped back to the playhead twice a
    // second could not be dragged at all — the web solves this the same way.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrubbingTo == null) { onScrubbingChanged(scrubbingTo != null) }

    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val positionMs = scrubbingTo?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)
    // Counted from the playhead, not the scrub thumb, which only previews where a seek would land.
    val endsLabel =
        endsLine(
            cataloguedSecs = view.catalogedDurationSecs,
            positionMs = progress.currentPositionMs,
            durationMs = progress.durationMs,
            speed = view.choices.speed,
            nowMs = System.currentTimeMillis(),
        )

    Column(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(start = CARD_MARGIN, end = CARD_MARGIN, bottom = CARD_MARGIN)
                .widthIn(max = CARD_MAX_WIDTH)
                .fillMaxWidth()
                .onGloballyPositioned { onBounds(it.boundsInRoot().roundToIntRect()) }
                .testTag(PlayerCardTag)
                .playerCard()
                .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        PlayerScrubber(
            positionMs = positionMs,
            durationMs = durationMs,
            endsLabel = endsLabel,
            scrubbingTo = scrubbingTo,
            onScrubbingToChange = { scrubbingTo = it },
            onSeek = player::seekTo,
        )
        CardToolsRow(choices = view.choices, actions = actions)
        CardTransportRow(player = player, upNext = view.upNext, statsShown = view.statsShown, hasEpisodes = view.hasEpisodes, actions = actions)
    }
}
```

`PlayerCardTools.kt`:

```kotlin
package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.roundToIntRect
import designsystem.Spacing
import player.PlayerChoices
import player.speedLabel

/**
 * The card's middle row: what changes how the title plays, never where it is.
 *
 * CC turns regular subtitles on and off in one press, and is disabled rather
 * than hidden for a title without a regular track, so the row does not shift
 * from one title to the next; ▾ beside it opens the languages and the style,
 * and stays open to a forced-only title, whose lines still take a size.
 * Speed and Framing name the current choice. Audio is left out for a single
 * track, which is a label, not a choice. Picture-in-picture rides at the end
 * on a phone, as volume and fullscreen do on the web.
 *
 * Wraps rather than squeezes: on a 360dp phone a row of 48dp targets does
 * not fit one line.
 */
@Composable
internal fun CardToolsRow(
    choices: PlayerChoices,
    actions: PlayerCardActions,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "CC",
            description = "Subtitles",
            enabled = choices.ccVisible,
            onClick = actions.onToggleSubtitles,
            dimmed = !choices.subtitlesOn,
        )
        GlyphButton(
            glyph = "▾",
            description = "Subtitle options",
            enabled = choices.ccVisible || choices.subtitleStyleVisible,
            onClick = { actions.onOpenMenu(CardMenu.Subtitles) },
            modifier = actions.anchorFor(CardMenu.Subtitles),
        )
        LabelButton(
            label = speedLabel(choices.speed),
            description = "Speed",
            onClick = { actions.onOpenMenu(CardMenu.Speed) },
            modifier = actions.anchorFor(CardMenu.Speed),
        )
        if (choices.audioOptions.isNotEmpty()) {
            LabelButton(
                label = "Audio",
                description = "Audio",
                onClick = { actions.onOpenMenu(CardMenu.Audio) },
                modifier = actions.anchorFor(CardMenu.Audio),
            )
        }
        LabelButton(
            label = choices.framing.label,
            description = "Framing",
            onClick = { actions.onOpenMenu(CardMenu.Framing) },
            modifier = actions.anchorFor(CardMenu.Framing),
        )
        actions.onEnterPip?.let { enter ->
            GlyphButton(glyph = "⧉", description = "Picture in picture", enabled = true, onClick = enter)
        }
    }
}

/** Reports where [menu]'s button sits, in root coordinates, so the menu can be drawn directly above it. */
private fun PlayerCardActions.anchorFor(menu: CardMenu): Modifier =
    Modifier.onGloballyPositioned { onAnchor(menu, it.boundsInRoot().roundToIntRect()) }
```

`PlayerCardTransport.kt`:

```kotlin
// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import designsystem.Spacing
import player.UpNextUiState

/**
 * The card's bottom row: ↺ ⏮ −15 ▶/❚❚ +15 ⏭, then ⓘ and ☰.
 *
 * ↺ goes back to 0:00 of this title; ⏮ is never that — it opens the title
 * before this one in the run, and is disabled on the first. Both ⏮ and ⏭
 * are left out for a title with no run (a film) rather than shown disabled.
 *
 * The skips are media3's own: their labels and their amount are read back
 * off the player, and `Player.seekBack`/`seekForward` keep a skip inside the
 * title — never before 0:00, never past the end, where the title then ends
 * the ordinary way and up next follows.
 */
@Composable
internal fun CardTransportRow(
    player: Player,
    upNext: UpNextUiState,
    statsShown: Boolean,
    hasEpisodes: Boolean,
    actions: PlayerCardActions,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val back = seekBack.seekBackAmountMs / 1_000
    val forward = seekForward.seekForwardAmountMs / 1_000

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(glyph = "↺", description = "Restart", enabled = true, onClick = actions.onRestart)
        if (upNext.inRun) {
            TransportButton(icon = TransportIcons.Previous, description = "Previous", enabled = upNext.hasPrevious, onClick = actions.onPrevious)
        }
        GlyphButton(glyph = "−$back", description = "Back $back seconds", enabled = seekBack.isEnabled, onClick = seekBack::onClick)
        TransportButton(
            icon = if (playPause.showPlay) TransportIcons.Play else TransportIcons.Pause,
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
        )
        GlyphButton(glyph = "+$forward", description = "Forward $forward seconds", enabled = seekForward.isEnabled, onClick = seekForward::onClick)
        if (upNext.inRun) {
            TransportButton(icon = TransportIcons.Next, description = "Next", enabled = upNext.hasNext, onClick = actions.onNext)
        }
        // Apart from what moves the film: ⓘ only reports, ☰ only lists.
        GlyphButton(glyph = "ⓘ", description = "Stats", enabled = true, onClick = actions.onToggleStats, dimmed = !statsShown)
        if (hasEpisodes) GlyphButton(glyph = "☰", description = "Episodes", enabled = true, onClick = actions.onEpisodes)
    }
}
```

- [ ] **Step 4: Run** — same command — expected PASS. Also `cd android && ./gradlew -q :ui-mobile:compileDebugKotlin` (the old `PlayerControls.kt` still compiles against the widened `GlyphButton`/`TransportButton`; `SubtitleStyleSection`'s `SyncRow` calls `GlyphButton` by name and is unaffected).

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ui/player/PlayerControlCard.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerCardTools.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerCardTransport.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerControlParts.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerScrubber.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerControlCardTest.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerControlCardWidthTest.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerControlsWidthTest.kt
git commit -m "feat(player): phone control card with seek, tools and transport rows"
```

---

### Task 3: Card menus

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/player/PlayerCardMenus.kt`, `SpeedSection.kt`
- Test: `git mv android/ui-mobile/src/test/kotlin/ui/player/PlayerSettingsSheetGatesTest.kt android/ui-mobile/src/test/kotlin/ui/player/CardMenuGatesTest.kt` and rewrite it

**Interfaces:**
- Consumes: `cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset` and `Modifier.playerCard()` (phase 03, ui-common); `PlayerCardState.menu/bounds/origin/anchorOf/switchTo/closeMenu` (Task 1); `SubtitleSection` (`SubtitleSection.kt:26`), `SubtitleStyleSection` (`SubtitleStyleSection.kt:29`), `AudioSection` (`AudioSection.kt:27`), `FramingSection` (`FramingSection.kt:27`) — all unchanged; the sheet's `SpeedRow` (`PlayerSettingsSheet.kt:103-114`, moved); `PLAYBACK_SPEEDS`, `speedLabel` (`PlaybackSpeed.kt:7,10`).
- Produces:
  ```kotlin
  internal const val CardMenuTag = "player-card-menu"
  internal class CardMenuActions(
      val onSpeed: (Float) -> Unit, val onAudio: (AudioOption) -> Unit, val onSubtitle: (String) -> Unit,
      val onSize: (Int) -> Unit, val onBacking: (String) -> Unit, val onNudge: (Int) -> Unit,
      val onResetOffset: () -> Unit, val onFraming: (Framing) -> Unit,
  )
  @Composable internal fun CardMenuPanel(menu: CardMenu, choices: PlayerChoices, actions: CardMenuActions, onOpen: (CardMenu) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier)
  @Composable internal fun BoxScope.CardMenuOverStage(card: PlayerCardState, choices: PlayerChoices, actions: CardMenuActions)
  @Composable internal fun SpeedSection(speed: Float, onChosen: (Float) -> Unit)
  ```

- [ ] **Step 1: Write the failing test** — `CardMenuGatesTest.kt`:

```kotlin
package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.Framing
import player.PLAYBACK_SPEEDS
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.speedLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What each card menu offers and what choosing in it does. The subtitle
 * menu's language rows follow a regular track and its Style… row any track
 * that can show; a title with neither never opens it (the card disables ▾).
 * Choosing closes a menu — except the style panel, which is adjusted a step
 * at a time against the film.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardMenuGatesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val chosen = mutableListOf<String>()
    private var opened: CardMenu? = null
    private var done = 0

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val english = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false))

    private fun show(
        menu: CardMenu,
        choices: PlayerChoices,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CardMenuPanel(
                        menu = menu,
                        choices = choices,
                        actions =
                            CardMenuActions(
                                onSpeed = { chosen += "speed=$it" },
                                onAudio = { chosen += "audio=${it.text}" },
                                onSubtitle = { chosen += "subtitle=$it" },
                                onSize = { chosen += "size=$it" },
                                onBacking = { chosen += "backing=$it" },
                                onNudge = { chosen += "nudge=$it" },
                                onResetOffset = { chosen += "reset" },
                                onFraming = { chosen += "framing=${it.stored}" },
                            ),
                        onOpen = { opened = it },
                        onDone = { done++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aForcedOnlyTitleOffersStyleButNoLanguageRows() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleStyleVisible = true))

        compose.onNodeWithText("Style…").assertExists()
        compose.onNodeWithText("Subtitles").assertDoesNotExist()
    }

    @Test
    fun aTitleWithARegularTrackOffersItsLanguagesOffAndStyle() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("Off").assertExists()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Style…").assertExists()
    }

    @Test
    fun choosingALanguageChoosesItAndCloses() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("English").performClick()

        assertEquals(listOf("subtitle=en"), chosen)
        assertEquals(1, done)
    }

    @Test
    fun styleOpensTheStylePanelInTheSameMenu() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("Style…").performClick()

        assertEquals(CardMenu.SubtitleStyle, opened)
        assertEquals(0, done)
    }

    @Test
    fun theStylePanelStaysOpenWhileItIsAdjusted() {
        show(CardMenu.SubtitleStyle, PlayerChoices(subtitleStyleVisible = true))

        compose.onNodeWithText("Subtitle style").assertExists()
        compose.onNodeWithContentDescription("Subtitles later").performClick()

        assertEquals(listOf("nudge=1"), chosen)
        assertEquals(0, done)
    }

    @Test
    fun theSpeedMenuListsEverySpeedWithTheCurrentOneSelected() {
        show(CardMenu.Speed, PlayerChoices(speed = 1.25f))

        for (speed in PLAYBACK_SPEEDS) compose.onNodeWithText(speedLabel(speed)).assertExists()
        compose.onNodeWithText("1.25×").assertIsSelected()
        compose.onNodeWithText("1×").assertIsNotSelected()
    }

    @Test
    fun choosingASpeedChoosesItAndCloses() {
        show(CardMenu.Speed, PlayerChoices())

        compose.onNodeWithText("1.5×").performClick()

        assertEquals(listOf("speed=1.5"), chosen)
        assertEquals(1, done)
    }

    @Test
    fun theFramingMenuOffersFitFillAndTheTwoShapes() {
        show(CardMenu.Framing, PlayerChoices())

        for (framing in Framing.entries) compose.onNodeWithText(framing.label).assertExists()
        compose.onNodeWithText("Fill").performClick()

        assertEquals(listOf("framing=fill"), chosen)
        assertEquals(1, done)
    }
}
```

- [ ] **Step 2: Run it** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.CardMenuGatesTest'` — expected FAIL (unresolved `CardMenuPanel`, `CardMenuActions`).

- [ ] **Step 3: Minimal implementation**

`SpeedSection.kt` (the sheet's `SpeedRow`, moved unchanged; the sheet itself is deleted in Task 5):

```kotlin
package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import player.PLAYBACK_SPEEDS
import player.speedLabel

/**
 * The Speed menu's rows: every one of [PLAYBACK_SPEEDS], the current one
 * marked — the same radio-row shape [AudioSection] and [FramingSection] use.
 */
@Composable
internal fun SpeedSection(
    speed: Float,
    onChosen: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Speed", style = MaterialTheme.typography.titleMedium)
        for (option in PLAYBACK_SPEEDS) {
            SpeedRow(speed = option, selected = option == speed, onClick = { onChosen(option) })
        }
    }
}

@Composable
private fun SpeedRow(
    speed: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(speedLabel(speed), modifier = Modifier.padding(start = Spacing.small))
    }
}
```

`PlayerCardMenus.kt`:

```kotlin
package ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import playback.AudioOption
import playback.Framing
import player.PlayerChoices

/** Finds an open card menu in a test. */
internal const val CardMenuTag = "player-card-menu"

/** Wide enough for the longest audio label; narrower than a phone's card, so a menu always fits inside it. */
private val MENU_MAX_WIDTH = 320.dp

/** Past this a menu scrolls — a file can carry a dozen subtitle languages. */
private val MENU_MAX_HEIGHT = 360.dp

/** What choosing in a card menu does — one bundle, as [PlayerMarksActions] is for the marks. */
internal class CardMenuActions(
    val onSpeed: (Float) -> Unit,
    val onAudio: (AudioOption) -> Unit,
    val onSubtitle: (String) -> Unit,
    val onSize: (Int) -> Unit,
    val onBacking: (String) -> Unit,
    val onNudge: (Int) -> Unit,
    val onResetOffset: () -> Unit,
    val onFraming: (Framing) -> Unit,
)

/**
 * One card menu's rows on the card's own fill. The rows are the retired
 * settings sheet's sections, unchanged. Choosing a value closes the menu
 * ([onDone]); the subtitle style panel is the exception — a size, a backing
 * or a sync nudge is judged against the film a step at a time, so it stays
 * until Back. "Style…" moves this same menu to the style panel ([onOpen]).
 */
@Composable
internal fun CardMenuPanel(
    menu: CardMenu,
    choices: PlayerChoices,
    actions: CardMenuActions,
    onOpen: (CardMenu) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The sections draw in the content colour; on the card's dark fill that has to be white.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier =
                modifier
                    .widthIn(max = MENU_MAX_WIDTH)
                    .heightIn(max = MENU_MAX_HEIGHT)
                    .testTag(CardMenuTag)
                    .playerCard()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        ) {
            when (menu) {
                CardMenu.Speed -> SpeedSection(speed = choices.speed, onChosen = { actions.onSpeed(it); onDone() })
                CardMenu.Audio -> AudioSection(options = choices.audioOptions, onChosen = { actions.onAudio(it); onDone() })
                CardMenu.Framing -> FramingSection(framing = choices.framing, onChosen = { actions.onFraming(it); onDone() })
                CardMenu.Subtitles -> {
                    if (choices.subtitleOptions.isNotEmpty()) {
                        SubtitleSection(options = choices.subtitleOptions, onChosen = { actions.onSubtitle(it); onDone() })
                    }
                    if (choices.subtitleStyleVisible) StyleRow(onClick = { onOpen(CardMenu.SubtitleStyle) })
                }
                CardMenu.SubtitleStyle ->
                    SubtitleStyleSection(
                        sizePercent = choices.subtitleSizePercent,
                        onSizeChosen = actions.onSize,
                        backing = choices.subtitleBacking,
                        onBackingChosen = actions.onBacking,
                        offsetMs = choices.subtitleOffsetMs,
                        onNudge = actions.onNudge,
                        onResetOffset = actions.onResetOffset,
                    )
            }
        }
    }
}

/** The subtitle menu's way into the style panel, a full touch target like every other control over the picture. */
@Composable
private fun StyleRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Style…")
    }
}

/**
 * The open menu, drawn over the stage directly above the button that opened
 * it and inside the card's width ([cardMenuOffset]). Every bound is in root
 * coordinates, so the stage's own place in the root is taken off. Drawn
 * clear until it has measured itself, so it never shows for a frame at the
 * wrong height.
 */
@Composable
internal fun BoxScope.CardMenuOverStage(
    card: PlayerCardState,
    choices: PlayerChoices,
    actions: CardMenuActions,
) {
    val open = card.menu ?: return
    val anchor = card.anchorOf(open) ?: return
    val bounds = card.bounds ?: return
    var size by remember(open) { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { Spacing.small.roundToPx() }
    CardMenuPanel(
        menu = open,
        choices = choices,
        actions = actions,
        onOpen = card::switchTo,
        onDone = card::closeMenu,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { cardMenuOffset(anchor, bounds, size, gap) - card.origin }
                .onSizeChanged { size = it }
                .alpha(if (size == IntSize.Zero) 0f else 1f),
    )
}
```

- [ ] **Step 4: Run** — same command — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ui/player/PlayerCardMenus.kt android/ui-mobile/src/main/kotlin/ui/player/SpeedSection.kt android/ui-mobile/src/test/kotlin/ui/player/CardMenuGatesTest.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerSettingsSheetGatesTest.kt
git commit -m "feat(player): card menus for subtitles, speed, audio and framing"
```

---

### Task 4: Episode sidebar

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/player/EpisodeSidebar.kt`, `EpisodeSidebarRow.kt`
- Test: `android/ui-mobile/src/test/kotlin/ui/player/EpisodeSidebarTest.kt`

**Interfaces:**
- Consumes: `EpisodeList`, `EpisodeSection`, `EpisodeRow` (phase 03, `feature/player`); `Modifier.playerCard()` (phase 03); `catalog.humanDuration(seconds: Int?): String?` (`feature/catalog/src/main/kotlin/TitleFacts.kt:26`); `GlyphButton`, `MIN_TARGET` (Task 2).
- Produces:
  ```kotlin
  internal const val EpisodeSidebarTag = "episode-sidebar"
  internal const val NOW_PLAYING = "Now playing"
  @Composable internal fun EpisodeSidebar(list: EpisodeList, onPick: (setId: String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)
  @Composable internal fun EpisodeSidebarRow(row: EpisodeRow, onPick: (setId: String) -> Unit)
  ```

- [ ] **Step 1: Write the failing test** — `EpisodeSidebarTest.kt`:

```kotlin
package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import player.EpisodeList
import player.EpisodeRow
import player.EpisodeSection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The episode sidebar over an [EpisodeList]: it opens on the open title's
 * season, steps through the others, says which rows are watched, partly
 * watched and playing, and hands a picked row back.
 */
abstract class EpisodeSidebarCases {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    protected val picked = mutableListOf<String>()
    protected var closed = 0

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    protected fun show(list: EpisodeList) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpisodeSidebar(
                            list = list,
                            onPick = { picked += it },
                            onClose = { closed++ },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun row(
        id: String,
        number: String,
        title: String,
        watched: Boolean = false,
        progress: Float? = null,
        current: Boolean = false,
    ) = EpisodeRow(id, number, title, runtimeSecs = 1_380, watched = watched, progress = progress, current = current)

    protected val twoSeasons =
        EpisodeList(
            sections =
                listOf(
                    EpisodeSection("Season 1", listOf(row("a1", "S1E1", "Pilot", watched = true), row("a2", "S1E2", "Second", progress = 0.5f))),
                    EpisodeSection("Season 2", listOf(row("b1", "S2E1", "Return", current = true), row("b2", "S2E2", "Finale"))),
                ),
            currentSection = 1,
        )

    protected fun sidebarWidth(): Float = compose.onNodeWithTag(EpisodeSidebarTag).getBoundsInRoot().let { (it.right - it.left).value }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class EpisodeSidebarTest : EpisodeSidebarCases() {
    @Test
    fun itOpensOnTheSeasonOfTheOpenTitle() {
        show(twoSeasons)

        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithText("Return").assertExists()
        compose.onNodeWithText("Pilot", substring = true).assertDoesNotExist()
    }

    @Test
    fun theArrowsStepThroughTheSeasonsAndStopAtTheEnds() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Next season").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNodeWithText("Season 1").assertExists()
        compose.onNodeWithContentDescription("Previous season").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next season").assertIsEnabled()
    }

    @Test
    fun oneSeasonIsATitleWithoutArrows() {
        show(EpisodeList(listOf(twoSeasons.sections[1]), currentSection = 0))

        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithContentDescription("Previous season").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next season").assertDoesNotExist()
    }

    @Test
    fun theOpenTitleReadsNowPlayingAndIsNotActionable() {
        show(twoSeasons)

        compose.onNodeWithText(NOW_PLAYING).assertExists()
        compose.onNode(hasText("Return") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aRowSaysItsRuntime() {
        show(twoSeasons)

        compose.onNode(hasText("Finale") and hasText("23m")).assertExists()
    }

    @Test
    fun aWatchedRowIsTickedAndStillPlayable() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNode(hasText("✓ Pilot") and hasClickAction()).performClick()

        assertEquals(listOf("a1"), picked)
    }

    @Test
    fun aPartWatchedRowCarriesAProgressLine() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun closeAsksToClose() {
        show(twoSeasons)

        compose.onNodeWithContentDescription("Close episodes").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun besideThePictureItIs320dpWide() {
        show(twoSeasons)

        assertTrue(sidebarWidth() in 319.5f..320.5f, "sidebar is ${sidebarWidth()}dp")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class EpisodeSidebarNarrowTest : EpisodeSidebarCases() {
    @Test
    fun underSixHundredDpItTakesTheWholeWidth() {
        show(twoSeasons)

        assertTrue(sidebarWidth() in 359.5f..360.5f, "sidebar is ${sidebarWidth()}dp")
    }
}
```

- [ ] **Step 2: Run it** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.EpisodeSidebar*'` — expected FAIL (unresolved `EpisodeSidebar`, `EpisodeSidebarTag`, `NOW_PLAYING`).

- [ ] **Step 3: Minimal implementation**

`EpisodeSidebar.kt`:

```kotlin
package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import player.EpisodeList

/** Finds the sidebar in a test. */
internal const val EpisodeSidebarTag = "episode-sidebar"

/** Beside the picture on anything wide enough to leave the film in view. */
private val SIDEBAR_WIDTH = 320.dp

/** Under this a 320dp column would leave a sliver of film, so the list takes the window. */
private const val FULL_WIDTH_BELOW_DP = 600

/**
 * The run's titles, a season (or a course's section) at a time, standing
 * down the right of the picture while the film keeps playing — the web's
 * sidebar on the phone. It opens on the section holding the open title
 * ([EpisodeList.currentSection]); ‹ › step through the others and with one
 * section there is nothing to step to, so only its title shows. Kept on the
 * section a viewer stepped to while rows change under it — a title finished
 * elsewhere re-greys its row without moving the list.
 */
@Composable
internal fun EpisodeSidebar(
    list: EpisodeList,
    onPick: (setId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(list.currentSection) { mutableIntStateOf(list.currentSection) }
    val section = list.sections.getOrNull(shown) ?: list.sections.firstOrNull() ?: return
    val narrow = LocalConfiguration.current.screenWidthDp < FULL_WIDTH_BELOW_DP

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(SIDEBAR_WIDTH))
                    .testTag(EpisodeSidebarTag)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .playerCard(),
        ) {
            SectionHeader(
                title = section.title,
                arrows = list.sections.size > 1,
                onPrevious = if (shown > 0) { { shown -= 1 } } else null,
                onNext = if (shown < list.sections.lastIndex) { { shown += 1 } } else null,
                onClose = onClose,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(section.rows, key = { it.setId }) { row -> EpisodeSidebarRow(row = row, onPick = onPick) }
            }
        }
    }
}

/** `‹ Season N ›` and ✕. An arrow with nowhere to go is disabled rather than gone, so the title does not jump. */
@Composable
private fun SectionHeader(
    title: String,
    arrows: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        if (arrows) GlyphButton(glyph = "‹", description = "Previous season", enabled = onPrevious != null, onClick = { onPrevious?.invoke() })
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.small),
        )
        if (arrows) GlyphButton(glyph = "›", description = "Next season", enabled = onNext != null, onClick = { onNext?.invoke() })
        GlyphButton(glyph = "✕", description = "Close episodes", enabled = true, onClick = onClose)
    }
}
```

`EpisodeSidebarRow.kt`:

```kotlin
package ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import catalog.humanDuration
import designsystem.Spacing
import player.EpisodeRow

/** What the open title's row says in place of its runtime. */
internal const val NOW_PLAYING = "Now playing"

/** A watched row: still readable, plainly done. */
private const val WATCHED_ALPHA = 0.45f

/**
 * One title of the run: its number, its name and how long it runs. Watched
 * is drawn faint with a ✓ and is still playable; a title started and left
 * carries the catalogue's own progress line under it; the open title says
 * "Now playing" and has nothing to press.
 */
@Composable
internal fun EpisodeSidebarRow(
    row: EpisodeRow,
    onPick: (setId: String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (row.current) Modifier else Modifier.clickable { onPick(row.setId) })
                .heightIn(min = MIN_TARGET)
                .alpha(if (row.watched) WATCHED_ALPHA else 1f)
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.number.isNotEmpty()) Text(text = row.number, style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (row.watched) "✓ ${row.title}" else row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = if (row.current) NOW_PLAYING else humanDuration(row.runtimeSecs).orEmpty(), style = MaterialTheme.typography.labelMedium)
        }
        row.progress?.let { fraction ->
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.extraSmall))
        }
    }
}
```

- [ ] **Step 4: Run** — same command — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ui/player/EpisodeSidebar.kt android/ui-mobile/src/main/kotlin/ui/player/EpisodeSidebarRow.kt android/ui-mobile/src/test/kotlin/ui/player/EpisodeSidebarTest.kt
git commit -m "feat(player): episode sidebar for the phone player"
```

---

### Task 5: The screen wears the card; the sheet retires

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/player/PlayerCardBridge.kt`
- Modify: `PlayerScreen.kt` (whole file, 207 lines → ~180), `PlayerScreenParts.kt:38-75` (`PlayerTopChrome`), `PlayerTopBar.kt` (whole file), `PlayerMarks.kt:43` (drop the marks' own scrim), `PlayerScreenLifecycle.kt:25-38` (`ControlsAutoHide`); comment-only: `AudioSection.kt:19`, `FramingSection.kt:19-24`, `SubtitleSection.kt:19`, `SubtitleStyleSection.kt:22`, `PlayerGestures.kt:23,30,33`, `ImmersiveEffect.kt:31-39`, `PipActions.kt:127`, `UpNextCard.kt:34`
- Delete: `PlayerControls.kt`, `PlayerSettingsSheet.kt`, `PlayerSettingsSheetBridge.kt`
- Test: create `android/ui-mobile/src/test/kotlin/ui/player/PlayerCardScreenTest.kt`; modify `PlayerTestActivity.kt:32-51`, `PlayerLifecycleFixture.kt:40-42,84`

**Interfaces:**
- Consumes: `PlayerViewModel.episodes` (phase 03); `previous()`, `restart()`, `playFromRun(setId)` (phase 03, `PlayerViewModelRun.kt`); `playNext()` (`PlayerViewModel.kt:116`); `controlsShouldFade(isPlaying, isScrubbing, menuOrSidebarOpen)` (phase 03, `ControlsVisibility.kt`); `UNKNOWN_TITLE` (phase 03); the `PlayerViewModel.*` writes in `PlayerViewModelDelegates.kt:38-51`; `PipController`/`PipButtonState` (`PipController.kt:51,70`); `PlayerMarks`/`PlayerMarksActions` (`PlayerMarks.kt:34,65`); `NotesLayout` (`NotesLayout.kt:47`); `UpNextCard` (`UpNextCard.kt:48`); `VideoWithSubtitles` (`VideoWithSubtitles.kt:39`); Tasks 1-4.
- Produces:
  ```kotlin
  internal fun playerCardActions(viewModel: PlayerViewModel, card: PlayerCardState, onToggleStats: () -> Unit, onEnterPip: (() -> Unit)?): PlayerCardActions
  internal fun PlayerViewModel.cardMenuActions(): CardMenuActions
  @Composable internal fun ControlsAutoHide(controlsShown: Boolean, isPlaying: Boolean, scrubbing: Boolean, menuOrSidebarOpen: Boolean, onHide: () -> Unit)
  @Composable internal fun PlayerTopBar(title: String, showTitle: Boolean, onBack: () -> Unit, onNotes: (() -> Unit)? = null, modifier: Modifier = Modifier, marks: @Composable () -> Unit = {})
  @Composable internal fun PlayerTopChrome(openSet: MediaSet?, barShown: Boolean, statsShown: Boolean, isInPip: Boolean, player: Player?, totals: () -> PlaybackTotals, onBack: () -> Unit, modifier: Modifier = Modifier, held: Boolean = false, onNotes: (() -> Unit)? = null, marks: @Composable () -> Unit = {})
  // test-only: PlayerTestActivity.run: List<String>, PlayerTestActivity.switches: MutableList<String>; PlayerLifecycleFixture(watchState, catalog: CatalogRepository = mockk(relaxed = true))
  ```

- [ ] **Step 1: Write the failing test**

`PlayerTestActivity.kt` — pass the run and record switches (lines 32-40 and the companion at 49-51):

```kotlin
                        PlayerScreen(
                            "set-one",
                            run,
                            null,
                            handPicked = false,
                            onBack = { showingPlayer = false },
                            onSwitch = { id, _ -> switches += id },
                            viewModel = playerViewModel,
                        )
```

```kotlin
    companion object {
        internal lateinit var fixture: PlayerLifecycleFixture

        /** The run the title opens on: none — a film — unless a test gives it one. */
        internal var run: List<String> = emptyList()

        /** Every title a switch asked to move to, in order. */
        internal val switches = mutableListOf<String>()
    }
```

`PlayerLifecycleFixture.kt` — let a test hand in the catalogue (lines 40-42, and `mockk<CatalogRepository>(relaxed = true)` at :84 becomes `catalog`):

```kotlin
internal class PlayerLifecycleFixture(
    val watchState: WatchStateFixture = WatchStateFixture(),
    private val catalog: CatalogRepository = mockk(relaxed = true),
) : AutoCloseable {
```

`PlayerCardScreenTest.kt`:

```kotlin
package ui.player

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import player.UNKNOWN_TITLE
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The card over a playing title, as the screen wires it: Back closes what
 * the card opened before it leaves anything, the card stays up while a menu
 * or the sidebar is open and only then, a menu opens above its button, and
 * ⏮ ⏭ ☰ follow the run. A tablet's width, so the sidebar stands beside the
 * card rather than over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerCardScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: PlayerLifecycleFixture
    private lateinit var controller: ActivityController<PlayerTestActivity>

    private fun open(run: List<String> = emptyList()) {
        compose.runOnUiThread {
            // Nothing in the catalogue: a run's rows list as unknown titles, which is all these need.
            val catalog = mockk<CatalogRepository>(relaxed = true)
            coEvery { catalog.sets() } returns emptyList()
            fixture = PlayerLifecycleFixture(catalog = catalog)
            PlayerTestActivity.fixture = fixture
            PlayerTestActivity.run = run
            PlayerTestActivity.switches.clear()
            controller = Robolectric.buildActivity(PlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        assertEquals(PlayerUiState.Playing, controller.get().playerViewModel.state.value)
    }

    @After
    fun close() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
            PlayerTestActivity.run = emptyList()
        }
    }

    /** Back through the dispatcher, as the system's own Back gesture delivers it. */
    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun lingerPast() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
    }

    @Test
    fun backWithAMenuOpenClosesOnlyTheMenu() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()
        compose.onNodeWithTag(CardMenuTag).assertExists()

        back()

        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithText("Library").assertDoesNotExist()
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun backClosesTheMenuThenTheSidebar() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()
        compose.onNodeWithContentDescription("Speed").performClick()

        back()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(EpisodeSidebarTag).assertExists()

        back()
        compose.onNodeWithTag(EpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun aMenuOpensAboveItsButtonInsideTheCard() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        val menu = compose.onNodeWithTag(CardMenuTag).getBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Speed").getBoundsInRoot()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(menu.bottom <= button.top, "the menu sits above the button that opened it")
        assertTrue(menu.left >= card.left && menu.right <= card.right, "and inside the card's width")
    }

    @Test
    fun aTapOnThePictureClosesTheMenuNotTheCard() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        compose.onNodeWithContentDescription("Speed").performClick()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun anOpenMenuKeepsTheCardUpWhilePlaying() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithTag(CardMenuTag).assertExists()
    }

    @Test
    fun anOpenSidebarKeepsTheCardUpWhilePlaying() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithTag(EpisodeSidebarTag).assertExists()
    }

    @Test
    fun withNothingOpenTheCardTakesItselfAwayWhilePlaying() {
        open()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertDoesNotExist()
    }

    @Test
    fun aFilmHasNoStepsAndNoEpisodes() {
        open()

        compose.onNodeWithContentDescription("Restart").assertExists()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
    }

    @Test
    fun theFirstTitleOfARunOffersNextButNotPrevious() {
        open(run = listOf("set-one", "set-two"))

        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next").assertIsEnabled()
        compose.onNodeWithContentDescription("Episodes").assertExists()
    }

    @Test
    fun aRowPickedInTheSidebarSwitchesToItAndClosesTheSidebar() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        compose.onNode(hasText(UNKNOWN_TITLE) and hasClickAction()).performClick()

        assertEquals(listOf("set-two"), PlayerTestActivity.switches)
        compose.onNodeWithTag(EpisodeSidebarTag).assertDoesNotExist()
    }

    @Test
    fun theMarksRideInTheTopBarAboveTheCard() {
        open()

        val marks = compose.onNodeWithText("Add to list").getBoundsInRoot()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(marks.bottom < card.top)
    }
}
```

Note on `aTapOnThePictureClosesTheMenuNotTheCard`: a second press of the open menu's own button is `PlayerCardState.toggle` closing it — the tap-on-picture path itself is pinned by `PlayerCardStateTest.aTapOnThePictureClosesAnOpenMenuRatherThanTheCard`; a raw tap on the picture through Robolectric would race the double-tap timeout.

- [ ] **Step 2: Run it** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerCardScreenTest'` — expected FAIL (the screen still draws `PlayerControls`: no `PlayerCardTag`, no "Speed"/"Episodes", and `PlayerLifecycleFixture(catalog = …)` is new).

- [ ] **Step 3: Minimal implementation**

`PlayerScreenLifecycle.kt:25-38`:

```kotlin
/**
 * Takes the card and the top bar away after [CONTROLS_LINGER_MS] once
 * [controlsShouldFade] says they may — never while a menu or the episode
 * sidebar is open; the rule itself lives there, where it can be tested.
 */
@Composable
internal fun ControlsAutoHide(controlsShown: Boolean, isPlaying: Boolean, scrubbing: Boolean, menuOrSidebarOpen: Boolean, onHide: () -> Unit) {
    LaunchedEffect(controlsShown, isPlaying, scrubbing, menuOrSidebarOpen) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = isPlaying, isScrubbing = scrubbing, menuOrSidebarOpen = menuOrSidebarOpen)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        onHide()
    }
}
```

`PlayerCardBridge.kt`:

```kotlin
package ui.player

import player.PlayerViewModel
import player.chooseAudioTrack
import player.chooseFraming
import player.chooseSubtitleLanguage
import player.nudgeSubtitleOffset
import player.previous
import player.resetSubtitleOffset
import player.restart
import player.setSpeed
import player.setSubtitleBacking
import player.setSubtitleSize
import player.toggleSubtitles

/**
 * The card's and its menus' actions over [PlayerViewModel] and the card's
 * own [PlayerCardState] — split out of `PlayerScreen` to keep it under the
 * project's line guideline; the card and its menus stay plain functions of
 * what they are handed, with no `PlayerViewModel` of their own.
 */
internal fun playerCardActions(
    viewModel: PlayerViewModel,
    card: PlayerCardState,
    onToggleStats: () -> Unit,
    onEnterPip: (() -> Unit)?,
): PlayerCardActions =
    PlayerCardActions(
        onRestart = viewModel::restart,
        onPrevious = viewModel::previous,
        onNext = viewModel::playNext,
        onToggleSubtitles = viewModel::toggleSubtitles,
        onOpenMenu = card::toggle,
        onToggleStats = onToggleStats,
        onEpisodes = card::toggleSidebar,
        onEnterPip = onEnterPip,
        onAnchor = card::anchor,
    )

/** Every write a card menu makes — kept as the one place that names them, as the settings sheet's bridge was. */
internal fun PlayerViewModel.cardMenuActions(): CardMenuActions =
    CardMenuActions(
        onSpeed = this::setSpeed,
        onAudio = this::chooseAudioTrack,
        onSubtitle = this::chooseSubtitleLanguage,
        onSize = this::setSubtitleSize,
        onBacking = this::setSubtitleBacking,
        onNudge = this::nudgeSubtitleOffset,
        onResetOffset = this::resetSubtitleOffset,
        onFraming = this::chooseFraming,
    )
```

`PlayerTopBar.kt` — replace the whole file:

```kotlin
package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import designsystem.Spacing

/** The top gradient the web keeps behind its own bar: [SCRIM_ALPHA] black at the top edge, clear by the bottom of the bar. */
private val TOP_GRADIENT = Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent))

/**
 * The slim bar along the top: back, what is playing, and the controls the
 * web keeps in its own top bar — My List, Kids and Add to list ([marks]) and
 * Notes ([onNotes], when the open title has notes). Picture-in-picture rides
 * in the card instead.
 *
 * The arrow stays up regardless of the card's fade — Android's own way
 * back, not something the web has; everything else follows [showTitle], the
 * same fade the card is under. Beside the arrow, the title over a row of the
 * marks and Notes that wraps rather than squeezes on a narrow phone. Inset
 * by the status bar's band even while it is hidden, so the bar does not jump
 * on fullscreen.
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
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (showTitle) Modifier.background(TOP_GRADIENT) else Modifier)
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.small)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        if (showTitle) {
            Column(modifier = Modifier.weight(1f).padding(top = Spacing.medium, end = Spacing.small)) {
                if (title.isNotEmpty()) {
                    Text(text = title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), itemVerticalAlignment = Alignment.CenterVertically) {
                    marks()
                    if (onNotes != null) TextButton(onClick = onNotes) { Text(text = "Notes", color = Color.White) }
                }
            }
        }
    }
}
```

`PlayerMarks.kt:43` — the marks now sit on the bar's gradient, not a scrim box of their own:

```kotlin
    Row(modifier = modifier) {
```

(drop the then-unused `androidx.compose.foundation.background` and `androidx.compose.foundation.layout.padding` imports if nothing else in the file uses them; `designsystem.Spacing` likewise.)

`PlayerScreenParts.kt` — `PlayerTopChrome` (`:38-75`) loses `onEnterPip` and gains `marks`; its doc's "the picture-in-picture button beside it" becomes "the marks and Notes beside it":

```kotlin
@Composable
internal fun PlayerTopChrome(
    openSet: MediaSet?,
    barShown: Boolean,
    statsShown: Boolean,
    isInPip: Boolean,
    player: Player?,
    totals: () -> PlaybackTotals,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    onNotes: (() -> Unit)? = null,
    marks: @Composable () -> Unit = {},
) {
    if (isInPip) return
    Column(modifier = modifier) {
        PlayerTopBar(title = titleLine(openSet), showTitle = barShown, onBack = onBack, onNotes = onNotes, marks = marks)
        // Gated on the bar being shown as well as on the toggle, so the
        // statistics have no visibility rule of their own: a viewer who
        // leaves the numbers on gets the picture back when the card takes
        // itself away, and keeps them while the film is paused.
        if (statsShown && barShown) {
            player?.let { current ->
                PlaybackStatsOverlay(player = current, totals = totals, held = held, modifier = Modifier.padding(start = Spacing.medium))
            }
        }
    }
}
```

`PlayerScreen.kt` — replace the whole file:

```kotlin
// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.round
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.PlayerUiState
import player.PlayerViewModel
import player.UpNextPhase
import player.chooseFraming
import player.controlsMayShow
import player.createListAndAdd
import player.playFromRun
import player.retry
import player.setInList
import player.setKidsMark
import player.toggleWatchlist

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — never for a rotation or a
 * picture-in-picture resize, both declared in the manifest's own
 * `android:configChanges` so the activity is never recreated for either;
 * this composition simply reflows at the new size instead, and the
 * singleton player/ViewModel underneath were never touched either way.
 * See [shouldStopOnDispose].
 *
 * The controls are one card at the bottom of the picture
 * ([PlayerControlCard]) under a slim top bar ([PlayerTopChrome]). A tap
 * toggles both; they take themselves away while a film runs and stay while
 * it is paused, being scrubbed, or while a menu or the episode sidebar is
 * open — see [controlsShouldFade]. A tap on the picture with a menu open
 * closes the menu instead. Back closes the open menu, then the sidebar,
 * before the library's own Back is reached. Double-tap seeking, play/pause
 * and pinch framing live in [PlayerGestureLayer], which wraps everything
 * below. All of it is hidden while [LocalIsInPictureInPicture] is true; see
 * [PipController].
 */
@Composable
fun PlayerScreen(
    setId: String,
    run: List<String>,
    fsk: String?,
    handPicked: Boolean,
    onBack: () -> Unit,
    onSwitch: (setId: String, run: List<String>) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val openSet by viewModel.openSet.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val held by viewModel.held.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val isInPip = LocalIsInPictureInPicture.current
    val pip = PipController(player = player, isPlaying = state is PlayerUiState.Playing, onDismissed = viewModel::pauseForPipDismissal)

    PlayerNavigationEffects(viewModel, setId, run, fsk, handPicked, onSwitch)
    PlayerLifecycleEffects(
        viewModel,
        // The countdown drops `isPlaying` (the title has ended) and the gate
        // wait pauses on purpose; neither is a viewer looking away.
        isPlaying = state is PlayerUiState.Playing || upNext.phase != UpNextPhase.HIDDEN || upNext.awaitingStart,
    )

    // Shown when the screen opens, so a viewer finds out the card is there
    // at all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    // Saved, because a rotation destroys this composition and a viewer who
    // turned the phone to read a wider row did not ask for the numbers back.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    val card = remember { PlayerCardState() }
    ControlsAutoHide(controlsShown, isPlaying = state is PlayerUiState.Playing, scrubbing, menuOrSidebarOpen = card.somethingOpen, onHide = { controlsShown = false })
    // Composed after the library's own Back, so it answers first — and only
    // while the card has something open to close.
    BackHandler(enabled = card.somethingOpen, onBack = card::closeTopmost)
    // `!isInPip` folds in here: there is no touch surface of this app's own
    // inside that window to show a card on.
    val barShown = controlsShown && controlsMayShow(state) && !isInPip
    // The up-next card appearing is itself a reason to bring the card back —
    // a viewer who let it fade is exactly who most wants to see the panel.
    LaunchedEffect(upNext.phase) { if (upNext.phase != UpNextPhase.HIDDEN) controlsShown = true }
    // A title with no run left has nothing to list.
    LaunchedEffect(episodes == null) { if (episodes == null) card.sidebarOpen = false }
    val barTop = card.bounds?.top?.toFloat()?.takeIf { barShown }

    // Root-coordinate measurements the up-next card clamps to; see UpNextCard.
    var screenBottom by remember { mutableStateOf<Float?>(null) }
    var pictureBottom by remember { mutableStateOf<Float?>(null) }

    NotesLayout(notes, isInPip, onClose = viewModel::toggleNotes) {
        PlayerGestureLayer(
            player = player,
            onToggleControls = { if (!card.dismissMenu()) controlsShown = !controlsShown },
            onPinchFraming = viewModel::chooseFraming,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onGloballyPositioned {
                        screenBottom = it.boundsInRoot().bottom
                        card.origin = it.positionInRoot().round()
                    },
        ) {
            player?.let { current ->
                VideoWithSubtitles(current, subtitleCues, choices, barTop = barTop, isInPip = isInPip, onPictureBottomChanged = { pictureBottom = it })
                if (barShown) {
                    PlayerControlCard(
                        player = current,
                        view = PlayerCardView(choices, upNext, statsShown, hasEpisodes = episodes != null, catalogedDurationSecs = openSet?.durationSecs),
                        actions = playerCardActions(viewModel, card, onToggleStats = { statsShown = !statsShown }, onEnterPip = pip.enterPip.takeIf { pip.supported }),
                        onScrubbingChanged = { scrubbing = it },
                        onBounds = { card.bounds = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    CardMenuOverStage(card, choices, viewModel.cardMenuActions())
                }
                // The countdown that may run it keeps ticking either way — it
                // lives in the up-next controller, not in this composable.
                if (!isInPip) {
                    UpNextCard(
                        state = upNext,
                        onPlayNow = viewModel::playNext,
                        onCancel = viewModel::cancelUpNext,
                        barTop = barTop,
                        pictureBottom = pictureBottom,
                        screenBottom = screenBottom,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            // Over the picture, which keeps playing beside it.
            episodes?.takeIf { card.sidebarOpen && !isInPip }?.let { list ->
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

            when (state) {
                PlayerUiState.Preparing -> CenteredSpinner()
                is PlayerUiState.Failed -> PlayerFailure((state as PlayerUiState.Failed).message, onRetry = viewModel::retry)
                PlayerUiState.Playing, PlayerUiState.Paused -> Unit
            }

            PlayerTopChrome(
                openSet = openSet,
                barShown = barShown,
                statsShown = statsShown,
                isInPip = isInPip,
                player = player,
                totals = viewModel.totals,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopStart),
                held = held,
                onNotes = viewModel::toggleNotes.takeIf { notes != null },
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
            )
            ActionNoticeBar(actionNotice, viewModel::dismissActionNotice, Modifier.align(Alignment.BottomCenter))
        }
    }
}
```

Delete the retired files:

```bash
git rm android/ui-mobile/src/main/kotlin/ui/player/PlayerControls.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerSettingsSheet.kt android/ui-mobile/src/main/kotlin/ui/player/PlayerSettingsSheetBridge.kt
```

Comment sweep (wording only — no code changes):
- `AudioSection.kt:19` "The settings sheet's "Audio" section" → "The Audio menu's rows"; `FramingSection.kt:19-24` "The settings sheet's "Framing" section" → "The Framing menu's rows", "Always on the sheet, unlike Audio and Subtitles" → "Always offered, unlike Audio and Subtitles", "without opening the sheet at all" → "without opening the menu at all"; `SubtitleSection.kt:19` → "The subtitle menu's language rows"; `SubtitleStyleSection.kt:22` → "The subtitle style panel, reached from the subtitle menu's Style… row".
- `PlayerGestures.kt:23` "A tap toggles the transport bar" → "A tap toggles the card"; `:30` "the other two framings are the sheet's own rows" → "the other two framings are the Framing menu's"; `:33` "the transport bar, the settings sheet and the up-next card all consume" → "the card, its menus, the episode sidebar and the up-next card all consume".
- `ImmersiveEffect.kt:31-39` "the settings sheet is its own dialog window" → "the Add to list dialog is its own window"; "the sheet itself closing" → "the dialog itself closing"; "(the sheet opening)" → "(the dialog opening)"; "the sheet closed" → "the dialog closed".
- `PipActions.kt:127` "the transport bar's own buttons" → "the card's own buttons"; `UpNextCard.kt:34` "while the transport bar is" → "while the card is".

- [ ] **Step 4: Run** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest` — expected PASS (the new screen tests and the untouched suite, incl. `PlayerNoticesTest`, `PlayerKidsChoiceTest`, which still find "Add to list" and the Kids choice in the top bar). Then `cd android && ./gradlew -q :app:checkDebugDuplicateClasses` — expected PASS. Then `grep -rn "PlayerSettingsSheet\|PlayerControls(" android/ui-mobile/src` — expected no output.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ui/player/ android/ui-mobile/src/test/kotlin/ui/player/PlayerCardScreenTest.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerTestActivity.kt android/ui-mobile/src/test/kotlin/ui/player/PlayerLifecycleFixture.kt
git commit -m "feat(player): phone player wears the control card; retire the settings sheet"
```

---

### Task 6: Double tap seeks by the player's own skip

**Files:**
- Modify: `android/ui-mobile/src/test/kotlin/ui/player/PlayerGesturesTest.kt` (rewrite the header doc at `:6-11`; add two tests)

**Interfaces:**
- Consumes: `handleDoubleTap(player, tapX, width, previous): SeekFlash?` (`PlayerGestures.kt:80`), `SeekFlash` / `SeekZone` (`SeekRipple.kt:21,24`); `SKIP_MS = 15_000L` drives `seekBackIncrement` in production (phase 03, `PlayerFactory.kt`).
- Produces: nothing (tests only — the double tap already reads `player.seekBackIncrement`/`seekForwardIncrement`, so 15 s follows `SKIP_MS` with no code change here).

- [ ] **Step 1: Write the test** — replace the class doc and add, inside `PlayerGesturesTest` (with imports `androidx.media3.common.Player`, `io.mockk.every`, `io.mockk.mockk`, `io.mockk.verify`):

```kotlin
/**
 * Which third of the screen a double tap lands in, and what a double tap on
 * either outer third does: seek by the player's own increment — the same
 * fifteen seconds the card's −15 and +15 read off it — never a second number
 * kept here.
 */
class PlayerGesturesTest {
```

```kotlin
    @Test
    fun aDoubleTapOnTheLeftSeeksBackByThePlayersOwnSkip() {
        val player = mockk<Player>(relaxed = true) { every { seekBackIncrement } returns 15_000L }

        val flash = handleDoubleTap(player, tapX = 100f, width = width, previous = null)

        verify { player.seekBack() }
        assertEquals(SeekZone.BACK, flash?.zone)
        assertEquals(15L, flash?.totalSeconds)
    }

    @Test
    fun aDoubleTapOnTheRightSeeksForwardByThePlayersOwnSkip() {
        val player = mockk<Player>(relaxed = true) { every { seekForwardIncrement } returns 15_000L }

        val flash = handleDoubleTap(player, tapX = 1_100f, width = width, previous = null)

        verify { player.seekForward() }
        assertEquals(SeekZone.FORWARD, flash?.zone)
        assertEquals(15L, flash?.totalSeconds)
    }
```

- [ ] **Step 2: Run it** — `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerGesturesTest'` — expected PASS on first run: this pins existing behaviour the 15 s skip relies on (the old doc said no fake `Player` existed; mockk is already a test dependency). A FAIL means the double tap stopped reading the player's increment — stop and report.

- [ ] **Step 3: Minimal implementation** — none.

- [ ] **Step 4: Run** — same command — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/test/kotlin/ui/player/PlayerGesturesTest.kt
git commit -m "test(player): double tap seeks by the player's own fifteen seconds"
```

---

### Task 7: Record the card in the Android design system

**Files:**
- Modify: `DESIGN.md` (repo root) — `:437-440`, `:501-504`, insert before `:733` (`## Do's and Don'ts`), `:790-794`, `:808-811`. Grep the anchors first (`grep -n "No Floating Container Rule\|until the bar\|^## Do's and Don'ts\|have not\|palette is unresolved" DESIGN.md`): line numbers move as edits land, so apply bottom-up.

**Interfaces:**
- Consumes: the merged values from Tasks 2-5 (`CARD_FILL_ALPHA`, `CARD_MAX_WIDTH`, `CARD_MARGIN`, `SIDEBAR_WIDTH`, `MIN_TARGET`, `WATCHED_ALPHA`); TV values from the spec (760dp, 32dp, 360dp) — phase 06 re-checks them against phase 05's merge.
- Produces: documentation only.

- [ ] **Step 1: Write the check** — the doc's own acceptance, run before editing:

```bash
grep -c "### Player card" DESIGN.md                                   # expect 0 now, 1 after
grep -n "The player, the title detail screen" DESIGN.md                # expect a hit now, none after
grep -n "palette is unresolved on Android" DESIGN.md                   # expect a hit now, none after
```

- [ ] **Step 2: Run it** — expected: `0`, one hit, one hit (the "failing" state).

- [ ] **Step 3: Edit** (bottom-up):

`:808-811` — replace the bullet with:

```markdown
- **The player's palette is the catalogue's, plus one card.** Its controls
  sit on a single black-at-78% card (Player card, above) over the
  catalogue's own ground; whether playback wants a second, darker palette
  beyond that card is still open.
```

`:790-794` — replace "The player, the title detail screen and the collection screen have not been restyled at all; …" with:

```markdown
  The title detail screen and the collection screen have not been restyled
  at all; they inherit the palette and type through the theme but their own
  composition is undocumented and unreviewed — a sweep of what that leaves
  off-token is tracked outside this file (see the Settings/System redesign
  plan's own review). The player's controls are composed: see Player card.
```

Insert before `## Do's and Don'ts` (after `### Platform window`):

```markdown
### Player card

- **The card:** the player's controls in one card at the bottom of the
  picture, in three rows — where the film is (position, scrub bar, length ·
  ends HH:MM), how it plays (CC, ▾, speed, Audio, framing, and
  picture-in-picture on a phone), and what moves it (↺ ⏮ −15 ▶ +15 ⏭, then
  ⓘ and ☰). Black at 78%, a 1dp hairline at 8% white, radius 12dp
  (`Radius.card`), 16dp inside, no shadow. Phone: the window's width less
  12dp each side, never wider than 720dp, 12dp above the bottom edge.
  Television: 760dp, centred, 32dp above the bottom edge. A row wraps rather
  than shrink a control; every touch target is at least 48dp.
- **Menus and the episode sidebar** take the card's own fill. A menu opens
  directly above the button that opened it, inside the card's width, one at
  a time. The sidebar stands down the right — 320dp on a phone or tablet,
  the whole width under 600dp, 360dp on a television — watched rows at 45%
  with a ✓, the catalogue's progress line under a part-watched row, and
  "Now playing" for the open one.
- **Top bar:** back, the title, My List, Kids, Add to list and Notes, over a
  gradient from 55% black (`SCRIM_ALPHA`) at the top edge to clear.
- **Nothing lifts or grows.** A control keeps its size on press and on
  focus; the television marks focus with its own colours and border only.
- **Deliberate difference: no blur.** The web blurs what lies behind its
  card; Android fills it with a flat dark tint. The video draws on its own
  surface, which is what keeps HDR and Dolby Vision passthrough working —
  blurring it would need a TextureView, and that breaks both.
```

`:501-504` — after "…until the bar settles." append:

```markdown
  The player card makes the same choice for a different reason; see Player
  card.
```

`:437-440` — after "…which is the one thing this world does not do." append:

```markdown
The player card is the one floating container, and it floats over the
picture, not between art and page: it is the film's controls, not a band of
the catalogue (see Player card).
```

- [ ] **Step 4: Run** — the three greps again — expected: `1`, no hit, no hit.

- [ ] **Step 5: Commit**

```bash
git add DESIGN.md
git commit -m "docs: record the player control card in the Android design system"
```

---

## Todo

- [ ] Task 1: Card state holder
- [ ] Task 2: The card — seek, tools and transport rows
- [ ] Task 3: Card menus
- [ ] Task 4: Episode sidebar
- [ ] Task 5: The screen wears the card; the sheet retires
- [ ] Task 6: Double tap seeks by the player's own skip
- [ ] Task 7: Record the card in the Android design system

## Unresolved questions

- ~~A hand-built list holding the same title twice would crash the `LazyColumn` on equal keys.~~ Resolved in phase 03: `episodeListOf` keeps one row per distinct id, so `setId` keys stay unique.
- Menu rows are lifted to 48 dp in Task 3 (lead's ruling, Key insights); confirm on the tablet walk.
