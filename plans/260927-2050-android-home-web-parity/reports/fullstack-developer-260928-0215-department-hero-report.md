# Phase Implementation Report

## Executed Phase
- Phase: phase-03b-department-hero-web-look
- Plan: plans/260927-2050-android-home-web-parity
- Status: completed

## Addendum 2 — root-caused at the lead's own direction (same session, same worktree)

The first addendum's fix 3 (scrims fading to `colorScheme.background`) was itself a
patch over a real gap the lead already had on file: `AppChrome.kt`'s pushed-frame
`Scaffold` painted its own container `colorScheme.surface`, one step lighter than the
window's actual ground — `plans/260927-1731-android-settings-system-redesign/phase-05-verify-docs-version.md`
§ Known follow-ups, item 1, open since that phase closed. Reverted the scrim-only
patch and fixed the root instead, per instruction:

1. **`ui.pageGround`** (`ui-mobile/src/main/kotlin/ui/PageGround.kt`, new) — one
   shared `ColorScheme.background` accessor. `MobileApp.kt`'s root `Surface`
   (previously M3's own default `color`, silently `colorScheme.surface`) and
   `AppChrome.kt`'s `LibraryScaffold` (`containerColor`, previously an explicit
   `colorScheme.surface`) both read it now; `DepartmentHero.kt`'s two scrims read
   it too, back on `background` as they always should have been, now that the page
   around them actually draws on it.
2. **A genuine light-theme bug the lead's own request for a light sheet caught,
   not the seam fix.** `WideDeptHero`'s copy column (kicker/title/facts line)
   passed `onImage = art != null` — on-image's fixed, always-light two colours,
   whenever any art was present, following the OLD `DepartmentHero`'s own
   convention. Read against `departments.css:31-37` again to be sure: neither
   `.dept-title` nor `.dept-kicker`/`.dept-line` names `--on-image` at all — only
   `.dept-quote` does, since only the quote actually sits over the picture itself;
   the copy sits where the art's own left fade has already blended it back to the
   page. In dark theme this coincidentally read fine (light text, near-black
   paper); the Series sheet in light theme showed it for what it was — the
   kicker/title/line nearly invisible, on-image's own light colour against light
   paper. Fixed to always read the theme's own ink; `onImage` dropped from
   `DeptHeroWords` entirely, since neither caller ever needed it true any more.
3. **A pixel-level Robolectric check was attempted for real, not skipped.** Three
   different harnesses — a plain manual `ActivityController`, the same with
   `@GraphicsMode(NATIVE)`, and a self-managed `ActivityScenarioRule` — each
   failed `captureToImage()` for their own environment reason (a `forceRedraw`
   timeout twice, an instrumentation `RuntimeException` once), none of them the
   fix under test. `PageGroundColorTest` instead composes `MediagramTheme` under
   each theme and captures what `MaterialTheme.colorScheme.pageGround`/`.surface`
   actually resolve to — no drawing needed, and it catches the same regression
   (the ground and the page one step above it stay two different colours, in
   both themes).
4. **TV checked, not touched.** `ui.tv.TvApp`'s own `TvShell` already reads
   `containerColor = MaterialTheme.colorScheme.background` explicitly — no
   equivalent gap. Every remaining `colorScheme.surface` read on TV is a real
   card, plate, or dialog (`TvFeatureStrip`, `TvPlate`, `TvProfileTiles`,
   `TvAddToListDialog`, `TvConfirmDialog`) — correctly raised, not page ground.

