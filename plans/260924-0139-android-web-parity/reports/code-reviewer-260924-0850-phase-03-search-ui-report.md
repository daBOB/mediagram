# Phase 03 search screen and genre pages — review fixes

Findings tally as relayed by the coordinator: 1 Critical, 3 High, 5 Medium,
6 Low. C1 was confirmed on the tablet by the coordinator before this pass
started (a film on a genre page did nothing; back returned to the page the
viewer came from).

## C1 (Critical) — genre page cards did nothing

`LibraryFlowBranches.kt`'s `when` checked one slot per screen kind in a
fixed priority order (player > menu > search > genre > title > …). Opening
a title or a collection from a genre page set `titleId`/`collection`
alongside the still-set `genre`, but the genre branch was checked first and
always won — the genre page stayed on top no matter what was tapped, and
back from it returned to whatever the genre page itself was opened from,
skipping the title or series entirely.

**Fix.** `LibraryPositions` is now a real stack: one saved string, decoded
into frames of `(FrameKind, payload)`, `top` giving the frame `pop` removed
last. Every position (`titleId`, `genre`, `collection`, …) is derived by
scanning the stack for the most recent frame of its own kind rather than
read off a single shared slot — the change a slot-based stack-of-kinds
first draft still got wrong: a title opened from a genre page opened from
another title overwrote the outer title's id in the shared field, so
leaving the inner one twice landed on the wrong title (caught by
`LibraryPositionsBackStackTest`, which failed against that draft before
the payload-per-frame version fixed it). `LibraryBranches` now dispatches
on `top` alone; every `onOpenX` calls the matching `at.openX(...)`, which
pushes; every `onLeave` is `at::pop`.

Considered a `List<Frame>` saved with a custom `Saver`; kept a single
encoded `String` instead (control characters as separators, since no
title, genre or query is going to contain one) — it is what a `Bundle`
already carries across a killed process with no `Saver` to write, the same
reason every payload is a key rather than a tree.

Every existing hop was re-tested unchanged: catalog → collection → season →
title → player, a menu screen, search, a hand-built list
(`LibraryPositionsTest`). New: genre → title, genre → collection, a title
→ genre (the mirror case), search → player, and a menu screen → search,
all in `LibraryPositionsBackStackTest`, plus a test rebuilding
`LibraryPositions` from the same saved string to stand in for a killed
process.

## H1 — search showed old results when reopened

`SearchViewModel` is scoped above the screen (like every other ViewModel
`LibraryFlowBranches` reaches for) and kept its query and `Ready` rows
between visits, while `LibraryPositions` reset `search` to `""` on each
open — so reopening showed the previous visit's results until a fresh
keystroke.

**Fix.** `SearchBranch` calls `SearchViewModel.open(query)` once on entry.
`open` is blank-aware: a blank query calls the new `clear()`, which answers
`Idle` immediately rather than through the debounce. The debounce itself
changed from `debounce(200).mapLatest` to a single `flatMapLatest` that
answers `Idle` at once for a blank query and only delays for a real one —
one operator instead of two, and no separate "blank" path to fall out of
sync with the delayed one.

## H2 — a query restored after a killed process showed Idle

Same root cause as H1 — a fresh `SearchViewModel` instance starts blank
regardless of what `LibraryPositions` restored. The H1 fix covers it:
`open(query)` on entry syncs the ViewModel to whatever the position
restored, blank or not.

Tests: `SearchViewModelTest.reopeningWithABlankQueryDropsWhateverWasFoundBefore`,
`.reopeningWithARestoredQueryAnswersIt`, `.clearGoesIdleAtOnceRatherThanWaitingOutThePause`.

## H3 — search let a document reach the player

A search hit naming a document (`SearchRow.kt`) was clickable like any
other row and handed its id straight to `onPlay`, which the player cannot
open — the same failure mode `CollectionRows.kt`'s `ItemRow` already
guards against for one inside a course.

**Fix.** `isPlayable(set)` (`false` for `Kind.DOCUMENT`) gates the row's
`clickable` modifier and its text colour, mirroring `ItemRow` exactly,
including the reused `DOCUMENT_REASON` string (now `internal`, one
constant for both rows). Test: `SearchRowTest.aDocumentIsShownButNotPlayable`.

## M1 — the search icon did nothing on System, Settings or TMDB key

The old priority order meant the menu branch always won over search, so
tapping the icon set `search` but showed nothing until the menu screen was
later left, when search would appear as if from nowhere.

**Fix.** Falls out of the C1 stack rewrite: `openSearch()` always pushes a
new frame on top of whatever is showing, menu screen included, so it is
visible the instant it is tapped; back reveals the menu screen again,
back again the catalog. Test:
`LibraryPositionsBackStackTest.openingSearchFromAMenuScreenShowsItOverTheMenu`.

