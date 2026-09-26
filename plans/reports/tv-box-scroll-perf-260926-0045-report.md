# TV box scroll performance + three layout defects — 2026-09-26

Device: Skyworth UHD Google TV STB, armeabi-v7a, Android 14, 960x540dp, adb `192.168.0.35:5555`
(HP OMEN monitor, 3840x2160@60, UI surface 1920x1080). Profile **andre**, read-only browsing.
Branch `feat/android-tv-ui`. Screens, gfxinfo dumps and traces: scratchpad `perf/`.

## Commits

| Commit | What |
|---|---|
| `f3f32a1b` build(android): a minified benchmark build signed for on-device measurement | `benchmark` build type, profileable manifest, JNA/UniFFI keep rules |
| `89195f70` perf(android): compose a wall's plates without a subcomposition each | TvPlate initials, TvWall contentType + cache window |
| `b33c7ea9` fix(android): fit the profile picker's tiles inside the TV safe width | defect a + tests |
| `a5bf288c` fix(android): scroll the title page inside the overscan-safe band | defect b + test |

No version bump (as instructed). `./gradlew testDebugUnitTest lint :app:assembleDebug` pass; no new lint findings.

## 1. Benchmark build

`benchmark` = `initWith(release)`, `isMinifyEnabled`/`isShrinkResources = true`, `isDebuggable = false`,
debug signing, `matchingFallbacks += release`; `app/src/benchmark/AndroidManifest.xml` adds
`<profileable android:shell="true"/>`. `installBenchmark` over the debug install kept the Telegram
session (app opened signed in, library loaded, Home shows andre's Continue). ART state after install:
`speed-profile [install-dm]` (library baseline profiles).

**Keep rules added** (`app/proguard-rules.pro` §20): `-keep class com.sun.jna.** { *; }`,
`-keep class * implements com.sun.jna.** { *; }`, `-keep class uniffi.mediagram_core.** { *; }`,
`-dontwarn java.awt.**`. JNA binds native symbols by method name and reads Structure fields
reflectively. Nothing else was needed: Hilt, Media3 FFmpeg (`core/ffmpeg` already ships consumer
rules), serialization (test-only) all ran — library, browsing and search worked with no crash in
`logcat -b crash`. Playback on the benchmark build was not exercised beyond one accidental start
(see "Incident").

## Measurements

gfxinfo `reset` → keys → `dumpsys gfxinfo`. "Rapid" = 20× `input keyevent 20` + longpress burst
(~13 rows/s, like a held key). "Paced" = 12 presses 0.4 s apart. Benchmark rows are fresh install,
run 1 / 2 / 3 (run 1 always worst: glyph and composition caches cold).

| Screen | Build | frames | janky % | p50 | p90 | p99 |
|---|---|---|---|---|---|---|
| Movies wall, rapid | debug (before) | 66 / 85 | 98.5 / 94.1 | 350 / 250 | **500 / 350** | 750 / 500 |
| Movies wall, rapid | benchmark, original code | 206 / 234 / 227 | 10.2 / 3.9 / 6.2 | 22–24 | **38 / 34 / 32** | 57 / 46 / 61 |
| Movies wall, rapid | benchmark, fixed | 227 / 244 / 241 | 4.9 / 2.9 / 1.2 | 22–24 | **36 / 34 / 32** | 57 / 61 / 46 |
| Movies wall, paced | benchmark, original | 360 | 0.56 | – | 24 | 36 |
| Movies wall, paced | benchmark, fixed | 363 | **0.0** | – | **25** | 34 |
| Home rows (12 down) | debug | 31 | 48.4 | 42 | 97 | 109 |
| Home rows | benchmark orig / fixed | 44 / 43 | 4.6 / 2.3 | – | 24 / 14 | 30 / 46 |
| Season list (30 Rock S1, 18 down) | debug | 28 | 78.6 | 22 | 26 | 57 |
| Season list | benchmark orig / fixed | 29 / 28 | 0 / 0 | – | 11 / 11 | 13 / 12 |
| 30 Rock series page | debug | 31 | 6.5 | 16 | 29 | 38 |

(The Series tab has only 6 shows — no scroll — so a season list and a series page stand in for "a Series wall".)

UI-thread view (Perfetto, 16 rapid presses on Movies, main-thread `doFrame`):

| Build | doFrame >25 ms | worst doFrame | measureAndLayout total |
|---|---|---|---|
| benchmark, original (2 traces) | 13 / 10 | 60.5 / 30.6 ms | 571 / 485 ms |
| benchmark, fixed (3 traces) | 11 / 11 / 7 | 35.3 / 35.4 / 34.8 ms | 439 / 303 / 358 ms |

Target (p90 ≤ 33 ms for D-pad scrolling): **met for paced navigation (25 ms) and for Home/lists;
at the edge for a held-key burst (32–36 ms)**, before and after the code fixes alike.

## Root causes

1. **The debug build (main cause).** ~6 fps → ~45–60 fps from the build type alone: debuggable
   apps run Compose interpreted/JIT with no AOT (`cmd package compile` is capped at `verify`).
2. **A new line of six plates is composed in the frame that scrolls it on screen.** Per plate:
   a tv-material `Card`, a `BoxWithConstraints` **subcomposition** for the initials, and 2–3 text
   layouts in the variable Newsreader/Fraunces faces (~0.55 ms each on this CPU). One line ≈ 25–40 ms
   on the UI thread. Prefetch can't keep up with a held key (items enter every ~75 ms).
3. **gfxinfo p90 is inflated by buffer back-pressure, not app work.** RenderThread spends ~7 ms/frame
   in `dequeueBuffer` (full 3-buffer queue, 4K output); framestats with that wait removed:
   UI-thread p50 3.7 ms / p90 10 ms, work p90 28 ms. This is why p90 hovers at 32–38 ms even
   when only ~3% of frames miss their deadline.

Checked and not a cause: plates are keyed (`keyOf`); `rememberWatchMarks` is `remember(watch)`,
once per wall; focus-scale animation only recomposes the two plates changing focus; no screen-wide
recomposition per frame; Coil sizes decodes to the plate's constraints (and this box's channel
index carries no posters, so none are decoded).