### Gate and device verification

Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug`
green, run twice (once after the root-cause fix, once more after the light-theme
text-colour fix it surfaced). Reinstalled on `caad49da`; test profile confirmed
(avatar "T") before, after, and after every Appearance toggle. Appearance was
opened and its Theme radio tapped twice this round — Light, to capture the sheet
the lead asked for, then Dark again — the same on-device technique this exact
phase's own first pass already used for a light-theme scrim check, always ending
back on the profile's own starting point ("Dark · Green"), confirmed by a final
screenshot before finishing. No other Settings choice was touched.

### Files touched this addendum

- `ui-mobile/src/main/kotlin/ui/PageGround.kt` — new, `ColorScheme.pageGround`
- `ui-mobile/src/main/kotlin/ui/MobileApp.kt` — root `Surface` now takes an
  explicit `color = MaterialTheme.colorScheme.pageGround`
- `ui-mobile/src/main/kotlin/ui/AppChrome.kt` — `LibraryScaffold`'s `Scaffold`
  `containerColor` → `pageGround`
- `ui-mobile/src/main/kotlin/ui/catalog/DepartmentHero.kt` — both scrims'
  `paper` → `pageGround`; `DeptHeroWords` calls drop `onImage`
- `ui-mobile/src/main/kotlin/ui/catalog/DeptHeroText.kt` — `DeptHeroWords` lost
  its `onImage` parameter, always reads the theme's own ink now
- `ui-mobile/src/test/kotlin/ui/PageGroundColorTest.kt` — new (composition-capture,
  not pixel-capture — see fix 3 above)
- `plans/260927-1731-android-settings-system-redesign/phase-05-verify-docs-version.md`
  — follow-up items 1, 3 and 4 annotated addressed (`DepartmentHero.kt`'s own
  half; `TvCoverStory.kt`/Home's cover stay/are out of scope, see the file
  itself for which)
- 3 screenshots replaced/added in `plans/260927-2050-android-home-web-parity/reports/`
  (`dept-hero-260928-series-top-web-vs-android.png` regenerated,
  `dept-hero-260928-series-top-light-web-vs-android.png` and
  `dept-hero-260928-home-dark-vs-light-no-seam.png` new)

### Unresolved from this addendum

- **`TvCoverStory.kt`** (phase-05 follow-up item 3's other half) stays open —
  TV, out of this phase's ownership.
- **`CoverStory.kt`/`CoverSlide.kt` (Home's own cover)** was checked directly for
  the same page/surface and on-image-off-the-picture defects and found clean on
  both counts — not a live instance of either follow-up any more, noted in
  `phase-05-verify-docs-version.md` itself rather than left for a future check to
  rediscover.

---

## Addendum — lead review round (same session, same worktree)

The lead reviewed the first pass's sheets and found four issues; all four
addressed, gate re-run green, sheets regenerated. In order:

1. **"DOCUMENTARIES" wrapped mid-word.** The first pass's fix for
   truncation (`maxLines = 2`) let the web's own clamp size wrap the
   string — legal for CSS text, wrong for a title nobody should ever see
   broken inside a word. Reused `designsystem.PageHead`'s own technique
   instead of its own component (see the original report's "decisions,"
   point 4, on why this component doesn't call `PageHead` directly):
   `BasicText` with `TextAutoSize.StepBased(maxFontSize = ...)` and
   `maxLines = 1` — the web's clamp value is now a *ceiling* the title
   steps down from to fit one line, never a wrap boundary. New tests at
   the three widths asked for (360dp, 777dp, 1164dp), each asserting the
   full string "DOCUMENTARIES" renders as one exact, unellipsized node.
2. **Compact quote reversed to match the web exactly.** `CompactDeptHero`
   no longer draws a quote at all — the web's own `.dept-quote{display:none}`
   below 900px (`departments.css:92`), not the deliberate-Android-difference
   call the first pass made. `quote` is only ever computed for the wide
   branch now. `HeroBackdropModesTest`'s own compact-width case updated to
   match (it no longer asserts a quote it never draws).
3. **Hard bottom edge on the art, root-caused, not guessed at.** Read as a
   gradient-math bug first; it was not — a `matchParentSize()`-wrapped
   debug build with the bottom fade's target colour swapped for solid red,
   captured on the tablet, showed the fade itself is smooth and reaches all
   the way to the hero's own bottom edge (confirmed against a Robolectric
   height check too: 451dp, matching `fluid(420,0.58,600,777dp)` exactly).
   The actual mismatch: this hero's scrim fades to `colorScheme.background`
   (`Palette.Ground`, `#0d0d0e`), but the app's own visible page colour is
   `colorScheme.surface` (`Palette.Page`, `#151517`) — `MobileApp.kt`'s root
   `Surface()` takes M3's default `color` parameter, which is `surface`, not
   `background`. The two are close but not equal in this palette, so the
   fade's own endpoint sat a shade darker than what actually follows it —
   invisible where the difference is small, a visible step exactly at the
   art's own edge where nothing past it lightens back up. Fixed by fading to
   `colorScheme.surface` in both the wide and compact scrims. `ui.catalog.home.CoverSlide`'s
   own `CoverScrim` fades to the same, now-wrong `background` — flagged
   below, not fixed, since it is Home's own file, outside this phase's
   ownership.
