# Code review: department hero in the web's look + app-wide page ground (0.71.1)

Worktree `mediagram-home`, branch `feat/android-home-web-parity`, uncommitted diff on top of `475c812b`.
Read-only review. Probes ran in a scratch copy (`scratchpad/cr-dept-hero-review-0337`), never in the worktree.

## Scope
- Files: `DepartmentHero.kt` (226), `DeptHeroText.kt` (116), `DeptRowHeading.kt`, `DepartmentScrollStates.kt`, `HeroArtOf.kt`, `PageGround.kt`, `CoverBlend.kt`, `LibraryHome.kt`, `LibraryFlowBranches.kt` (338), `CatalogScreen.kt`, `MobileApp.kt`, `AppChrome.kt`, three department screens, `PullQuote.kt` (deleted), tests, changelog, and the plan files.
- Web reference: `department-hero.js`, `department-pages.js`, `collections-page.js`, `departments.css`, `appearance.css`, `theme.css`, `title-page.css`.
- Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug` is **green**. I also forced a rerun of `:ui-mobile:testDebugUnitTest --rerun`, which is green too.

## Overall
The port itself is careful. These all match the web:
- The scrim's three layers, in the right order, with the right stops.
- The hero heights (the clamp floor is tested).
- Kicker, title and line: sizes, fonts and ink colours.
- The lead each department picks, the leadName for shows and courses, and the facts lines.
- Solid mode.
- The per-department bar blend.

The blend holds the chrome review's H1 and M1:
- The states are hoisted in `LibraryBranches`, above the width-class switch, so a rotation keeps them.
- The blend is read from the list position, not from scroll deltas, so a back-navigation restores it correctly.
- Each tab has its own state.

TV is untouched: `ui-tv`, `ui-common` and `core/designsystem` have no diff. The static Fraunces cut is used under `TextAutoSize`, so no variation axes are lost.

The problems are in four places:
1. One side effect of the page-ground change (the tab row).
2. The compact layout and the 840–900dp band.
3. How legible and how wide the quote is.
4. The pages that share the hero (Documentaries, Collections, franchise), plus tests that cannot fail and a changelog that is out of date.

## High

### H1. Title and show pages now draw a `surface`-coloured band behind the tab row
`ui/catalog/TitleTabs.kt:46`. `ScrollableTabRow(...)` does not pass `containerColor`, so it uses the default.
- I checked material3 1.4.0's bytecode: `TabRowDefaults.primaryContainerColor` → `PrimaryNavigationTabTokens.ContainerColor` = `ColorSchemeKeyTokens.Surface`.
- Before this change the page was `surface` as well, so the band was invisible. Now the page is `background`.
- Scenario: open any film (`TitleDetailScreen.kt:111`) or any show or course (`CollectionScreen.kt:212`). The Overview/Cast/Similar/Details row sits on a full-width strip: `#151517` on `#0d0d0e` in dark, `#FBF8F2` on `#F4F0E8` in light.
- The web's `.tab-list` has no background, only a bottom rule (`title-page.css:121`).
- This is the same kind of seam this change set out to remove. It appears on every title page, in both themes.
- Fix: `ScrollableTabRow(..., containerColor = Color.Transparent)`, or `pageGround`.

I swept the rest of ui-mobile for other M3 defaults that use `surface`, `surfaceContainerLow` or `surfaceContainerLowest`. There are none:
- No `ListItem`, `Card`, `OutlinedCard`, `ElevatedCard`, `ElevatedButton`, `TopAppBar` or default `Surface`.
- Text fields are outlined (transparent). Dialogs use Sunk. The one `ModalBottomSheet` is over the black player. `NotesPanel` sets `surface` explicitly over the player.
- ui-common has no material3 at all.

## Medium

### M1. The compact hero leaves a bar-height gap in the 840–900dp band, and its copy never overlaps the art the way the web's does
`DepartmentHero.kt:216` (`vertical = if (topChrome > 0.dp) topChrome + Spacing.small else Spacing.large`).
- Root cause: the two width signals disagree.
  - `LibraryHome` bleeds when the WindowSizeClass is EXPANDED (≥840dp, from window metrics).
  - `DepartmentHero` goes compact when `Configuration.screenWidthDp` is ≤900.
  - Between the two, the compact layout gets `LocalTopChrome > 0`, and that value is added above and below copy that already sits *below* the art.
- Probe at w880dp with the bar: art bottom 563dp, kicker top 671dp, a 108dp gap, and another 108dp of dead space at the bottom.
- Devices that land in the band: a Pixel Fold unfolded in landscape (841dp), or a phone in landscape where `screenWidthDp` leaves out the navigation bar (a 914dp window reads as 866).
- Even outside the band, compact is off from the web:
  - `departments.css:87` puts the copy at `padding-top: 48vw` over a `64vw` art strip, so the copy overlaps the fade by 16vw.
  - Android starts the copy at 64vw + 24dp. That is 88dp lower at 400dp and 165dp lower at 880dp. The portrait sheet shows the gap.
  - Web bottom padding is 28px (Android 24). Web no-art top padding is 40px (Android 24).
