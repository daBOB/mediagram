# Android motion parity — design spec

2026-10-10. Agreed in conversation in three sections. This file is the written form for review.
The source for every row below is `plans/reports/motion-parity-inventory-261009-report.md`, which maps each web motion to its CSS line and its Android call site.

## Goal

The phone and tablet move the way the web player moves: the same easings, durations and triggers, and nothing the web doesn't do. Today Android mostly snaps where the web animates, and animates (Material ripple, sliding tab indicator, scaling menus, spinners) where the web is still. Both are parity gaps (`CLAUDE.md` § Surface Parity).

## User decisions (2026-10-09, do not reverse silently)

| # | Question | Answer |
|---|---|---|
| 1 | What "UI micro-interactions" means | **Bring the web's motion to Android.** No new motion of our own. |
| 2 | Surfaces | **Phone and tablet now.** The TV adopts the same module inside TV parity batches 3 (cover and art motion) and 4 (focus motion). |
| 3 | Structure | **A: one shared module** in `core/designsystem`. |
| 4 | Cover turn | **Fade up from black, like the web.** Not Android's current cross-fade. |
| 5 | Player controls linger | **Keep 4 s on touch**, written down as a difference. The web's 2.6 s suits a mouse. |
| 6 | Phone quick-return header | **Keep it, write it down.** |

Ruled by parity during design (Surface Parity makes the web the reference, so no question was needed):
- no ripple anywhere, including dialog buttons;
- Material's dropdown menu and sliding tab indicator are replaced;
- the four spinners become the web's still loading text;
- `web/DESIGN.md:229` and `:292` are corrected to say reduced motion is `none`, as `theme.css:181` does.

## 1. The motion module (`core/designsystem`)

Foundation and animation types only, so ui-tv can use it later without seeing material3. Split across files to keep each under 200 lines: `Motion.kt` (tokens, `rememberReducedMotion`), `MotionModifiers.kt` (`entrance`, `drift`, `hoverMotion`), `CatalogueIndication.kt`.

```kotlin
object Motion {
    val Ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)      // theme.css --ease
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)   // theme.css --ease-out
    val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS `ease`: player HUD, reel fade
    val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)   // CSS `ease-out`: reel drift and copy rise
    const val HOVER_MS = 160                                 // theme.css --hover
    fun <T> hover(): TweenSpec<T> = tween(HOVER_MS, easing = Ease)
}

@Composable fun rememberReducedMotion(): Boolean

fun Modifier.entrance(durationMs: Int, delayMs: Int = 0, fromY: Dp = 0.dp, fromScale: Float = 1f,
                      fadeMs: Int = durationMs, easing: Easing = Motion.Ease): Modifier

fun Modifier.drift(from: Float, to: Float, durationMs: Int, easing: Easing, running: Boolean = true,
                   translate: Offset = Offset.Zero, restScale: Float = to): Modifier

fun Modifier.hoverMotion(source: InteractionSource, durationMs: Int, lift: Dp = 0.dp, nudge: Dp = 0.dp,
                         zoom: Float = 1f, nudgeOnPress: Boolean = nudge != 0.dp): Modifier

fun catalogueIndication(pressScale: Float = 0.98f): IndicationNodeFactory
```

