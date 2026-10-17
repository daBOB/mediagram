# Code review: feat/kids-daily-limit vs main (2026-10-17)

Scope: `git diff main..HEAD` (6 feature commits + `d6205538` version bump to 0.125.0), the generated uniffi bindings only for signatures. Every finding below was traced in code; none was run on a device or in a browser.

Clean (checked, no defect): TS/Rust parse, merge, tie-break, null kept, kid-only, absent never unsays (fixtures cover all of these); import SQL on both sides; export of a never-set limit; v13→v14 / v9→v10 migrations appended last; `MAX(now, MIN(stamp, LAST)+1)` stamping on both; set-limit uses exactly `set-kids-age` / `Action::SetKidsAge` + PIN, with 1..1440-or-null checked first on both servers and in FakeProfiles; every web open goes through `play()` → `openTitle` (the gate at `web/public/app.js:309` re-checks `pendingOpen` after the await); every Android library open goes through `LibraryPositions.openPlayer`, every in-player switch through `UpNextController.switchTo`; no path pauses, seeks or stops a playing title; the warning is once per title on both; the day is server-local (`localDay`) on the web and `LocalDate.now()` on Android, matching how each records stats; no UTC. TV: new dialog/step rows are keyed, focusRequesters sit on stable rows, the library pill has no focus node.

---

## HIGH 1: A refused session move leaves the kid in the session. The open is retried every 2 s, the kid's controls drive the others' title, and the kid's film ending ends their session

**Where**
- Web: `web/public/lib/playback/together.js:47-50` (2 s `check` → `apply(true)`), `:41-44`, `:61-65` (`openTitle` → `openForTogether`) → `app.js:286-294` `play()` → refused at `app.js:309`. The kid stays a member (`id` is kept).
- Android: `android/feature/player/src/main/kotlin/together/TogetherFollowing.kt:50-57` (`keepInStep`), `:65-75` (`apply`: "asked again on every check until it has opened") → `UpNextController.kt:135` `requestTitle` → `:186-200` `switchTo`/`refuse()` → `PlayerViewModel.kt:105` `tellLimit(OUT)`.

**Defect.** The out-of-age case leaves the session (`LEFT_NOT_HERE`). A daily-limit refusal does not, so the screen stays a member while it plays a different title:
1. **Retry loop.** `bring()` returns the session's set on every drift check while the kid's video is ready (`together-steps.js:28-31`, `TogetherSteps.kt:52-57`). So every 2 s the open is asked again. On the web each try costs a `GET /state` (`refreshState` in `play()`) and a `GET /today`, and resets the OUT words in `#together-note`. On Android each try costs a core read and calls `view.show(OUT, 8000)`. The 8 s hold is re-armed every 2 s, so the OUT pill never goes away, and every together notice ("X paused" etc.) is overwritten within 2 s.
2. **The kid's controls move the others.** `viewer()` applies the kid's own pause, seek and rate to the session's state (`together.js:123-137`, called from `player.js:392-394,936,941`; Android `TogetherController.viewer` via `ViewerRelay`). The kid pausing its own film pauses the sibling. The kid scrubbing to 40:00 in film A seeks the sibling's film B to 40:00.
3. **The kid's film ending ends their session.** `together.js:160-165` and `TogetherController.kt:119-124` (`onTitleEnded`) send `end` when the screen's own title has no next. `finished()` (`moves.js:39`) does not check the set.

**Scenario.** A kid and a sibling watch film A together. The sibling (no limit) opens film B, which moves the session. The kid is out, so it is refused and stays on A. From then on: the OUT pill is pinned; two GETs every 2 s (web); the kid's pause pauses B for the sibling; when A ends on the kid's screen, the sibling's session on B is ended.

**Fix.** Treat a refused session move as "may not follow": leave the session with the OUT words, as `LEFT_NOT_HERE` does.
- Web: have `play()` return `openTitle`'s promise, resolving `false` on refusal; let `openForTogether` pass it through. In `together.js` `openTitle(set)`: `.then((opened) => { if (!opened && expecting === set) leave(DAILY_LIMIT_WORDS.out); })`.
- Android: give `requestTitle` a refusal callback (or a result). On refusal, `TogetherController` calls `leave(DailyLimitWords.OUT)` instead of letting `refuse()` run its generic path.
- Add a test on each surface: a refused move, then two drift checks, ends with the screen out of the session and the opener asked once.

## HIGH 2: A refused open leaves a pending watch-together join armed; the next title opened on that device, by any profile, joins the old session and is pulled to its title

**Where**
- Web: `web/public/lib/together-now.js:121-126`. `openForTogether(setId, join)` sets `joinNext`, then calls the opener (`play`). The library's Join (`lib/catalog/together-join.js:46,57`) and the player's `joinLive` (`together.js:108`) both arm it. The only place that clears it is `takeJoin()`, inside `together.open()` (`together.js:186-189`), which runs only when a title actually opens. A refusal at `app.js:309` returns before that.
- Android: `CatalogViewModel.kt:190` `join(offer)` → `TogetherLive.joinOnOpen` (a `@Singleton`, `DataModule.kt:166-173`), then `TogetherJoin.kt:43-45` → `at.openPlayer` → refused in `LibraryPositions.kt:189-202`. In the player: `TogetherJoining.kt:37-38` → `requestTitle` → refused. It is consumed only in `TogetherController.open` (`TogetherController.kt:84-85`).