- Fix:
  - Compact becomes `Box { art strip (0.64w); Column(padding(top = art ? 0.48w : 40.dp, horizontal = gutter, bottom = 28.dp)) }`, with no `topChrome` term. The art strip is what sits under the bar.
  - Either take `compact` from the same WindowSizeClass/window width that `LibraryHome` uses, or bleed only when width > 900.

### M2. The quote is 25% narrower than the web's, and its attribution is unreadable over bright art
`DepartmentHero.kt:175-183`, `DeptHeroText.kt:94-95, 106`.
- **Width.** The modifier order is `.widthIn(max = 272.dp).padding(end = gutter, …)`, followed by the quote's own `.padding(16.dp)`. So the gutter and the inner padding are both taken out of the 17rem cap.
  - Probe at 1164dp: the quote is laid out at max 203dp. The web's is 272px (`departments.css:42`; `right: var(--gutter)` sits outside the width).
  - The Movies and Series sheets show the effect: Android wraps to 3 lines where the web uses 2.
  - `maxLines = 4` with an ellipsis (the web has no clamp) then cuts taglines that the web shows in full.
- **Legibility.**
  - The web puts `text-shadow: 0 2px 28px rgba(0,0,0,.65)` on the quote (`departments.css:45`). It also draws a 34%×46% ellipse over the art, which is about 277×207dp at 1164dp.
  - Android has no shadow. Its patch is a `radialGradient` with the default radius, which is `minDimension/2` ≈ 85dp, centred in the quote box.
  - The attribution line and the ends of the quote lines fall outside that patch.
  - In the Movies and Series sheets, "— DER ASTRONAUT – PROJECT HAIL MARY" and "SHAMELESS – NICHT GANZ NÜCHTERN" disappear into faces and highlights. The web's copies read cleanly.
- Fix:
  - Use `.padding(end = gutter, top = …).widthIn(max = 272.dp + Spacing.medium * 2)`.
  - Add `shadow = Shadow(Color.Black.copy(alpha = .65f), Offset(0f, 2.dp.px), blurRadius = 28.dp.px)` to both quote styles.
  - Give the radial brush a radius near `size.maxDimension * 0.6f` (a `drawBehind` gives you the size).
  - Drop `maxLines`, or raise it to 6 or more.

### M3. The Documentaries hero is still a button that does nothing, and so is the Collections hero
`DepartmentHero.kt:88`, together with `DocumentariesDepartmentScreen.kt:112` and `CollectionsScreen.kt:45` (`onOpenTitle = {}`).
- The brief says the Documentaries hero is not clickable. The code does the opposite: any hero with a `lead` gets `clickable(role = Button)`. `DepartmentHeroTest.kt:151-153` even documents this.
- Scenario: TalkBack on the Documentaries tab announces the whole hero as a button ("Double-tap to Documentaries"). Tapping gives a ripple and nothing happens.
- The web hero is never clickable. Only the quote's credit links anywhere, and for Documentaries that link is `#`.
- On Collections, the web links the lead film (`collections-page.js:74`).
- `onClickLabel = title` also reads badly: "Double-tap to Movies".
- Fix:
  - Make it `onOpenTitle: ((String) -> Unit)?` and skip `clickable` when it is `null`. Documentaries passes `null`.
  - Collections should either pass the film-opening callback (parity with the web) or `null`.
  - The label should be `"Open ${leadName ?: lead.title}"`.

### M4. Collections on a phone: the taller hero pushes the last control off a page that does not scroll
`CollectionsScreen.kt:39` is a plain, non-scrolling `Column`. That is pre-existing, but this change made it worse.
- Probe of the same column at 412×915 (about 620dp left after the status bar, the three-row header and the nav bar):
  - Old hero: "＋ New list" at 569–620dp, still reachable.
  - New hero: "＋ New list" starts at 620dp, fully clipped, and "Your lists" starts at 628dp.
  - At 400×780 (540dp left), both old and new clip everything below the franchise row.
  - In every case the lists' own `LazyColumn` gets 0 height.
- Scenario: a phone user with one franchise can no longer create a list from Collections.
- Fix: make `CollectionsScreen` a single `LazyColumn` (hero, franchise row, list rows, "New list"). That also gives Collections a list state, so its hero can bleed under the bar the way the web's does. Today `heroArtOf` returns `null` for Collections, so its art always sits under a solid bar.

