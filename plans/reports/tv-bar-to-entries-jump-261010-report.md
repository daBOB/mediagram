# TV: the hard jump from the bar down to the entries

2026-10-10 · branch `feat/tv-parity` · fix `7a9a7981`, released in the tree as 0.120.3 (not installed, not published)

## What the jump was

Down on a department pill moves focus to the page's content (`TvLibraryChrome.kt:225`, `downTarget = focus.contentFocus`). What happened next depended on the page. Measured in Robolectric at 960×540dp, with the bar's padding and the TV's own 30% pivot rule, one row per 16ms frame (page scrolled, px = dp):

| Page | Before | After |
|---|---|---|
| Anime, nothing underway (the screenshot) | f0 **441**, f1 424, f2 394, rest | f1 28, f2 60, f5 192, f10 280, f15 299, f20 306, f25 308, rest |
| Movies (row page) | f1 47, f2 128, f3 202, f4 259, f5 **298, dead stop** | f1 27, f2 58, f5 185, f10 270, f15 289, f20 295, f29 298, rest |
| Home | lands on the cover's Watch now, no scroll | unchanged |

**1. Anime with nothing underway jumped in zero frames.** The hero holds no stop (`headerHoldsNoStop = resumeCards.isEmpty()`, `TvAnimeDepartmentPage.kt:79`), so the wall's header takes the entry (`passesEntryOn`, `TvPassesEntryOn.kt:40`) and its `passOn` called `gridState.scrollToItem(plate)` (`TvWall.kt:174` at `ddd6be55`). That put the first plate at the top of the list in one frame, hero and "Series" heading gone. Then the plate's own scroll-into-view (the pivot) slid the page back down 47px over two frames. So the page jumped past the plate and came back.

**2. Every other entry was a five-frame spring that stopped dead.** Compose scrolls a newly focused stop in on `BringIntoViewSpec`'s default `spring()` (stiffness 1500, foundation 1.11.4). Its `ContentInViewNode` asks the page's rule again on every frame. `StaysPutWhenOnScreen` (`TvPage.kt:61-63`) answers 0 as soon as the stop is inside the safe band. So the spring, still heading for the 30% pivot, stopped at full speed the frame the row's foot crossed into the band: 298px in 5 frames, with the last frame still moving 39px. This covers Movies, Documentaries and Collections, and walls whose header holds a row (Anime or Series with Continue).

## The fix

`GlidesOnEntry` (`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvGlidesOnEntry.kt`) wraps the page's vertical list. It is used by `TvWall`, `TvMoviesDepartmentPage`, `TvDocumentariesDepartmentPage` and `TvCollectionsPage`.

- When focus comes into the list (`onFocusChanged`) it waits for the first stop that reports its bounds (`onFocusedBoundsChanged`). It then animates the list (`animateScrollBy`) over `TvEntryGlide`.
- **Where it ends:** where Compose's own scroll would have stopped. That is found by bisecting the page's rule for the first distance at which the rule answers 0, which is the safe band's edge. Movies ends at 298, as before. Anime ends at 308 coming up from below, where the old jump past the plate left it at 394. About 52dp of the hero's foot now stays under the bar, the same rest any Down move on that wall gets.
- **While the glide runs**, the list's own scroll-into-view (the wrapped rule) answers 0, so Compose's spring never starts against it.
- **When the remote moves on**, the focused stop reports `null`, which cancels the glide. The next stop then scrolls in the ordinary way: Down or Right mid-glide, and Up to the bar, where `toTopWhenLeftForTheBar` still brings the hero back.
- **The wall's header** (`passOn`, `TvWall.kt:174`) now focuses the plate at once when it is already laid out at the screen's foot, which it is under a department hero. It still scrolls first only for a header taller than the screen.
- **Unchanged:** focus targets, restoring (rail Right and Back restores keep their stops), and the rows' horizontal scrolling. Inside a page, rows read the wrapped rule, which passes straight through except during the glide.
- **Reduced motion:** Compose's animations follow Android's animator scale, so with animations off the glide lands at once. The TV has no `rememberReducedMotion`/`LocalReducedMotion` on this branch, and none was needed.

## The web's numbers

The web has no scripted scroll for this move: a browser scrolls its own page, and `theme.css:181` only turns smooth scrolling off under reduced motion. So the glide uses:

- **Curve:** `--ease` `cubic-bezier(0.32, 0.72, 0, 1)` (`web/public/styles/theme.css:120`), as `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)`.
- **Duration:** 420ms, the web's `main.turning { animation: page-in 420ms var(--ease) }` (`web/public/styles/shell.css:162`). That is the web's own movement for a page's content rising into place under the masthead. The web's other `--ease` movements are 520ms and longer, and `.reveal`'s 1100ms is too slow for a key press.

## Tests

- New: `TvGlidesOnEntryTest` (3 tests). Down from the bar on Anime and on Movies spreads the scroll over frames: under half the distance in the first 3 frames, short of the end at ~190ms, settled by 30 frames, and never moving back. Focus lands on the first plate or the Featured card. Up returns to the bar with the hero back at 0. A Down mid-glide hands over to the next line, fully on screen.
- Against the pre-fix code (the fix's main sources stashed), the Anime test fails on "jumped" and the Movies test on "over too soon".
- `LeanbackPivot` was moved to file level in `TvFranchisePageStateTest.kt` so both tests share it.
- `./gradlew :ui-tv:testDebugUnitTest`: 615 tests, 0 failures. `./gradlew :ui-tv:lintDebug`: passes, with no findings in the changed files.

## Not done / open

- Not checked on a device: no box, tablet or emulator walk. Check the first Down on the box after install, Anime and Movies.
- Up to the bar still uses `animateScrollToItem(0)`, Compose's spring. Should it glide back on the same 420ms ease? It was not asked for.
- Row-to-row moves on `TvPage` pages still use the spring that stops dead at the band's edge (cause 2 above). Should every vertical focus scroll glide, or only the entry? Only the entry was changed.