**Defect.** Before this branch, an open after `joinOnOpen` or `openForTogether(…, join)` always happened, so the join was always consumed at once. The gate adds a common refusal path that leaves it armed indefinitely. A profile switch does not clear it (the web does not reload; on Android the holder is app-wide).

**Scenario.** The TV box (shared) or a web tab: the kid is out and presses Join on a sibling's live session. The open is refused and the words show. Later, while that session is still live, a parent switches to their own profile on the same device and plays a film. `open()` takes the stale id and joins the session. The next check sees `own.set ≠ session.set` and opens the sibling's title over the parent's film. The parent's screen is now a member, so the parent's pause and seek drive the kids' session. The same happens to the kid after a parent raises the limit.

**Fix.** Discard the pending join when an open is refused.
- Web: in the refusal branch at `app.js:309`, call `takeJoin()` (import it from `together-now.js`).
- Android: give `LibraryPlayGate` the `TogetherLive` and call `takeJoin()` when `mayOpen()` is false. Do the same in `UpNextController.refuse()`, or in the together refusal callback from HIGH 1.
- Test: Join, then refuse, then open another title → no join request.

## LOW 3: Every progress PUT now runs the today-sum for every profile, grown-ups included

**Where:** `web/src/state/routes.ts:121` → `profiles-routes.ts:93-96` `leftToday` → `watched-today.ts:19-25`. `readToday` runs the profile lookup and then `SUM(stats_days…)` unconditionally; `leftToday` discards the result unless the profile is a limited kid.

**Defect.** Brief point 4 says non-kids get zero extra work on hot paths. Here every viewer's 10 s progress write, beacon included, gets two extra queries. They are indexed (PK prefix `profile_id, day`), so this is cheap, but it is not zero.

**Fix.** In `readToday`, or in a `leftToday`-only helper, return before the SUM when `kids = 0` or `daily_limit IS NULL`; the `/today` route can keep the full read.

## LOW 4: The web gate's fetch has no timeout, so a kid's open can hang on a slow server

**Where:** `web/public/lib/daily-limit-gate.js:62`, a bare `fetch(.../today)`, awaited before every kid open (with or without a limit). Compare `refreshState`'s `AbortSignal.timeout(1500)` (`watch-state.js:186-190`), which exists so an open never waits on a round trip.

**Scenario.** The server is busy in a long synchronous sync import (bun:sqlite blocks the event loop), or the network stalls. The kid's click opens nothing until the request returns or the browser gives up. The rule says a server that cannot be asked lets the title open.

**Fix.** `fetch(url, { signal: AbortSignal.timeout(1500) })`. The existing `catch` then lets the title open.

## LOW 5: An Android refusal permanently cancels up-next for the open title, including refusals from the together retry; the web does not

**Where:** `UpNextController.kt:196-200`. `refuse()` adds `session.openSetId` to `cancelledIds`, which lives as long as the controller (`:20-21`, `:44`). The web refusal only hides the panel until the next phase change (`player-next-title.js:126-129`).

**Scenario.** At OUT, the kid presses Next mid-episode, or the together loop from HIGH 1 refuses once. A parent then raises the limit and sync brings it in. When the episode ends, Android shows no up-next card and does not autoplay; the web would. This is a surface-parity divergence.

**Fix.** Keep a separate `refusedFor` that holds only for the countdown that was refused (cleared in `startTitle`, and when the limit changes), or drop the `cancelledIds` write and rely on `stopCountdown()` plus `ended`.

## LOW 6: Android library gate pushes the player onto whatever stack exists when the answer lands; the TV records a restore key for an open that never happened

**Where:** `android/ui-common/src/main/kotlin/ui/common/LibraryPositions.kt:189-205`. The push happens after `gate.mayOpen()`, which for a limited kid waits on `coreProvider.awaitCore()` (`WatchStateRepository.kt:340`, seconds on a cold start). `asking` silently drops every other tap meanwhile. TV: `TvLibrary.kt:136` writes `restore.opened(here, setId)` before `openPlayer`, even when the open is then refused (the agent flagged this).

**Scenario.** On a cold start the kid taps a Continue card, gets no response, presses Back or opens another page, and the player then opens over that page. On TV, after a refusal, a later return to Home can land focus on the refused card instead of where the kid came from.

**Fix.** Capture `raw` before the launch and push only if the stack is unchanged when the answer arrives. On TV, note the restore key only once the open goes through (e.g. an `onOpened` callback from `openPlayer`).

---

### Not defects / notes
- A `{minutes: N, updatedAt: 0}` record (no writer produces one) is imported by both surfaces. Afterwards Rust exports it (`exchange/roles.rs`: `minutes.is_some() || at > 0`) and the web does not (`roles-exchange.ts`: `at > 0`). Merges still converge, so this is harmless.
- The web warms the next episode's transcode in the last 30 s even when the kid is out (`warmNext`), which is wasted work. Android preloads likewise.
- Not run: browser and device behaviour, the real-core `CoreContract`. Findings come from code tracing.

**Counts:** High 2, Medium 0, Low 4.

## Not fixed
- **Low 5** (Android refusal cancels up-next for the open title): the countdown not restarting after a refusal mirrors a refused autoplay on the web, which also does not retry.
- **Low 6** (library gate pushes onto whatever stack exists; TV restore key): the core read is local and fast after start-up, so the window is a cold start only.