## M2 — a leading space or any keystroke that blanked the field closed it

`SearchScreen`'s `onValueChange` called `onBack()` the instant the field
read blank — including a single leading space, and including a field a
viewer had only just opened.

**Fix.** Picked the simpler of the two options the review offered rather
than reproducing the web's own mechanism (trim, then leave only once the
debounce settles on nothing typed): a blank field never leaves on its own.
It shows the empty state; leaving is only ever the bar's back arrow or the
system gesture, both already wired to the same `pop()` every other screen
uses. Recorded in the phase file as a deliberate difference — a touch
keyboard has no pause to wait out the way the web's address bar does, and
a screen a still-held backspace can make vanish is worse than one that
always waits to be asked.

## M3 — every settled query rebuilt the whole catalog on the main thread

`SearchViewModel` joined hits by calling `CatalogRepository.sets()`, which
maps every `SetSummary` to a `MediaSet` and calls the core's synchronous
`posterPath` — a disk check — once per set, on every settled keystroke.

**Fix.** The join moved out of the ViewModel entirely.
`SearchUiState.Ready` now carries the core's own `SearchHit` list;
`searchRowsOf(hits, catalogState)` (pure, `feature/catalog`) joins them
against the `CatalogUiState` the screen already holds — the same shelves
already built, no second read of the core, no re-checked poster.
`SearchViewModel` only ever calls `CatalogRepository.search`. Test:
`SearchRowsOfTest` (a hit joins, a hit naming a gone set is dropped, a
still-loading catalog joins nothing).

## M4 — scroll position lost after search → play → back

Confirmed as a limitation the app already has everywhere a list is
scrolled before playing something, not specific to search. Recorded in
the phase file rather than fixed here.

## M5 — coming back from the player popped the keyboard over the results

`SearchScreen` always requested focus and asked to show the keyboard on
entry, including when re-entering with an already-answered, non-empty
query.

**Fix.** The `LaunchedEffect` that requests focus and shows the keyboard
now runs only when the entry query is blank — the fresh-open case the web
also autofocuses for — not on every re-entry.

## L1 — the search icon had nothing to do on the search screen itself

`AppChrome.kt`'s own doc comment said as much without the code matching
it. **Fix.** `showsSearchAction(destination)` (pure, tested in
`AppChromeTest`) hides the action on `Destination.Search`; the doc comment
now says so.

## L2 — search header text, location line for documents, genre chips on episode/lesson pages

Recorded in the phase file as deliberate: the location line groups a
document with a lesson (same folder-first reasoning as `CollectionRows`),
and genre chips only ever appear on a film's own page or a show's/course's
header, never per-episode — the web has no per-episode genre link either.

## L4 — the genre page said "nothing tagged" while the catalog was still loading

`GenreBranch` built its shelf from `(catalogState as? Ready)?.shelves`, so
a restore landing here before the catalog finished loading read as an
empty, correctly-tagged genre rather than "not yet answered."

**Fix.** `GenreBranch` shows "Loading your library…" while `catalogState`
is not yet `Ready`, the same wording `CatalogScreen` uses for the same
wait, before asking `genreShelf` anything.

## L5 — a doc comment ended up detached from what it documented

`LibraryBranch`'s doc comment sat above `ResolvedPositions` after an
earlier split. Moot after the C1 rewrite: `LibraryBranch` moved to
`LibraryFlowBranches.kt`, its only caller, with its comment directly above
it; `ResolvedPositions` now has its own file, `LibraryResolve.kt`.

## L6 — a failed search round crashed rather than showing a message

`SearchViewModel.search` let any exception from `CatalogRepository.search`
propagate out of the `flatMapLatest` pipeline uncaught.

**Fix.** Catches everything but `CancellationException` (rethrown — a
cancelled query belongs to a coroutine a newer keystroke already replaced,
not a failure) and answers `SearchUiState.Failed(message)`, which
`SearchResults` shows as "Search failed: …", the same wording the web's
own `viewSearch` catch block uses. Test:
`SearchViewModelTest.aFailedRoundIsShownRatherThanThrown`.

## Files touched by this pass

`android/ui-mobile/src/main/kotlin/{LibraryPositions,LibraryFlowBranches,
LibraryFlow,AppChrome,SearchScreen,SearchRow,GenreScreen,CollectionRows}.kt`,
new `LibraryResolve.kt`; `android/feature/catalog/src/main/kotlin/
{SearchUiState,SearchViewModel}.kt`; tests: new
`LibraryPositionsBackStackTest.kt`, `SearchRowsOfTest.kt`, rewritten
`LibraryPositionsTest.kt` (split so both stay under 200 lines),
`SearchViewModelTest.kt`, extended `AppChromeTest.kt`, `SearchRowTest.kt`,
`FakeCatalogRepository.kt` (a `searchThrows` fake for L6).