## Fixes (`:ui-tv` only; no ViewModel/UiState, phone untouched)

- `TvPlate`: initials drawn with `drawWithCache` + `TextMeasurer` at the width-derived size instead
  of a `Text` inside `BoxWithConstraints` — no subcomposition or extra text node per plate; letters
  still in the merged semantics (existing `withNoPosterTheInitialsStandInForTheArt` test passes).
- `TvWall`: `contentType` per cell kind; `LazyLayoutCacheWindow(ahead = 320.dp, behind = 320.dp)`
  keeps a line composed ahead/behind in idle time.
- Effect: UI-thread layout work −25…40%, worst frame 60 → 35 ms, janky frames avg 6.8% → 3.0%
  (rapid), 0.56% → 0% (paced). p90 under a held key unchanged within noise (pipeline-bound, above).
- **Baseline profile: tried, not kept.** Wildcard `baseline-prof.txt` for `ui/tv`, `catalog`,
  `designsystem`, `model` (profile grew 13.6 → 14.5 KB); first-run-after-install p90 was still 40 ms,
  runs 2–3 unchanged — not measurably helpful, so removed.

## Layout defects

- **a. Profile picker, "New profile" cut at the right edge — fixed.** 5 tiles × 180dp + gaps = 964dp
  > 864dp safe width. The phone's picker shows every profile at once, so tiles now narrow to fit the
  safe width (`profileTileWidth`: 180dp max, 140dp floor; past the floor the LazyRow still scrolls,
  keeping the existing six-profile androidTest valid). On the box: all five tiles visible, right edge
  at x=1824 (= 1920 − 96). Tests: Robolectric at `w960dp-h540dp` (4 profiles + New profile inside
  the safe width) and a unit test of the width rule. Screens: `before-profile-picker.png`,
  `after-profile-picker.png`.
- **b. Title page synopsis cut at the bottom edge — fixed.** The scroll content already ended with
  the overscan padding (focusing the overview scrolled it to 1026px), but the viewport was the whole
  screen, so on open the overview ran into the bottom band and was cut by the panel. The page now
  scrolls inside the vertical safe band (vertical padding outside `verticalScroll`, horizontal inside
  so focused links can still grow). On the box, *Das ist das Ende*: on open the text stops at the safe
  line; Down shows the full synopsis ending above it. Test fails on the old page, passes now.
  Screens: `after-title-open.png`, `after-title-synopsis.png`. Residual: an overview taller than the
  whole band (~27+ lines) could still not be read to its end — none seen.
- **c. Home "Continue · 3" with 2 cards — not a TV bug, left unchanged.** Deliberate shared rule,
  from the web: `home-shelves.js` (`totals.continues: started.length` — "the figure has to agree with
  the page 'See all' opens"; cards drop titles already shown under Next up — "one title is one card
  on this page"). Ported as `feature/catalog` `underwayOf` (`continuesTotal = started.size`), read by
  `homeRowsOf`; the phone's `HomeScreen` prints the same `row.total`, so phone and web show the same
  count-vs-cards difference. TV matches both; changing it would be a shared-behaviour change for the
  user to decide.

## Incident

While looking for a long synopsis I pressed Centre on a **Search result** (Der 13te Krieger), which
starts playback directly. It played ~4 s on **andre** before Back stopped it. Continue on Home still
reads "Continue · 2" (the position is below the resume threshold), but a progress row for that title
may now exist on andre and sync. Nothing was marked or cleared.

## Box state

Debug build reinstalled (`pkgFlags=[ DEBUGGABLE … ]`), app opens signed in on andre's Home
(Continue · 2, focus on the first card).

## Unresolved

- Held-key p90 is bound by the display pipeline on this box; further gains would need lighter plates
  (e.g. a plate lighter than tv-material `Card`, which is a focus-treatment decision).
- Whether the ~4 s Der 13te Krieger position on andre should be cleared (needs a user decision).