- **`entrance`**: a one-shot fade, rise and/or scale from the given start to rest. It starts on first **placement**, not first composition, because LazyColumn prefetch composes items off screen. The delay is `tween(delayMillis)`, never `delay()`, so the system duration scale zeroes it too. Whether it has already played is the call site's saved flag, keyed to the item's stable id (the web's `.settled` rule): scrolling back, a catalog refresh or a recomposition never replays it.
- **`drift`**: a one-shot slow scale and pan. When `running` turns false it freezes in place and resumes from there. Under reduced motion it shows `restScale` and never ticks.
- **`hoverMotion`**: lift, nudge or zoom on `HoverInteraction` (mouse, trackpad, hovering stylus). Arrows (`nudgeOnPress`) also nudge on press, which is the touch form of hover. A finger never lifts or zooms artwork, as the web's `(hover: hover)` gate does.
- **`catalogueIndication`**: replaces ripple. An ink overlay of about 8% on press, hover and focus, fading with `Motion.hover()`; a 2 dp accent ring on focus with no animation (focus is only taken from a keyboard or D-pad, so this is `:focus-visible`); a snapped scale to `pressScale` while pressed. Link-type sites (see-all, genre tile, destination, person) pass `pressScale = 1f`.
- **`rememberReducedMotion`** replaces the two hand-rolled `ANIMATOR_DURATION_SCALE` reads (`HomeCover.kt`, `FeaturedReel.kt`). It reads once, not live; a comment says so, because the setting is rarely toggled.
- **Left out**: a crossfade wrapper, a player-controls helper and a selection-colour helper. Each has one user, which calls Compose directly with the tokens.
- **Wiring**: `MediagramTheme` provides `LocalIndication provides catalogueIndication()` and `LocalRippleConfiguration provides null`, inside `MaterialTheme`. Foundation clickables then need no change. M3 widgets hard-code ripple, so with ripple off they go silent; each gets `Modifier.indication(source, catalogueIndication())` on a shared interaction source. The player's three button wrappers (`PlayerControlParts.kt`) cover its controls in one place.

## 2. Adoption on phone and tablet (`ui-mobile`, `ui-common`)

Row numbers refer to the inventory's motion table.

**Everywhere**
- **Taps and clicks**: `catalogueIndication` replaces ripple on every clickable, M3 buttons and dialog buttons included (#17, #22).
- **Colour, border and background** of pills, the rail, header controls, genre pills, the mode toggle, search filters, swatches, cover dots and player tools fade over `Motion.hover()` instead of snapping (#22, #23). Where the web turns text to accent (player tools, rows, genre pills), the text colour fades the same way. The reel's dots stay a snap, as on the web.
- **⋯ menus**: one shared popup menu replaces Material's `DropdownMenu` at the four sites (`TitlePills.kt`, `chrome/OverflowMenu.kt`, `SeasonWall.kt`, `player/PlayerMarks.kt`). In the catalog it fades in and drops 4 dp over 320 ms (`Motion.Ease`), and closing snaps (#10). In the player (`PlayerMarks`) it opens and closes with no motion, because the web's player menus are plain `[hidden]` toggles. The menu keeps what `DropdownMenu` gave: dismissal on an outside tap and Back, focus, and TalkBack roles.
- **Tabs** (`TitleTabs.kt`): no sliding indicator (`indicator = {}`). Each tab draws its own underline, whose colour fades with `Motion.hover()` (#9).
- **Loading**: the four `CircularProgressIndicator`s (`MobileApp.kt`, `player/PlayerScreenParts.kt`, `profile/ProfilePickerScreen.kt`, `setup/LibraryScreen.kt`) become still text. The library load in `MobileApp.kt` copies the web's own: a cover-shaped dark block with "Loading your library…" near its foot (`web/public/index.html:98-99`, `shell.css:175-177`). The other three show plain still text in the same style, since the web has no counterpart that spins.
- **Dialogs** open and close without the platform window animation, as the web's `showModal()` does. One shared helper clears the dialog window's animations; every dialog calls it.

**Changing page** (#1): the new page rises 10 dp and fades in over 420 ms (`Motion.Ease`). This covers the frame switch (`LibraryFlowBranches.kt`) and the department tab switch (`chrome/LibraryHome.kt`). The old page is dropped at once, with no cross-fade. It doesn't play on a redraw of the same page, while a search is being refined, or for the player frame.

**Home**
- **Cover** (`home/HomeCover.kt`, `home/CoverSlide.kt`): the old slide is removed at once and the new one fades up from the black ground over 1400 ms (`Motion.Ease`) (#3). Each slide then drifts once from 1.06 to 1 over 14 s (`Motion.EaseOut`) (#4). The drift multiplies with Blurred's 1.12 overscan in `HeroArtwork`, it doesn't replace it. The existing hold rules, pause button, mid-fade tap shield and TalkBack hold stay (#5).
- **Sections** (`HomeScreen.kt` items other than the cover, `HomeFeatures`, department rows): each rises 28 dp over 1100 ms with a 900 ms fade as it scrolls into view, once per page visit (#2).
- **Continue strip** (`home/ResumeCard.kt` `ResumeRow`): snaps to a card's start (`rememberSnapFlingBehavior`, `SnapPosition.Start`) (#25). Other rows keep the default fling, as on the web.
- **Masthead blend** (`ui-common` `chrome/CoverBlend.kt`): under reduced motion the bar is solid, as the web's is (#14).

**Featured reel** (`FeaturedReel.kt`, `FeaturedSlide.kt`)
- The slides cross-fade over the existing duration on `Motion.CssEase` (#11).
- The poster drifts once per slide from 1 to 1.09, panning (−1.5%, −1%), over 8.5 s (`Motion.CssEaseOut`). It freezes in place while the reel is paused, and rests at 1 under reduced motion. The `InfiniteTransition` loop goes (#12).
- The copy rises 14 dp over 900 ms (`Motion.CssEaseOut`), staggered 0/150/300/450 ms (#13).

**Title and department pages**
- **Hero art** (`TitleSpread.kt`, `WideTitleSpread.kt`, `DepartmentHeroLayouts.kt`): fades in from scale 1.04 over 1400 ms (#6). The pull-quote art (`WideTitleSpread.kt`, `DeptHeroText.kt` `DeptQuote`) does the same 200 ms later (#7).
- **Tab content** (`TitleTabs.kt`): rises 8 dp over 520 ms on a tab change (#8).
- **Arrows** (#21): each glyph becomes its own `Text` so it can move on its own. "See all →" nudges +4 dp over 480 ms; "▶ Play" +2 dp over 520 ms; the back arrow −3 dp over 520 ms; the "All N →" pill moves +3 dp as a whole. `DeptRowHeading` gains the web's "→".

**Cards** (#18–#20): with a mouse or stylus hovering, posters lift 6 dp over 700 ms (`PosterArt`), feature, resume and genre or destination artwork zooms (1.045 over 1600 ms, 1.04 over 1200 ms, 1.05 over 1100 ms, inside a clipped card), and faces (`CastPanel.kt` `PersonCard`) lift 3 dp over 520 ms. Under a finger only the poster's name turns accent while pressed. No shadow: Android plates carry none, as `PosterArt.kt` already explains.

**Player** (#16): the top bar and the card fade over 260 ms (`Motion.CssEase`) in and out. Taps are blocked from the moment the fade-out starts, as the web's `pointer-events: none` does. Nothing else moves over the video.

**Stays still, as on the web**: the player's menus, the episode sidebar, Up next and its countdown, notices, progress bars and list changes (no `animateItem()`).

**Written differences** (kept, recorded in `docs/system-architecture.md` § Parity with the web player):
- **Player controls linger 4 s** (`CONTROLS_LINGER_MS`), against the web's 2.6 s. A finger has to reach the controls; a mouse is already there.
- **The phone's compact header is quick-return**: it hides on scroll down and returns on any scroll up. The web's phone masthead only reappears at the top. Kept because the phone's screen is short.
- **The cover holds still for TalkBack**, already written down in `HomeCover.kt`.

## 3. Reduced motion, speed, tests, delivery

**"Remove animations"**
- Compose already follows `ANIMATOR_DURATION_SCALE` (verified in ui-android 1.11.4: the recomposer's motion duration scale), so every tween, `Crossfade` and `AnimatedVisibility` snaps to its end. Every entrance's end is the web's resting state, so `entrance` and `hoverMotion` need no check of their own.
- An explicit check is needed only where the end isn't the rest, or where a timer runs on `delay()`: the reel drift rests at 1; the masthead blend goes solid; the cover rotation stops (it already does). The reel keeps rotating, on both surfaces.

**Speed**
- All motion is `graphicsLayer` alpha, translation and scale in lambdas (draw phase): no `offset`, no `animateContentSize`, no layout reads, no recomposition per frame.
- The cover drift stops while the cover item isn't visible or the lifecycle is below RESUMED.
- The player gets only the 260 ms fade; the slow byte path is untouched.
- **Risk: Blurred.** A `RenderEffect` blur under a scaling layer may re-blur every frame. Measure Home frame times (`dumpsys gfxinfo`) on the tablet with a release build, cover visible, Blurred on and off. If Blurred janks, the cover doesn't drift in Blurred, and that is written down. The same release build gives the frosted masthead its fair jank measurement.

**Tests**
- **The module**, as Robolectric Compose tests with the test clock paused:
  - each entrance ends at alpha 1, offset 0, scale 1;
  - a drift freezes when `running` turns false and resumes from where it stopped;
  - under reduced motion a drift shows `restScale`;
  - a press scales to 0.98 at once, and `pressScale = 1f` doesn't scale;
  - a press alone never lifts or zooms; a hover does; `nudgeOnPress` nudges on press.
- **Screens**:
  - the cover's new slide starts at alpha 0 over black, with the old slide gone;
  - the catalog menu opens with the entrance and closes without one;
  - the player menu opens with none;
  - player controls ignore taps once the fade-out starts.
- `scripts/check.sh` stays green.
- **A tablet walk**, with the profile avatar checked first (T, PIN 1357): Home, a title, a department and the player, with animations on and with "Remove animations" on. Always with `ANDROID_SERIAL=caad49da`.

**Delivery**
- **Branch**: `feat/motion` in a worktree at `../mediagram-motion`, off main.
  - It overlaps `feat/frosted-masthead` in `CoverBlend.kt` and `LibraryHome.kt`; whichever merges second rebases.
  - `feat/tv-parity` takes the module when its batches 3 and 4 begin.
- **Order**:
  1. the module and the press feedback;
  2. page turns and section reveals;
  3. the cover and the reel;
  4. title and department pages;
  5. cards and arrows;
  6. menus, tabs, loading and dialogs;
  7. the player.
- **Docs**:
  - the written differences in `docs/system-architecture.md`;
  - the `web/DESIGN.md` reduced-motion fix;
  - a changelog entry in `docs/project-changelog.md`.
- **Version**: the next minor above whatever main carries at merge, in all three manifests, bumped by pattern.

## Out of scope

- The TV (`ui-tv`), until its parity batches 3 and 4.
- The web player: it is the reference, and only its `DESIGN.md` wording changes.
- New motion with no web counterpart, and the web's hover-only effects that have no Android element (the search field widening, the plate shadow).
