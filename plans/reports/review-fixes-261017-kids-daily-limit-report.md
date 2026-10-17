# Review fixes: kids daily limit (2026-10-17)

## Changes
1. **High 1, refused session move leaves the session.**
   - Web: `app.js` `play()`/`openTitle` now resolve `false` on a daily-limit refusal (`true` otherwise); `openForTogether` returns that promise (`together-now.js`, `.d.ts`); `together.js` `openTitle` calls `leave(DAILY_LIMIT_WORDS.out)` on `false` while still `expecting` that set.
   - Android: `UpNextController.requestTitle(id, onRefused)` -> `refuse(refused)`; `TogetherController.openTitle` takes the refusal callback; `TogetherFollowing.apply` leaves with `DailyLimitWords.OUT` (guarded by generation/expecting). Joining passes a no-op (not in a session yet).
2. **High 2, pending Join dropped on refusal.**
   - Web: `takeJoin()` at the refusal in `app.js openTitle`.
   - Android: `LibraryPlayGate` takes `TogetherLive`, calls `takeJoin()` on refusal; `PlayerViewModel`'s `UpNextController` `onRefused` also calls `togetherLive.takeJoin()`. Fixtures (`LibraryFlowFixture`, `TvAppFixture`) pass their `together` fake.
3. **Low 3, no today query for non-limited profiles on progress PUT.** `readToday(..., limitedOnly)` / `state.today(id, limitedOnly)` return before the SUM when the profile has no limit; `leftToday` asks with `true` (204 as before). `/today` route keeps the full read.
4. **Low 4, gate timeout.** `daily-limit-gate.js` fetch uses `AbortSignal.timeout(1500)`; the existing catch lets the title open.

Also: `code-standards.test.ts` ceiling for `public/app.js` 701 -> 705 (documented in its header comment).

## Tests
- Web: `together-player.test.ts` (refused move: leaves, OUT words, one open, no further sends); `browser-application.test.ts` (refused Join not armed for next title; fails without the fix); `state-daily-limit.test.ts` (limitedOnly skips sum, grown-up 204).
- Android: `TogetherControllerTitleTest` (refused move leaves, asked once); `LibraryPlayGateTest` (pending join dropped); `TogetherRig` gained `refusing`.
- Green: `bun test` (3387), `bun run lint`, `bun run typecheck`; `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin`.

**Status:** DONE_WITH_CONCERNS
**Summary:** All four findings fixed on web and Android; Low 5/6 recorded under "Not fixed" in the review report. Nothing committed.
**Concerns:** No direct test for the in-player pending-join drop (`PlayerViewModel` wiring of `onRefused`; `UpNextController` itself only exposes the callback). Android `bun`-side "no query" is asserted by result (watched 0), not by counting queries.
