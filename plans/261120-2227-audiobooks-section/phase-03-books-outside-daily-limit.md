# Phase 03 — Books outside the daily limit

**Context:** spec § Daily limit. User: listening does not count against a kid's limit, and a book opens after it is used up.
**Status:** todo. **Depends on:** Phase 01.

## Requirements

- A progress write for a `book` adds its step to `stats_titles` and the position as today, but **not** to `stats_days`. On the web server (`writeProgress`) and in the Rust core (`set_progress_counted`, used by Android). Every other kind is unchanged.
- Consequence, accepted: the per-day hours and streaks leave listening out; the per-title time keeps it.
- Opening a book is never refused for the time limit: web `mayOpen(set)`, Android `PlayGate.mayOpen(setId)`, Android cast receiver `refusalOf`.

## Files

- Modify: `web/src/state/stats-recorder.ts:99` `writeProgress(db, ticks, device, profileId, setId, rawAt, duration, countsToday = true)`; skip `UPSERT_DAY` when `countsToday` is false
- Modify: `web/src/state/store.ts:215` `setProgress(...)` and `web/src/state/routes.ts:121` to pass `countsToday: kindOf(setId) !== "book"`, with `kindOf` wired at `web/src/routes.ts:113` from `playableSet(db, id)?.kind`
- Modify: `web/public/lib/daily-limit-gate.js:57` `mayOpen(set)`: returns `true` first when `set?.kind === "book"`; caller `web/public/app.js:318` passes `set`
- Modify: `crates/mediagram-core/src/state/stats.rs:71` `set_progress_counted(…, counts_today: bool)`; `crates/mediagram-core/src/api/state.rs:110` `set_progress` decides `counts_today` from the set's kind in the installed catalog (the same store `listing.rs` reads); an unknown set counts, as today
- Modify: `android/feature/player/src/main/kotlin/DailyLimitGate.kt`: `interface PlayGate { fun applies(): Boolean; suspend fun mayOpen(setId: String): Boolean }`; `DailyLimitGate` gains `isBook: suspend (setId: String) -> Boolean` (from the catalog repository's current sets) and returns `true` for a book; callers `ui-common/.../LibraryPositions.kt:214`, `UpNextController.kt:194`, `LibraryPlayGate.kt:38` pass the id
- Modify: `android/feature/player/src/main/kotlin/CastReceiverViewModel.kt:117`: no time reason when the set is a book
- Test: `web/test/state-daily-limit.test.ts`, `web/test/daily-limit.test.ts`, `crates/mediagram-core/src/state/stats_tests.rs`, `android/feature/player/src/test/kotlin/{DailyLimitTest,LibraryPlayGateTest}.kt`

## Interfaces

- Produces: `mayOpen(set)` (web), `PlayGate.mayOpen(setId: String)` (Android). Phase 05/07 do not touch these.

## Steps

- [ ] **1: Failing tests**
  - Web server `state-daily-limit.test.ts`: `"listening to a book adds nothing to today"`: two progress writes 60 s apart for a `book` set → `readToday(...).watchedSeconds == 0`, while the title row's seconds are 60; the same for a `movie` set → 60.
  - Web client `daily-limit.test.ts`: `"a book opens at the limit"`: with today `out`, `mayOpen({ kind: "book" })` is `true` and `mayOpen({ kind: "movie" })` is `false`.
  - Rust `stats_tests.rs`: `a_book_counts_for_its_title_not_the_day`: `set_progress_counted(…, counts_today=false)` twice 60 s apart → `stats_days` sum 0, `stats_titles.seconds` 60.
  - Android `DailyLimitTest`: `aBookOpensPastTheLimit`: limit used up, `isBook = { true }` → `mayOpen("b1")` true; `isBook = { false }` → false. `CastReceiverHiddenSectionTest` (or beside it): a cast book past the limit is not refused for time.
- [ ] **2: Run, expect failures** (web `bun test test/state-daily-limit.test.ts test/daily-limit.test.ts`, `cargo test -p mediagram-core stats`, gradle `:feature:player:testDebugUnitTest`).
- [ ] **3: Implement** per Files.
- [ ] **4: All checks** green (web, Rust, Android `:feature:player` + `:ui-common`).
- [ ] **5: Commit** `feat: audiobooks sit outside a kid's daily limit`.

## Success criteria

The four new tests pass; existing daily-limit tests unchanged and green.

## Risks

An Android caller left passing nothing compiles only if a default is added: give `mayOpen` no default so the compiler finds every caller.