### M5. The franchise page title is squeezed onto one line
`DeptHeroText.kt:60-66`.
- `maxLines = 1` with `StepBased` suits one-word department names. `FranchiseScreen` uses the same hero for names like "The Lord of the Rings Collection".
- Probe with native graphics:

  | Width | Android | Web |
  |---|---|---|
  | 360dp | ≈26.7sp, one line | 41.6px over about 3 lines |
  | 1164dp | ≈48.8sp, one line | 64px over 2 lines |

- The web's own rule, `.franchise-hero .dept-title { font-size: clamp(2.6rem, 5.5vw, 5rem); line-height: .92 }` (`departments.css:109`), was not ported.
- A name of about 40 characters at 320dp reaches the 12sp floor and then clips (`BasicText` defaults to `Clip`).
- Fix: add a `franchise` or `titleMaxLines` variant: up to 3 lines, `fluid(41.6f, .055f, 80f)`, line-height .92. Keep `maxLines = 1` for department names.

### M6. Artwork mode is not honoured
`DepartmentHero.kt:136` always draws the art over 70% of the width.
- `appearance.css:23` sets `[data-backdrop="artwork"] .dept-art { inset: 0 }`: full-width art behind the words on wide screens.
- The spec asks to honour `LocalBackdrop` "exactly as `appearance.css` does".
- Fix: `val fraction = if (LocalBackdrop.current == Backdrop.ARTWORK) 1f else ART_WIDTH_FRACTION`.
- `TitleSpread` has the same gap already; it is outside this diff.

### M7. The three "never breaks mid-word" tests cannot fail
`DepartmentHeroTest.kt:100-123`. There are two reasons:
1. A text node's semantics carry the full string however it is laid out. Probe: a `BasicText("DOCUMENTARIES")` at 300sp, `maxLines = 1`, with an ellipsis, still passes the exact `onNodeWithText(...).assertIsDisplayed()`.
2. These classes run Robolectric's LEGACY graphics, where text is measured at about 1px per character. The probe measured "DOCUMENTARIES" at 14px, so autosizing never kicks in.

They are the only guard for the bug the user reported.

Fix: annotate them with `@GraphicsMode(NATIVE)`. Native measurement works; only `captureToImage` failed for the implementer. Then assert on `SemanticsActions.GetTextLayoutResult`: `lineCount == 1 && !hasVisualOverflow`. My probe did exactly this and got realistic widths.

### M8. The changelog's 0.71.1 entry is out of date and leaves things out
`docs/project-changelog.md`:
- It says the quote is "below the facts line on a phone … kept here". It is hidden now.
- It says the name "wraps to a second line rather than truncating". It now steps down to fit one line.
- It does not mention the app-wide page ground change: every screen moves from `#151517` to `#0d0d0e`, and in light from `#FBF8F2` to `#F4F0E8`.
- It does not mention that Collections and franchise pages now show the quote on wide windows.

## Low
- **L1. The copy column is 566dp, not 640.** `DepartmentHero.kt:156` does `widthIn(640).padding(gutter)`, which takes the gutter out of the 40rem cap. The probe confirmed `maxW` = 566 at 1164dp. Fix: swap the order. That also gives long titles more room before autosize shrinks them.
- **L2. `DepartmentScrollStates` is rebuilt on every recomposition.** `rememberDepartmentScrollStates()` remembers the four states but not the wrapper, so `remember(…, deptScroll)` (`LibraryFlowBranches.kt:142`) recomputes every time `LibraryBranches` recomposes. `LibraryHome` then rebuilds its `derivedStateOf`, and `CatalogScreen` gets an unequal parameter. Nothing breaks. Fix: `remember { DepartmentScrollStates(...) }`, or make it a `data class`.
- **L3. Spacing without art or in Solid.** The web puts the copy 64px (no art) or 40px (Solid) below the masthead, with 48px at the bottom. Android uses 24/24.
- **L4. The quote's italic is synthesized.** Android slants upright Newsreader (the `Read` family has no italic file). The web loads `newsreader-italic-*.woff2`.
- **L5. The title has no heading semantics.** The web uses `h1`. Fix: `semantics { heading() }` when the hero is not clickable.
- **L6. The Movies line has no thousands separator.** It reads "1761 hours" where the web reads "1,761 hours" (see the Movies sheet). This is pre-existing in `MoviesDepartmentScreen.kt:108-112` (`movieDeptLine`). Fix: `NumberFormat.getIntegerInstance().format(it)`.
- **L7. The refresh bar pushes the hero down.** On department tabs, the refresh progress and notice rows are padded by `topChrome` (`CatalogScreen.kt:172-181`), which moves the bleeding hero down by the bar's height while a refresh runs. Home already behaved this way.
- **L8. In light theme, department tabs now open under the translucent bar,** which reads as a grey band over light paper (see the light sheet). This comes from the chrome phase's bar and is newly exposed on four tabs. It is an observation, not a defect of this diff.
- **L9. Style.**
  - `DepartmentHero.kt` is 226 lines and `LibraryFlowBranches.kt` is 338, both over 200.
  - Several comments tell the history instead of the reason: `PageGround.kt:10-15` ("for a while", "once"), `DepartmentHero.kt:115-121` ("was the actual bug behind a seam this scrim once showed").
  - `DepartmentHeroTest.kt:129` says "this phase's own", which is a plan reference.
  - `DeptQuote(onImage)` has one caller, which always passes `true`, so the `false` branch is dead code.