---

## Re-review (same day) — 2 new issues from the stack rewrite

C1, H1–H3, M1, M3, M5 and L6 above were confirmed fixed; the coordinator
also confirmed on the tablet that genre → film, genre → series, season
and back chains each pop one frame correctly. Two issues came from the
stack rewrite itself.

### N1 (High, regression) — a top frame whose key doesn't resolve drew nothing

`LibraryFlowBranches.kt`'s `FrameKind.TITLE`/`SEASON`/`COLLECTION`/`LIST`
branches each did `resolved.x ?: return` — a bare `return` out of the
whole composable, drawing no scaffold, no bar and no back handler, so the
system back gesture finished the Activity instead of leaving the screen.
Three real paths hit it: a restore landing on one of these while the
catalog is still `Loading` (and forever if it settles on `Failed` or
`Empty`, offline being the ordinary case); a menu screen left over a
title deleted elsewhere after "library changed"; a hand-built list
removed on another device while open here.

**Fix.** `resolveFrame(value, catalogReady)` (pure,
`LibraryResolvedBranch.kt`) turns the lookup into one of three outcomes —
`Resolved`, `Loading`, `Stale` — and `ResolvedBranch` renders each: the
resolved value's own screen; the chrome with "Loading your library…"
under it while the catalog is not yet `Ready`; or, once it is `Ready` and
the key still doesn't resolve, a `LaunchedEffect` that pops the stale
frame so the viewer lands on whatever is next. The stale comment on the
`null` branch (which no longer described anything happening there) was
cut. Tests: `FrameResolutionTest`, pure, one case per outcome — reading
`Loading` for an unresolved key before the catalog answers and `Stale`
once it has is the dispatch-level coverage the report asked for; a
`Composable`-level test would need test infra this project does not have.

### N2 (Medium) — decode crashed on a malformed saved string

A pasted query containing `\u001E` (the frame separator) split into a
token `decode` could not destructure, throwing `IndexOutOfBoundsException`
on every recomposition afterward; `FrameKind.valueOf` and
`MenuScreen.valueOf` threw the same way on a name from an older or newer
build's own saved string.

**Fix.** `typeSearch` strips ISO control characters
(`Character.isISOControl`) before saving, so neither separator can reach
the stack from typing or pasting in the first place. `decode` is
independently tolerant regardless: a token with no field separator, or a
kind `FrameKind.valueOf` does not recognise, is dropped via `mapNotNull`
rather than thrown; `menuScreen`'s own `MenuScreen.valueOf` is wrapped the
same way. Tests: `LibraryPositionsEncodingTest` — a string with no
separator, an unknown frame kind, an unknown menu screen name, and typing
`"a\u001Eb\u001Fc"` into an open search field lands as `"abc"`.

### L-a (the H1 leftover) — old results still flashed for a frame on reopen

`SearchViewModel.state` was a `StateFlow` a new collector reads
synchronously the instant it subscribes; `SearchBranch`'s
`LaunchedEffect(Unit) { viewModel.open(query) }` runs one frame later, so
the first frame after reopening search still showed whatever a previous,
unrelated visit had last found.

**Fix.** Two changes, not one option from the report alone, because
`replayExpirationMillis` only helps once the upstream has actually
stopped (`stopTimeoutMillis` after the last subscriber, 5 s) — a quick
reopen never reaches that. `SearchViewModel` now writes a plain
`MutableStateFlow` directly rather than deriving `state` through
`flatMapLatest`, so `open` resets it to `Idle` synchronously, before
asking anything. `SearchBranch` additionally gates its very first frame
on an `opened` flag that only flips once that `LaunchedEffect` has
actually run, so the raw collected value — stale or not — is never read
before then. Tests:
`SearchViewModelTest.openClearsAPreviousAnswerBeforeAskingAgain` (asserts
the reset with no time advanced, proving it is not the eventual answer to
the new query); the `opened` flag itself is `SearchBranch`-local
Compose state with no test infra to reach it, the same limitation N1's
`Composable` half has.

### L-b — a restored query showed "nothing found" before the catalog loaded

`SearchResults` computed `rows` from `searchRowsOf`, which answers empty
while the catalog is not yet `Ready` — read as "nothing found" rather
than "not yet asked".

**Fix.** `searchResultsView(catalogReady, rowsEmpty)` (pure,
`SearchRow.kt`) is checked before rendering: loading first, then empty,
then the rows — the same order `GenreBranch` already used for the same
reason. Tests: `SearchRowTest`, one case per outcome, including "an
answer already in hand but the catalog still not `Ready`" reading as
loading rather than as a result.

### L-c — no depth limit on the stack

No action, as the report itself concluded: the web's own history grows
the same way.
