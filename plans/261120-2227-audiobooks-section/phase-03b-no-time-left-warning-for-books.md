# Phase 03b — No "time left" warning while a book plays

**Context:** user, 2026-11-20 23:34: "fix the time-left warning for books too". Books never count toward a kid's daily limit and always open (Phase 03); the one-per-title "five minutes left" warning must not fire for a book either, on any surface.
**Status:** done. **Depends on:** Phase 03.

## Requirements

- Web: when the opened title is a `book`, its warning is not due: no "time left" notice from `opened()`, and none from `state.onToday` answers while that book is the open title. Opening a non-book title afterwards makes the warning due again exactly as today. Today `mayOpen(book)` returns before `latest = null`, so a warning could come from the previous title's standing — this must no longer reach the viewer for a book.
- Android: `DailyLimitWarning` never tells the warning for a book: `opened()` for a book leaves the title marked warned, so neither `opened()` nor `tick()` tells. A non-book opened afterwards is warned as today.
- Every other kind is unchanged.

## Files

- Modify: `web/public/lib/daily-limit-gate.js` — `export function opened(settled, set)`: `warned = set?.kind === "book"` before scheduling `warnOnce`; update its caller(s) (find with grep `opened(`) to pass the opened set
- Modify: `android/feature/player/src/main/kotlin/DailyLimitWarning.kt` — `fun opened(book: Boolean)`: `warned = book`, then `check()`; caller in `PlayerViewModel.kt` passes whether the opened set is a book (today `MediaSet.isBook`; a later task replaces it with `kind == Kind.AUDIOBOOK`)
- Modify: `docs/superpowers/specs/2026-11-20-audiobooks-design.md` § Daily limit — one line: no time-left warning while a book plays (user decision 2026-11-20)
- Add: this file as `plans/261120-2227-audiobooks-section/phase-03b-no-time-left-warning-for-books.md`, and a `03b` row in `plan.md`'s phase table
- Test: `web/test/daily-limit.test.ts` (or the gate's existing test), `android/feature/player/src/test/kotlin/` beside the existing `DailyLimitWarning` tests

## Steps

- [ ] **1: Failing tests**
  - Web `"a book is never warned about time left"`: with today in `warn` standing, `opened(Promise.resolve(), { kind: "book" })` then an `onToday` answer in `warn` → no notice; then `opened(Promise.resolve(), { kind: "movie" })` with `warn` → one notice.
  - Android `aBookIsNeverWarned`: gate in WARN, `opened(book = true)` then `tick()` → `tell` never called; then `opened(book = false)` → `tell(DailyLimitWords.WARNING)` once.
- [ ] **2: Run, expect failures.**
- [ ] **3: Implement** per Files.
- [ ] **4: Checks:** `cd web && bun test && bun run typecheck && bun run lint`; `cd android && ./gradlew :feature:player:testDebugUnitTest`.
- [ ] **5: Commit** `fix: no time-left warning while an audiobook plays` (code, tests, spec line, phase file, plan.md row).