- **L10. Evidence.**
  - The implementer's report lists two "scrolled-bar-opaque" sheets that are not in `reports/`.
  - The "light web vs android" sheet puts light Android next to the *dark* web page, so it proves nothing about light-theme parity.

## Verified correct (no action)
- **Scrim.** Android builds the layers back-to-front: 180° top fade (paper 55% → transparent at 24%), then 0° bottom fade (transparent to 62%, then paper), then the 90° left fade in front (paper → 70% at 26% → transparent at 64%). That is CSS's first-declared-on-top order. The stops match. The compact scrim matches `departments.css:90`.
- **Height.** `fluid(420, .58, 600, h)`; the test measures 450.66dp at 777dp.
- **Type and colour.**
  - Title: clamp(56, 8.5vw, 120) wide, clamp(48, 16vw, 72) compact, weight 500, -0.03em, line-height .86, ink.
  - Eyebrow: ink-3.
  - Line: 20sp Newsreader, 1.4, tnum, ink-2.
  - Quote: clamp(20.8, 1.9vw, 28.8), italic, 1.2, on-image.
  - Attribution: 10sp, .28em, 1.6, on-image-2 at 0.82.
- **Hidden where the web hides it.** No quote at ≤900dp, and none in Solid.
- **Content per department.**
  - Leads: Movies is the most popular unwatched film with a backdrop, Series/Tutorials the most popular first item with a backdrop, Documentaries the most recent with a backdrop.
  - Kicker "Only in your library" on all four; the franchise kicker is "The collection".
  - leadName: `lead.show` for shows and courses, `lead.title` otherwise.
  - Taps: Movies opens the film, Series/Tutorials open the show, the franchise page opens the film.
- **The bar blend.**
  - `heroArtOf` picks the same lead as each screen.
  - With Solid, or with no art, the bar starts solid.
  - Tab switches, rotation and back-navigation each read the right state's position (H1 and M1 hold).
- **The page ground.** `onBackground` equals `onSurface` in both themes, so content colours do not change. Text contrast against Ground is already held by `PaletteContrastTest`. Settings panes, the compact header and the departments bar already painted `background`; the pushed frame's `TopAppBar` was `background` over a `surface` Scaffold, and now the two match.

## Recommended actions (in order)
1. H1: make the `ScrollableTabRow` container transparent.
2. M2: quote width, text shadow and radial radius.
3. M1: the compact layout (48vw overlap, no `topChrome`) and a single width signal.
4. M3: a nullable `onOpenTitle`; Documentaries not clickable; Collections wired or null; a better click label.
5. M4: make `CollectionsScreen` a `LazyColumn`.
6. M7: native-graphics tests that assert on the layout result.
7. M5 and M6: the franchise title variant and Artwork mode at full width.
8. M8: fix the changelog.
9. The Low items as time allows.

## Metrics
- Gate: green. I ran it, then forced a rerun of the ui-mobile unit tests.
- Lint: green. No new issues.
- Test coverage: I did not measure it. The new tests cover placement, Solid mode, height and the not-clickable-without-a-lead case. The one-line title guard is vacuous (M7).

## Unresolved questions
- Should the whole-hero tap stay at all? The web makes only the credit clickable. The implementer kept the tap target on purpose, and I have not reversed that. M3 only asks that no-op heroes stop being buttons.
- In the 840–900dp band, should the bar bleed over the compact art strip, or should the compact layout never bleed? On the web at ≤900 the masthead does not overlap the hero.
- The TitleTabRow band (H1) rests on the bytecode, not on a device. I am about 90% confident; a device screenshot of a film page would settle it.

**Status:** DONE_WITH_CONCERNS
**Summary:** The port is faithful where it counts, the H1/M1 bar behaviour holds, TV is untouched and the gate is green. The page-ground change put a `surface` band behind every title page's tab row. The compact layout and the quote's width and legibility miss the web, and Documentaries is still a no-op button. The one-line title tests cannot fail.
**Concerns/Blockers:** The scratchpad is shared between agents. My first probe copy merged into another agent's `scratchpad/probe/android` (decoder work), and I told the lead; I then moved my probes to a separately named directory.
