# Motion parity inventory: web player → Android phone/tablet

2026-10-09 · read-only · the web is the reference (CLAUDE.md § Surface Parity). This is parity work, not new motion design.

Paths: web `web/public/styles/*.css` (shortened to `x.css`) and `web/public/lib/**` (`x.js`); Android `android/ui-mobile/src/main/kotlin/ui/**` (shortened to `ui/…`), `ui-common/…/ui/common/**`, `core/designsystem/src/main/kotlin/*`.

**Count.** The web defines 4 motion tokens and 25 distinct motions, drawn from 42 `transition:` and 22 `animation*:` declarations, 9 `@keyframes` and 5 JS drivers (page-turn, reveal, home-cover, featured-reel, player-hud). It also leaves 9 things still on purpose (§2b). Android today: 2 match (#5, #14), 1 has no counterpart to move (#15), 3 partly match (#3, #11, #25), 1 diverges (#12), and 18 snap, are missing, or fall back to a Material default.

---

## 1. Web motion tokens

| Token | Value | Source |
|---|---|---|
| `--ease` | `cubic-bezier(0.32, 0.72, 0, 1)`, "a spring that has lost its bounce" | theme.css:119-120, DESIGN.md:227 |
| `--ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` | theme.css:121, DESIGN.md:228 |
| `--hover` | `160ms var(--ease)`: colour, border or background only | theme.css:122, DESIGN.md:230 |
| Reduced motion | `animation: none !important; transition: none !important; scroll-behavior: auto` on every element. JS also checks it: reveal.js:15-18, home-cover.js:47,106-108 | theme.css:180-182 |

Off-token easings in use: the CSS keyword `ease` = `(0.25,0.1,0.25,1)` (HUD fade playback.css:41, reel fade :477), and the keyword `ease-out` = `(0,0,0.58,1)` (reel drift :499, reel copy rise :506).

The web doc disagrees with its own CSS. DESIGN.md:229 and :292 say reduced motion "collapses to 1ms", but the CSS removes animation outright (`none`). Android's duration scale 0 behaves like `none`. DESIGN.md should be corrected.

Rules (DESIGN.md:230, :290): controls never lift or grow on hover; artwork (plates, portraits) lifts; arrows nudge toward where they lead. `button:active` shrinks to 0.98 as a snap with no transition (theme.css:167). Tap highlight is turned off (theme.css:164). The plate lift only happens on `(hover: hover)` devices without reduced motion (catalog.css:51), so the web itself gives touch screens no lift.

---

## 2. Motion table

What Android does today: **Snaps**, **M3** (Material default), **Match**, **Partial**, **Missing** (no element to move).

### Entrances and state changes

| # | Web: element · trigger · property · timing · source | Android counterpart | Today | Proposed Compose |
|---|---|---|---|---|
| 1 | **Page turn**. Enter on navigation: opacity 0→1, translateY 10px→0, 420ms Ease. Not on a redraw of the same page, and not while search is being refined. shell.css:161-163, page-turn.js:33-45, app.js:628-634 | Frame switch `when (at.top)` ui/LibraryFlowBranches.kt:135. Department tab switch: `content()` ui/chrome/LibraryHome.kt:171 | Snaps | `key(frameKey) { Box(Modifier.entrance(420, fromY = 10.dp)) { … } }`. Exclude the PLAYER frame (the web `<dialog>` opens with no animation). No `AnimatedContent`: the web drops the old page at once, it does not cross-fade. |
| 2 | **Scroll reveal**. Enter when it scrolls into view, once per page visit: opacity 900ms, translateY 28px→0 over 1100ms, Ease. Shows at once on redraw or reduced motion. theme.css:206-209, reveal.js:26-46. Used on home sections (home-view.js:47,85,98), features (home-features.js:32), department rows (department-hero.js:46) | `HomeScreen` items ui/catalog/HomeScreen.kt:89-130 (not the cover), `HomeFeatures`, department rows | Missing | `Modifier.entrance(1100, fadeMs = 900, fromY = 28.dp)`, started at **first placement**, not first composition (LazyColumn prefetch composes off screen). A `rememberSaveable` played flag per item key, so scrolling back does not replay it. |
| 3 | **Cover turn**. The new slide fades in from 0 over 1400ms Ease. The old slide is removed at once, so the fade is up from the black `.cover` ground. Hold 9000ms. home.css:18,127, home-cover.js:20,56-63 | `Crossfade` ui/catalog/home/HomeCover.kt:164 (CROSSFADE_MS :46) | Partial: true cross-fade, default FastOutSlowIn | `tween(CROSSFADE_MS, easing = Motion.Ease)`. Black-dip vs cross-fade: see open question 1. |
| 4 | **Cover drift**. Per slide, once: scale 1.06→1 over 14s, Ease-out. home.css:27,128 | `HeroArtwork` in ui/catalog/home/CoverSlide.kt:73 | Missing | `Modifier.drift(1.06f, 1f, 14_000, Motion.EaseOut)` keyed per slide. Multiply it with Blurred's 1.12 (ui-common HeroArtwork.kt:94), don't replace it. Don't copy the web quirk where the animation's `transform` overrides the 1.12 overscan (appearance.css:22). |
| 5 | **Cover rotation rules**. Holds while a pointer or focus is on it, has a pause button, and never rotates under reduced motion. home-cover.js:42-80,106 | HomeCover.kt:43,72-94 | Match | Keep. The TalkBack hold is an Android-only addition and is already written down (HomeCover.kt:76-78). |
| 6 | **Hero art-in**. Enter: opacity 0, scale 1.04→1, 1400ms Ease. Title spread and department hero. Skipped on redraw. title-page.css:63,73,170; departments.css:17,94 | ui/catalog/TitleSpread.kt:87, WideTitleSpread.kt:90, DepartmentHeroLayouts.kt:64,156 | Snaps (Coil has no crossfade set) | `Modifier.entrance(1400, fromScale = 1.04f)` at these 4 call sites. Not on the cover, which has its own fade and drift (#3, #4). |
| 7 | **Pull-quote art-in**. Same as #6 with a 200ms delay. title-page.css:115, departments.css:45 | WideTitleSpread.kt:138-143, `DeptQuote` DeptHeroText.kt:125 | Snaps | `entrance(1400, delayMs = 200, fromScale = 1.04f)`. Put the delay in `tween(delayMillis)`, not `delay()`, so the duration scale also zeroes it. |
| 8 | **Tab panel**. Enter on tab change: opacity, translateY 8px, 520ms Ease. title-page.css:133-134 | `content(shown)` ui/catalog/TitleTabs.kt:83 | Snaps | `key(shown) { Box(Modifier.entrance(520, fromY = 8.dp)) { content(shown) } }` |
| 9 | **Tab underline**. On select: an inset box-shadow per tab fades over 160ms, colour 160ms. No sliding. title-page.css:122-132 | `ScrollableTabRow` + `Tab` TitleTabs.kt:51-63 | M3: the indicator slides between tabs | `indicator = {}`. Draw the underline under each tab with `drawBehind`, coloured by `animateColorAsState(if (selected) ink else Transparent, Motion.hover())`. |
| 10 | **⋯ menu**. Enter: opacity, translateY −4px, 320ms Ease. Exit snaps. title-page.css:46,50 | `DropdownMenu` TitlePills.kt:80. Also chrome/OverflowMenu.kt:60,107, SeasonWall.kt:87, player/PlayerMarks.kt:83 | M3: scale and fade, in and out | Either a `Popup` + `entrance(320, fromY = (-4).dp)` with no exit, or write the difference down (open question 5). |
| 11 | **Featured reel turn**. Cross-fade 1200ms CSS `ease`, hold 7000ms. playback.css:476-478, featured-reel.js:19-21,127-134 | `Crossfade` ui/catalog/FeaturedReel.kt:86 | Partial (easing) | `tween(FADE_MS, easing = Motion.CssEase)` |
| 12 | **Featured poster drift**. Once per slide: scale 1→1.09 and translate (−1.5%, −1%), 8.5s CSS `ease-out`. Pauses **in place** while the reel is paused. playback.css:499-503,585 | `DriftingPoster` ui/catalog/FeaturedSlide.kt:100-117 | Diverges: infinite 1↔1.05 reverse over 7s, snaps back to 1 when paused | `Modifier.drift(1f, 1.09f, 8_500, Motion.CssEaseOut, running = !paused, translate = …)`. Drop the `InfiniteTransition`. |
| 13 | **Featured copy rise**. Enter: opacity, translateY 14px, 900ms CSS `ease-out`, staggered 0/150/300/450ms. Shown at once under reduced motion. playback.css:505-510,623-625 | `FeaturedCopy` FeaturedSlide.kt:123-140 | Missing | `entrance(900, delayMs = 150 * min(i, 3), fromY = 14.dp, easing = Motion.CssEaseOut)` on each child |
| 14 | **Masthead settle**. Scroll-linked over the cover, glass→paper between 40vh and 75vh. Desktop only, off under reduced motion. shell.css:213-256 | `coverBlend` ui-common chrome/CoverBlend.kt; LibraryHome.kt:121-125 (EXPANDED only) | Match (the range curve differs slightly) | Keep. Add `if (reducedMotion) 1f` to match the web's opt-out. |
| 15 | **Search widens** on focus, 520ms Ease, desktop only (full width on phone, shell.css:205). shell.css:118-127 | Search opens `SearchScreen` (CompactLibraryHeader.kt:143) | n/a | None |
| 16 | **Player HUD**. The top bar and card fade, opacity 260ms CSS `ease`. Rest timer 2600ms. Pointer input is off the moment it starts resting. playback.css:41,63-70, player-hud.js:9,34-36 | `if (cardShown)` ui/player/PlayerScreen.kt:144. Top bar PlayerScreenParts.kt:63-75 / PlayerTopBar.kt:64-72. Linger 4000ms in feature/player ControlsVisibility.kt:13 | Snaps; timer differs | `AnimatedVisibility(cardShown, enter = fadeIn(tween(260, easing = CssEase)), exit = fadeOut(tween(260, easing = CssEase)))`. Block taps during the exit (the web's `pointer-events: none`). Timer: open question 2. |
| 17 | **Press**: a `button` shrinks to 0.98 as a snap (plates, resume cards, pills, tools, tabs, dots; links do not). theme.css:167 | Every clickable | M3 ripple | In `catalogueIndication`: `scale(0.98f)` while pressed, no tween. Link-type sites (see-all, genre tile, destination, person) pass `pressScale = 1f`. |

### Pointer and focus (hover)

| # | Web | Android counterpart | Today | Proposed Compose |
|---|---|---|---|---|
| 18 | **Plate lift**. Hover: the art moves up 6px and its shadow deepens over 700ms Ease. Name turns accent over 160ms. catalog.css:44,48-51 | `PosterCard` ui/catalog/PosterCard.kt:51-63, `PosterArt` PosterArt.kt:67 | Ripple only | `PosterArt(Modifier.hoverMotion(src, lift = (-6).dp, ms = 700))`. Name colour via `animateColorAsState(if (hovered or pressed) accent else onSurface, Motion.hover())`. No shadow: Android plates carry none, and PosterArt.kt:34-36 already says why. |
| 19 | **Artwork zoom**. Hover: feature scale 1.045 over 1600ms (home.css:157,160); resume 1.04 over 1200ms (:245-246); genre tile and destination 1.05 over 1100ms (catalog.css:141,145; departments.css:99,103). All Ease | HomeFeatures.kt:149-152, home/ResumeCard.kt:89-92, ArtTile.kt:80-84 | Ripple only | `hoverMotion(src, zoom = 1.045f, ms = 1600)` and so on, on the inner image with the card clipped |
| 20 | **Portrait lift**. Hover: up 3px, 520ms Ease. title-page.css:178,180 | `PersonCard` CastPanel.kt:110-117 | Ripple only | `hoverMotion(src, lift = (-3).dp, ms = 520)` on the face |
| 21 | **Arrow nudges**. See-all → moves +4px over 480ms (home.css:208-210); the play ▶ moves +2px over 520ms (title-page.css:26-27); `← Back` moves −3px over 520ms (title-page.css:84,86); the `All N →` pill moves +3px over 520ms (departments.css:81,83) | "See all →" home/ContinueBand.kt:102; DeptRowHeading.kt:31-37 (no arrow at all); `PagePill` with "N →" MoviesDepartmentScreen.kt:112 and DocumentariesDepartmentScreen.kt:136; "▶ Play" TitlePills.kt:60-62; back arrow chrome/AppChrome.kt:116-124 | Missing (each arrow is set inside the label text) | Split the glyph into its own `Text`, then `hoverMotion(src, nudge = 4.dp, ms = 480)`. Move the whole `PagePill` +3dp and the back arrow −3dp. DeptRowHeading gains "→" to match web `see-all`. |
| 22 | **Colour, border and background**, `--hover` 160ms. Pills (title-page.css:19-24; home.css:73-76), rows (catalog.css:76-78), rail and departments (shell.css:50,54), genre pills (catalog.css:96-97), mode toggle (:15-16), search filters (:151-152), pin control (:128-130), avatar ring (shell.css:149,152), cover dots and pause (home.css:123-126,338-340), department links (departments.css:50-51,64-66), player tools and menu items at accent plus 8% white (player-card.css:79-82,116-118), transport (playback.css:197-200), ghost (:97-99), reel buttons (:538-543), sidebar rows (episode-sidebar.css:37-38) | `Pill` and `CircleIconButton` chrome/ChromeControls.kt:49-62,111-125; LibraryRail.kt:174-185; CompactLibraryHeader.kt:87,105,114,143; SearchGroupsView.kt:121-139; ShelfModeToggle.kt:24-27; GenreLinks.kt:30; PagePill.kt; home/CoverControls.kt:31-125; player GlyphButton/TransportButton/LabelButton player/PlayerControlParts.kt:41-109 | M3 ripple everywhere | `catalogueIndication()` draws an ink overlay at about 8% (the web's 8–10% hover fills) on press, hover and focus, fading in and out with `Motion.hover()`. Where the web turns text to accent (tools, rows, genre pills), add `animateColorAsState(…, Motion.hover())` per site, most visible first: player tools, then pills. |
| 23 | **Selected state** animates on the same 160ms: active tab (#9), pressed search filter (catalog.css:151-153), swatch and accent ring (appearance.css:45-47,57-58), `aria-expanded` tool (player-card.css:81-82), cover dot `.on` (home.css:123-125). The reel dots **snap** (playback.css:575-582) | ChromeControls.kt:124, LibraryRail.kt:184, SearchGroupsView.kt:129-135, `SwatchCard` settings/SettingsControls.kt:144-153, CoverControls.kt:114-122 | Snaps | `animateColorAsState(target, Motion.hover())` for the background and border. Reel dots stay a snap. |
| 24 | **Focus ring**: `:focus-visible` 2px accent outline, offset 4px, snap. Focus never lifts or zooms; the web only does that on `:hover`. theme.css:170 | No ring on foundation clickables; the M3 ripple focus layer is faint | Missing | `catalogueIndication` draws a 2dp accent ring on `FocusInteraction` with no animation. In touch mode clickables never take focus, so this is exactly `:focus-visible`. |
| 25 | **Shelf scrolling**. Native `overflow-x`; the Continue strip snaps to start (`scroll-snap-type: x mandatory`). No arrow buttons, no JS scrolling. home.css:216-232 | `ResumeRow` LazyRow home/ResumeCard.kt:60-70 | Partial (no snap) | `flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start)` on ResumeRow only. Other rows keep default fling, as on the web. |

### 2b. Deliberately still on the web (Android must not add motion)

| Web | Android | Status |
|---|---|---|
| Player menus `[hidden]` (player-card.css:89-119), episode sidebar (episode-sidebar.css:22; `scrollIntoView` is instant, episode-sidebar.js:89), stats overlay | CardMenuOverStage player/PlayerCardMenus.kt:144-169, EpisodeSidebar.kt:61-99, PlayerScreenParts.kt:70 | Match (snap) |
| Up next appears without animation; the countdown is ticking text (playback.css:314-351, player-next-title.js:150-160) | UpNextCard.kt:58,78 | Match |
| Notices are plain text (player.js:404-420) | Snackbar with no host, PlayerActionNotice.kt:19 | Match |
| Progress bars are static (home.css:248-259, stats.css:33-36) | Determinate `LinearProgressIndicator`, PosterArt.kt:108 | Match |
| Loading: a static cover-shaped block with text (shell.css:172-177) | Indeterminate `CircularProgressIndicator`, MobileApp.kt:175 and PlayerScreenParts.kt:34 | **Diverges** (open question 6) |
| Redraw or sync: no replay (redraw.js, `.settled` in catalog.css:136) | Recomposition keeps state | Match if every new `entrance` is keyed to stable ids |
| List changes do not animate | Nothing | Match. **Do not** add `Modifier.animateItem()`: that would be new motion. |
| The player `<dialog>` opens without animation | PLAYER frame | Match. Keep it out of the page turn (#1). |

---

## 3. Touch adaptation (the hover rows)

Mapping: `(hover: hover)` corresponds to Compose `HoverInteraction`, which a tablet mouse, trackpad or hovering stylus produces (`clickable` is hoverable). `:focus-visible` corresponds to `FocusInteraction` from a keyboard or D-pad. A finger produces only `PressInteraction`.

| Row | Finger (phone or tablet) | Mouse or stylus hover (tablet) | Keyboard / D-pad focus |
|---|---|---|---|
| 18 Plate lift | **None**. The web gates it the same way (catalog.css:51). Name turns accent while pressed. | Lift −6dp | Ring only (#24) |
| 19 Artwork zoom | **None**. A 1.1–1.6s zoom would still be running after the tap had already navigated. | Zoom | Ring only |
| 20 Portrait lift | None | Lift −3dp | Ring only |
| 21 Arrow nudge | Nudge **while pressed**: it is a control-level cue, and press is the touch form of hover | Nudge | Ring only (the web nudges on `:hover` only) |
| 22 Colour/border/bg | **Pressed tint** via the indication, plus the per-site accent colour while pressed | Same tint | Tint and ring |
| 17 Press 0.98 | Yes, snap | n/a | n/a |

The web's rules carry over unchanged: controls never lift or grow (the press shrink is the only transform a control gets); artwork lifts or zooms on hover only; arrows nudge toward their destination.

---

## 4. Ripple and indication

- **The web does not allow it.** It sets `-webkit-tap-highlight-color: transparent` (theme.css:164), which turns off the browser's own tap feedback, and DESIGN.md:230 makes hover feedback a change of colour, border or background. A Material ripple is a platform default nobody chose, the same as the violet baseline that Theme.kt:27-30 already removes.
- **What ui-mobile does today.** `MediagramTheme` never overrides `LocalIndication` (Theme.kt:178-185), so all ~60 foundation `.clickable` and `.selectable` sites ripple. M3 widgets hard-code `ripple()`, which I verified in the material3 1.4.0 bytecode (`SurfaceKt$Surface$*` calls `RippleKt.ripple`). The M3 widgets in use: 52 `TextButton`, 15 `DropdownMenuItem`, 6 `IconButton`, 5 `Button`, 4 `OutlinedButton`, 5 `RadioButton`, a `Tab`, a `Switch`, a `Slider` and a `Checkbox`. The only opt-out is HomeCover.kt:185, the tap shield during a fade.
- **Proposal.** Inside `MaterialTheme`, provide `LocalIndication provides catalogueIndication()` and `LocalRippleConfiguration provides null`. Foundation clickables then get the web feedback with no further change. M3 widgets go silent, so they need `Modifier.indication(src, catalogueIndication())` with a shared `interactionSource`. The player's three wrappers (PlayerControlParts.kt:41-109) cover its most-used buttons in one place. Dialog `TextButton`s are the remaining tail (open question 3).
- **Constraint.** `core/designsystem` keeps material3 as `implementation` so that ui-tv never sees it (core/designsystem/build.gradle.kts:11-14). `catalogueIndication` is therefore a foundation `IndicationNodeFactory`, and the M3-specific `LocalRippleConfiguration` line goes in `MediagramTheme`, which is already M3.

---

## 5. Reduced motion

- **Compose already respects "Remove animations".** I verified this in the ui-android 1.11.4 bytecode, which is what the 2026.06.01 BOM resolves to: `WindowRecomposer_androidKt.getAnimationScaleFlowFor` observes `ANIMATOR_DURATION_SCALE` through a ContentObserver and feeds `MotionDurationScaleImpl` into the recomposer context. Every `tween`, `spring`, `Crossfade`, `AnimatedContent`, `AnimatedVisibility` and `Animatable` therefore snaps live at scale 0, and 0.5× and 2× are honoured too. `entrance` and `hoverMotion` need no check of their own, because their end state is the web's resting state.
- **Infinite transitions jump to their target.** Verified in animation-core 1.11.4 (`InfiniteTransition$run$1$1`): at scale 0 it calls `skipToEnd()` on every child, then suspends with `snapshotFlow{…}.first` until the scale is above 0. That means no frame ticking (good for battery), but the image sits at the **end** value. The current FeaturedSlide drift is already guarded by `drifting = !still` (FeaturedSlide.kt:113-116).
- **Where an explicit check is needed:**
  1. `drift` when the end value is not the resting look. The reel's 1→1.09 must rest at 1, as the web's `animation: none` leaves it. The cover's 1.06→1 already rests at 1.
  2. Timers driven by `delay()`, which the duration scale does not touch. Cover rotation already stops (HomeCover.kt:72-75,88), matching home-cover.js:47. The reel keeps rotating on both surfaces (featured-reel.js:6-8, FeaturedReel.kt:78-82), and that matches too.
  3. The scroll-linked bar blend (#14).
- **DRY.** The two hand-rolled `Settings.Global.getFloat(ANIMATOR_DURATION_SCALE)` reads (HomeCover.kt:72-75, FeaturedReel.kt:157-161) become one `rememberReducedMotion()` in designsystem. Both read once, not live; that is acceptable for a rarely toggled setting, so say so in a comment.

---

## 6. Shared module: `core/designsystem/Motion.kt`

Tokens plus 4 helpers, nothing else. It uses only foundation and animation types, so ui-tv can adopt it later. Today ui-tv uses default springs: `animateDpAsState` in TvLibraryRail.kt:90 and `animateFloatAsState` in TvSearchRows.kt:134.

```kotlin
object Motion {
    val Ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)      // theme.css:120
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)   // theme.css:121
    val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // `ease`: HUD, reel fade
    val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)   // `ease-out`: reel drift and rise
    const val HOVER_MS = 160                                 // theme.css:122
    fun <T> hover(): TweenSpec<T> = tween(HOVER_MS, easing = Ease)
}
@Composable fun rememberReducedMotion(): Boolean            // replaces the two copies

/** One-shot enter from (alpha 0, fromY, fromScale) to rest. A Modifier.Node, started on first placement, graphicsLayer only. Covers #1 #2 #6 #7 #8 #10 #13. */
fun Modifier.entrance(durationMs: Int, delayMs: Int = 0, fromY: Dp = 0.dp, fromScale: Float = 1f, fadeMs: Int = durationMs, easing: Easing = Motion.Ease): Modifier

/** One-shot slow scale and translate. Pauses in place when !running. Shows `restScale` under reduced motion. Covers #4 #12. */
fun Modifier.drift(from: Float, to: Float, durationMs: Int, easing: Easing, running: Boolean = true, translate: Offset = Offset.Zero, restScale: Float = to): Modifier

/** Hover-driven lift, nudge or zoom via graphicsLayer. Hover always; press only when nudgeOnPress (arrows). Covers #18-#21. */
fun Modifier.hoverMotion(source: InteractionSource, durationMs: Int, lift: Dp = 0.dp, nudge: Dp = 0.dp, zoom: Float = 1f, nudgeOnPress: Boolean = nudge != 0.dp): Modifier

/** Replaces ripple: an ink overlay of about 8% on press, hover and focus (Motion.hover()); a 2dp accent ring on focus; a snapped 0.98 scale on press. Covers #17 #22 #24. */
fun catalogueIndication(pressScale: Float = 0.98f): IndicationNodeFactory
```

Left out, because one call site each covers them: a crossfade wrapper (call `Crossfade` with `tween(…, Motion.Ease)` directly), a HUD helper (`AnimatedVisibility` inline), and selection colour (`animateColorAsState(…, Motion.hover())` inline).

---

## 7. Gaps, conflicts, risks

**Android animates where the web does not, and none of these differences is written down:**
1. Material ripple on every control (§4).
2. M3 `DropdownMenu` scales and fades in and out (#10); the web's menu only fades in and drops 4px, and its exit snaps.
3. M3 tab indicator slides (#9).
4. Indeterminate spinners where the web shows still text (§2b).
5. On a phone the compact header is **quick-return**: it hides on scroll down and comes back on any scroll up (LibraryHome.kt:131-139,187). The web's phone masthead is `position: static` (shell.css:205) and only reappears at the top.
6. The reel's infinite reverse drift (#12).
7. Platform `Dialog` window enter animations, for example FeaturedReel.kt's `Dialog`. The web's `showModal()` has none.
8. The HUD lingers 4000ms against the web's 2600ms (#16).
9. Cover: Android cross-fades both slides; the web fades up from black (#3).

**The web animates where Android cannot or should not:**
- Hover-only motion on a finger (§3).
- The search field widening (#15): there is no field to widen.
- The plate's deeper shadow: Android plates carry no shadow, already documented.
- The masthead settle on a phone: the web does not do it on a phone either.

**Risks:**
- **The slow byte path is not touched.** All artwork is local `File`s through Coil. The only motion over playback is the 260ms HUD fade, an alpha change in `graphicsLayer` with no recomposition. Add no continuous animation to the player screen.
- **Continuous frames on Home.** The cover drift (14s) is longer than its hold (9s), so Home redraws every frame while the cover is visible, as the web does. Mitigations: `graphicsLayer {}` lambdas only (draw phase); stop when the cover item is not visible or the lifecycle is below RESUMED. Under Blurred (API 31+), a `RenderEffect` blur under a scaling layer may re-blur every frame. Measure on the tablet and the TV box; if it janks, don't drift in Blurred.
- **Scroll jank.** Entrances must be `graphicsLayer` alpha, translation and scale only: no `offset`, no `animateContentSize`, no layout-phase reads. Start at placement so LazyColumn prefetch does not burn the animation off screen. Key `rememberSaveable` to item keys so a catalog refresh, or scrolling back, does not replay entrances (the web's `.settled` rule).
- **Battery.** Replacing the reel's `InfiniteTransition`, which ticks even while paused because it runs regardless of reads, with a one-shot `drift` means fewer frames. Under scale 0 nothing ticks either way.
- **Taps during the fade.** `AnimatedVisibility` keeps the card clickable during its exit; block input as soon as the exit starts (#16). The cover's existing mid-fade tap shield (HomeCover.kt:96-111) is the precedent.

---

## Open questions

1. Cover turn: keep Android's (and the TV's) true cross-fade and write the difference down, or match the web's fade-up from black?
2. HUD linger: change 4000ms to the web's 2600ms, or write down a touch-specific reason to keep 4000?
3. Ripple on M3 widgets: remove it everywhere (about 90 sites, which needs `Modifier.indication`; the player's three wrappers cover the most visible ones), or keep ripple on dialogs' M3 buttons as a written difference?
4. Phone quick-return header: keep it and write it down, or make it static like the web's phone masthead?
5. M3 `DropdownMenu` and the tab indicator: replace them with a Popup + `entrance` and a per-tab underline, or write them down as Material defaults?
6. Spinners against the web's still loading text: match or write down?
7. Web DESIGN.md:229 and :292 say "1ms" but theme.css:181 says `none`. Correct the doc?