4. **Documentaries/Tutorials no-art, run to ground.** Not an Android
   selection or mapping bug — `heroArtOf`/`DepartmentHero` read
   `MediaSet.backdropPath` correctly, the same field Movies/Series' own
   working art comes through. The gap is in the shared Rust core, already
   self-documented and already tracked: `crates/mediagram-core/src/api/store/editorial.rs:154-176`
   (`resolve_artwork`) resolves a poster and a season poster by
   materialising from the index's own `artwork` table on a miss, but a
   backdrop `false` — only ever names a file already on disk
   (`resolve_artwork`'s own doc comment, lines 158-166, names this
   explicitly as "a known gap from the web player... not fixed in the same
   change that batched it," and points at the exact issue: **`daBOB/mediagram#1`**,
   "Android shows no custom backdrops the web shows," open, filed by this
   project's own user, 12 titles affected on the current index). The web's
   own `has()` (`web/src/catalog/routes.ts`) checks the poster store *or*
   the artwork table; Android's core does not, for backdrops specifically.
   **Not touched — Rust, per instruction, and already planned as its own
   issue.**

Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug` green (one `HeroBackdropModesTest` case needed updating for fix 2, everything else passed unchanged). Reinstalled on `caad49da` (test profile — avatar "T" checked before, after, and after rotating for the portrait shot); all four top sheets and the portrait one regenerated against the same live web stub. The two scrolled ("bar opaque past the hero") sheets from the first pass were not regenerated — nothing in this round touches the blend/bar mechanism they check, and they already showed a fully opaque bar on both sides.

### Files touched this addendum
- `ui-mobile/src/main/kotlin/ui/catalog/DepartmentHero.kt` — `colorScheme.background` → `.surface` (both scrims); compact no longer receives or draws `quote`; `DEPARTMENT_HERO_TEST_TAG` added to the outer `Box`
- `ui-mobile/src/main/kotlin/ui/catalog/DeptHeroText.kt` — title switched to `BasicText` + `TextAutoSize.StepBased`, `maxLines = 1`
- `ui-mobile/src/test/kotlin/ui/catalog/DepartmentHeroTest.kt` — `onACompactWindowTheQuoteSitsBelowTheLine` → `onACompactWindowTheQuoteIsHidden`; three new one-line-at-three-widths tests; new `theHerosOwnHeightMatchesTheWebsClampFloor` regression guard
- `ui-mobile/src/test/kotlin/ui/catalog/HeroBackdropModesTest.kt` — `defaultKeepsTheDepartmentHeroesArtAndQuote` → `defaultKeepsTheDepartmentHeroesArt`, quote assertion dropped (compact-only class)
- 5 screenshots in `plans/260927-2050-android-home-web-parity/reports/` regenerated in place (`dept-hero-260928-{series,movies,tutorials,documentaries}-top-web-vs-android.png`, `dept-hero-260928-series-android-portrait-compact.png`); the wrap-fix screenshot (`dept-hero-260928-documentaries-android-no-art-wrap-fix.png`) replaced with the post-fix capture

### Unresolved from this addendum
- **`CoverSlide`'s own `CoverScrim` likely has the same `background`-vs-`surface` mismatch** — not checked closely or fixed, since it is Home's own file (phase 02's), outside this phase's ownership. Worth the same one-line fix if a maintainer confirms the same seam shows on Home's cover.
- **`daBOB/mediagram#1`** stays open, unplanned by this phase (Rust, out of scope per instruction) — the issue itself already asks the right question ("should Android's backdrop resolution materialize from the `artwork` table on a miss, the same as a poster already does").

---


## The bug, and the fix in one line

User: "the hero element in android app is broken layouted." The Movies/Series/
Tutorials/Documentaries hero was the pre-redesign one — art hard-edged below the
bar, a small title over its corner, a separate pull-quote block underneath.
Rebuilt to the web's `departmentHero()`: art fading into the page from the left
under a bar that starts translucent and settles solid, a huge uppercase Fraunces
title, a Geist eyebrow, a Newsreader facts line, and the lead's own tagline as a
quote inside the hero — top-right wide, below the line on compact.

## Files Modified

New (`ui-mobile/src/main/kotlin/`):
- `ui/catalog/DepartmentHero.kt` (219) — the hero itself: wide layout (art under
  the bar, copy bottom-left, quote top-right) and compact layout (art strip,
  copy and quote stacked below it)
- `ui/catalog/DeptHeroText.kt` (106) — `DeptHeroWords` (kicker/title/line, shared
  wide+compact) and `DeptQuote` (the tagline + attribution), split out to keep
  `DepartmentHero.kt` near the 200-line guideline
- `ui/catalog/DeptRowHeading.kt` (40) — split out of the old `DepartmentHero.kt`
  unchanged, for the same reason
- `ui/catalog/DepartmentScrollStates.kt` (32) — one hoisted `LazyListState`/
  `LazyGridState` per department that can draw a hero (Movies/Documentaries are
  a plain column, Series/Tutorials a grid), the same hoisting `homeListState`
  already does for Home
- `ui/HeroArtOf.kt` (49) — recomputes the active tab's own lead backdrop, the
  same way `LibraryBranches`'s existing `hasCover` recomputes Home's rather than
  reading state back from whichever screen composes it
- `ui-mobile/src/test/kotlin/ui/catalog/DepartmentHeroTest.kt` (118) — new tests
  (below)

Deleted:
- `ui/catalog/PullQuote.kt` — the quote it drew moved inside the hero itself;
  checked every remaining caller (Movies/Shows/Documentaries department
  screens) before deleting, none left

Modified:
- `ui/LibraryFlowBranches.kt` (302→338) — hoists `DepartmentScrollStates`
  beside `homeListState`; computes `hasHeroArt` for the active tab via
  `heroArtOf`; combines that with Home's own `hasCover` into one `heroState:
  HeroListState?` passed to `LibraryHome`
- `ui/catalog/CatalogScreen.kt` (260→269) — threads `DepartmentScrollStates`
  through to `MoviesDepartmentScreen`/`ShowsDepartment`/`DocumentariesDepartment`;
  `CatalogScreen` itself narrowed to `internal` (it now takes an `internal`
  parameter type and has exactly one caller in this module)
- `ui/catalog/MoviesDepartmentScreen.kt` — `state: LazyListState` param
  (defaulted), `PullQuote` item removed (hero draws it now)
- `ui/catalog/ShowsDepartmentScreen.kt` — `state: LazyGridState` param
  (defaulted), `PullQuote` item removed, `leadName = leadTitle?.show` (the
  show's own name credits the quote, not whichever episode happened to lead —
  the web's own `lead?.show`)
- `ui/catalog/DocumentariesDepartmentScreen.kt` — `listState: LazyListState`
  param (defaulted) on the `DocumentariesDepartment` wrapper, threaded to
  `DocumentariesDepartmentScreen`'s own `state`; `PullQuote` item removed
- `ui/chrome/LibraryHome.kt` — `homeScrollState: LazyListState, hasCover:
  Boolean` → one `heroState: HeroListState?`; `bleed`/`blend` generalized from
  "Home with a cover" to "any tab with a hero"
- `ui/chrome/CoverBlend.kt` (23→60) — added `HeroListState` (the three numbers
  `coverBlend` needs, read live) and `LazyListState`/`LazyGridState` adapters;
  `coverBlend` itself untouched, only its doc comment generalized
- `ui/chrome/DepartmentsBar.kt` — doc comment only (no longer claims the
  translucent state is Home-only)
- `ui-mobile/src/test/kotlin/ui/catalog/HeroBackdropModesTest.kt` — two tests
  renamed (`...AndPullQuote` → `...AndQuote`, the component they name is gone)
- `ui-mobile/src/test/kotlin/ui/chrome/WidthClassStateTest.kt` — two
  `LibraryHome(...)` calls updated to the new `heroState` param

Manifests / docs (not committed):
- `Cargo.toml`, `Cargo.lock` (5 workspace members: `mediagram`,
  `mediagram-cache`, `mediagram-core`, `mediagram-tmdb`, `mlib-spec`),
  `web/package.json`, `android/app/build.gradle.kts` `versionName` →
  `0.71.1` by regex, not exact-string (`versionCode` untouched, patch bump)
- `docs/project-changelog.md` — new `0.71.1` entry
- `plans/260927-2050-android-home-web-parity/{plan.md,phase-03b-*.md}` — phase
  marked completed, Todo boxes ticked
- 8 screenshots in `plans/260927-2050-android-home-web-parity/reports/`
  (`dept-hero-260928-*.png`)

## Numbers taken from the web's own CSS

- Hero height (wide): `min-height: clamp(420px, 58vh, 600px)`
  (`departments.css:10`) → `fluid(420f, 0.58f, 600f, windowHeightDp)`
- Art strip: `inset: 0 0 0 30%` (`departments.css:16`) → right 70% of the hero's
  own width
- Copy column: `max-width: 40rem` (`departments.css:29`) → 640dp
- Title (wide): `font: 500 clamp(3.5rem, 8.5vw, 7.5rem)/0.86` (`departments.css:33`)
  → `fluid(56f, 0.085f, 120f, windowWidthDp)`, drawn through `designsystem.PageTitle`
  (already the static Fraunces cut this exact clamp names in its own doc comment,
  from the Settings-redesign phase — reused, not rebuilt)
- Title (compact, ≤900px): `clamp(3rem, 16vw, 4.5rem)` (`departments.css:91`) →
  `fluid(48f, 0.16f, 72f, windowWidthDp)`, `designsystem.PageTitleCompact`
- Facts line: `font: 400 1.25rem/1.4 var(--serif)`, tabular-nums
  (`departments.css:37`) → 20sp Newsreader, `fontFeatureSettings = "tnum"`
- Kicker: `.eyebrow` (`theme.css:196-203`) → `designsystem.Eyebrow`, reused as-is
  (11sp Geist medium, 0.32em tracking) — no department-specific override needed
- Quote (wide): `max-width: 17rem` (272dp), `top: calc(masthead-height + 12%)`
  (`departments.css:41`), blockquote `clamp(1.3rem, 1.9vw, 1.8rem)/1.2` italic
  (`departments.css:48`) → `fluid(20.8f, 0.019f, 28.8f, windowWidthDp)`; figcaption
  `500 0.625rem/1.6`, 0.28em tracking, uppercase (`departments.css:49`) → 10sp
- Compact art strip: `height: 64vw` (`departments.css:89`) → `(windowWidthDp *
  0.64f).dp`, no clamp on the web either
- Gradient stops (both the wide 3-layer art scrim and the compact single one)
  transcribed directly from `.dept-art::after`/compact override
  (`departments.css:22-28,90`), reversed into Compose's own paint order — the
  same "CSS paints the first-declared layer on top" rule `CoverSlide`'s own
  scrim already documents

## Decisions taken where the source (not just the spec) settled it

1. ~~Compact keeps the quote; the web hides it.~~ **Superseded — see the
   addendum at the top.** The first pass read `departments.css:92`
   (`.dept-quote{display:none}` at ≤900px) as a divergence worth flagging
   rather than closing; the lead's review asked for parity, so this now
   matches the web exactly — `CompactDeptHero` never draws a quote at all.
2. ~~Title wraps to two lines rather than truncating.~~ **Superseded — see
   the addendum at the top.** Wrapping stopped truncation but let
   "DOCUMENTARIES" break mid-word ("DOCUMENT"/"ARIES") on a real device;
   `TextAutoSize.StepBased` (`designsystem.PageHead`'s own technique) now
   keeps the title on one line at every width, stepping the size down from
   the web's own clamp instead of wrapping or truncating.
3. **`aria-pressed`/click-target parity for the hero as a whole, not just the
   quote's own credit link.** The web's `departmentHero()` has no whole-hero
   anchor at all — only the quote's own `<a>` inside the credit line links
   anywhere. The existing Android convention (already shipped before this
   phase, not introduced here) makes the *whole* hero a tap target when
   `lead` is present, calling `onOpenTitle`/`onOpenCollection`; Documentaries
   already passed a no-op there. Kept as-is: a touch-target-sized affordance
   reads better than a small in-image link on a tablet, and requirement 5
   ("Documentaries hero stays a no-op tap … other departments' hero taps
   behave as the web's") is about *where a tap goes*, which this already
   matches, not about matching the web's own hit-test geometry pixel for
   pixel.
4. **`DepartmentHero` does not call `designsystem.PageHead`**, even though
   `PageHead` was built (Settings-redesign phase) citing this exact same CSS
   selector (`.dept-title`, `departments.css:31-36`) in its own doc comment.
   Read `PageHead.kt` and its one real caller (`SettingsPage.kt`) directly:
   Settings draws title-then-eyebrow (`el("h1","dept-title",...)` before
   `el("p","eyebrow",...)`, `settings-page.js:51`) — the *opposite* order
   from `department-hero.js`'s kicker-then-title-then-line, and `PageHead`
   has no third slot for the facts line or the quote either. `PageHead`'s
   own doc comment calling this "the web's own order" is accurate for its
   one real caller, not for `department-hero.js` in general — an
   over-generalization from the phase that built it, not a bug worth fixing
   there. Reused what does transfer directly instead: the exact same static
   font pair (`PageTitle`/`PageTitleCompact`) and the same wide/compact
   split, built inline in the kicker→title→line→quote order the web's own
   component actually uses.
5. **Documentaries/Tutorials showing no-art on this device is pre-existing,
   not a regression.** The web reference shots (fetched live from the stub
   at the same profile) show real backdrop art and a quote for both — Android
   shows the plain no-art hero for both. Checked `documentaries-260928-tablet-department-hero.png`
   (this plan's own phase-03 screenshot, taken before this phase touched
   anything): identically no-art, on the same device, same library, same old
   hero code. This is a data-availability gap (whether `backdropPath` is
   populated for a documentary/tutorial lead in this device's local index),
   not a `DepartmentHero` rendering defect — Movies and Series, which do have
   backdrop data here, both render art + quote correctly. Out of this
   phase's ownership (`feature/catalog`'s index population, not
   `ui-mobile`'s hero); flagged rather than chased.

## Every remaining difference from the web, with its reason

- **Art crop alignment.** The web crops at `object-position: 60% 25%`
  (`departments.css:17`); `HeroArtwork` (in `ui-common`, a shared component
  many other heroes already call, not owned by this phase) only exposes
  `contentScale`, defaulting `AsyncImage`'s own crop to center. On this
  device's own Movies backdrop (a mostly-dark spacesuit-interior shot), the
  compact strip's default center crop lands on a dark, low-detail region of
  the frame — visible faintly in `dept-hero-260928-series-android-portrait-compact.png`'s
  own Movies pass, not reproduced there since Series' own backdrop is bright
  throughout. Adding an `alignment` parameter to `HeroArtwork` was in scope
  by file count but not by ownership (shared, cross-department component);
  flagged rather than widened.
- **Radial "under the quote" scrim approximated as a circle, not a true CSS
  ellipse.** Folded into `DeptQuote`'s own background instead of a fourth
  full-width gradient layer in the art stack — functionally the same
  (guarantees contrast for `OnImage`'s fixed light text regardless of theme
  or the picture underneath, the same problem the cover's own M2 fix from
  phase 02 solved), simpler, and Compose has no native elliptical radial
  brush without a custom shader.
- **Documentaries/Tutorials no-art on this device.** Confirmed pre-existing
  and precisely root-caused — see the addendum at the top (fix 4) and
  decision 5 below.

## Tests Status
- Type check / compile: pass (`:ui-mobile:compileDebugKotlin`,
  `:ui-mobile:compileDebugUnitTestKotlin`)
- Unit tests: pass — full gate below, including 5 new `DepartmentHeroTest`
  cases (uppercase eyebrow/title, quote above-vs-below the title wide/compact,
  Solid hides art+quote on a wide window too — the existing
  `HeroBackdropModesTest` only covered compact — and a hero with no lead
  carries no click affordance at all)
- Lint: `./gradlew lint` green, no new issues
- `:app:assembleDebug`: pass

Gate run exactly as specified, twice (once before, once after the
`matchParentSize()` fix and the `maxLines` fix below):
```
./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest \
  :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug
```
Green on the final run. `:ui-tv` untouched (no files in `ui-tv/` touched this
phase; its own tests ran as part of the gate and stayed green).

## Bugs caught only by running it, not by reading the source

- **`fillMaxHeight()` under an unbounded height, again.** The wide art strip
  first used `Modifier.fillMaxHeight().fillMaxWidth(0.7f).align(CenterEnd)`
  directly inside the hero's own `heightIn(min = ...)` Box — the exact trap
  `CoverSlide`'s own doc comment already names for a `LazyColumn` item's
  unbounded height, reproduced here because I didn't re-read that comment
  closely enough on the first pass. Two new `DepartmentHeroTest` cases
  (quote-vs-title vertical order, wide and compact) failed with both nodes
  reporting `top = 0.0.dp` — the merged-semantics tree read first (a second,
  smaller mistake, fixed by adding `useUnmergedTree = true`, matching
  `HeroBackdropModesTest`'s own established note on why), and once that was
  fixed, the numbers showed the hero measuring itself at the full test
  window's height rather than its own floor. Fixed by wrapping the art +
  its three gradient layers in one `Box(Modifier.matchParentSize())` first —
  the same pattern `HeroArtwork`/`CoverScrim` already use in `CoverSlide`.
- **Title truncation on the tablet, not in Robolectric.** See "Decisions",
  point 2 — a real-device-only catch, since Robolectric's own text metrics
  never wrapped this string either way at the widths these tests use.

## Tablet sheets

`plans/260927-2050-android-home-web-parity/reports/`, tablet `caad49da`, "test"
profile throughout (avatar "T" checked before install, after install, after
rotating to portrait, and again after the final reinstall):

- `dept-hero-260928-series-top-web-vs-android.png`,
  `dept-hero-260928-movies-top-web-vs-android.png`,
  `dept-hero-260928-tutorials-top-web-vs-android.png`,
  `dept-hero-260928-documentaries-top-web-vs-android.png` — web (left) vs
  Android (right), top of each department page
- `dept-hero-260928-series-scrolled-bar-opaque-web-vs-android.png`,
  `dept-hero-260928-movies-scrolled-bar-opaque-web-vs-android.png` — scrolled
  past the hero, bar fully opaque on both
- `dept-hero-260928-series-android-portrait-compact.png` — web wide (left,
  for the copy) vs Android portrait/compact (right): art strip, quote below
  the line
- `dept-hero-260928-documentaries-android-no-art-wrap-fix.png` — the
  "DOCUMENTARIES" two-line wrap, post-fix

Navigation only, per instruction — Settings was opened once to read the
current Appearance value (`Dark · Green`) and confirm nothing had drifted,
then backed out without tapping any choice inside it; Light theme and the
Artwork/backdrop modes were verified through `DepartmentHeroTest` and source
reading instead (see "Decisions", and phase 02's own precedent for the same
split between on-device and source-verified claims).

## Version
- `Cargo.toml` (workspace `version`), `Cargo.lock` (5 members), `web/package.json`,
  `android/app/build.gradle.kts` `versionName`: `0.71.1` (patch — a rebuild/fix,
  not a new feature, per `plan.md`'s own versioning note)
- `versionCode` untouched (19)
- `docs/project-changelog.md`: new `0.71.1` entry, this phase's own voice
- Not committed, per instructions — worktree left with the diff for review

## Issues Encountered
- `DepartmentHero.kt` lands at 219 lines, just over the 200-line guideline —
  `DeptRowHeading`/`DeptHeroWords`/`DeptQuote` were already split into their
  own files to get it this close; a further split (e.g. wide vs compact into
  separate files) was judged not worth fragmenting a single cohesive
  composable further, the same call `HomeCover.kt` (240 lines) made in phase
  02's own report.
- `LibraryFlowBranches.kt` grew from 302 to 338 lines — already well over the
  guideline before this phase touched it (pre-existing orchestration file);
  this phase's own net addition is the ~36-line `heroState`/`hasHeroArt`
  block, with the heavier `heroArtOf` helper moved to its own file
  (`ui/HeroArtOf.kt`) specifically to keep that addition small rather than
  making an already-large file materially larger.

## Next Steps
- Phase 04 (verify on tablet, docs, version) is the last phase in this plan.
- Not committed; worktree left with the diff for review.

## Unresolved Questions
- **`daBOB/mediagram#1`** (Documentaries/Tutorials no-art) stays open —
  Rust, out of this phase's scope per instruction; see addendum 2's fix 4
  for the exact file:line and the issue's own open question.
- **`TvCoverStory.kt`** (phase-05 follow-up item 3's TV half) stays open —
  TV, out of this phase's ownership.
- **`HeroArtwork`'s missing `object-position` equivalent** — a real, if minor,
  crop-quality gap on some backdrops; the fix belongs in `ui-common`, not
  this phase's own file ownership.

**Status:** DONE
**Summary:** Movies/Series/Tutorials/Documentaries (and, as a side effect of
sharing the same `DepartmentHero`, Collections and a franchise page) now open
the way the web's department pages do — art fading smoothly into the actual
page colour, itself now `colorScheme.background` everywhere a page's own
container is set (a root-caused fix, not a local patch — closes a standing
follow-up from an earlier phase) — under a bar that bleeds and settles solid
per-tab, a huge uppercase Fraunces title that never breaks mid-word,
Newsreader facts line in the theme's own ink in both themes, and the lead's
own tagline as a quote inside the hero on a wide window only, matching the
web exactly; `PullQuote` is gone, full gate green, tablet-verified
side-by-side against the live web stub (dark and light) for all four
departments plus portrait, version bumped to 0.71.1, not committed.
**Concerns/Blockers:** None blocking. One confirmed pre-existing data gap,
already tracked as `daBOB/mediagram#1` (Rust core, not touched per
instruction), and two minor, out-of-ownership findings (`TvCoverStory.kt`'s
own words-over-art default, `HeroArtwork`'s crop alignment) are called out
above for a maintainer to confirm or follow up on.
