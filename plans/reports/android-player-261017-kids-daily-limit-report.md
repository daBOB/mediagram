# Android part 2: kids daily limit in playback (2026-10-17)

## Where the gate sits (two doors, one rule)
No single place covers both a navigation from the library and a switch inside the player, so each has exactly one door, both asking `DailyLimitGate` (`feature/player/.../DailyLimitGate.kt`: kid + `dailyLimit` => `watchedToday` => `dailyLimitState`; core error => open).
1. **Library -> player: `LibraryPositions.openPlayer`** (`ui-common`). Every caller (phone shelves/title/list, TV frames, Join offers) goes through it, so no per-caller code, and it asks BEFORE pushing the frame (no player flash). Only a kid with a limit waits for the answer (coroutine); everyone else pushes synchronously as before. A second tap while asking is ignored. Refused: nothing navigates, `LibraryPlayGate` (Hilt VM) holds the OUT words 10 s; shown with the watch-together pill at the bottom of the library (phone `TogetherNoticePill`, TV `TvTogetherNotice`; non-focusable, so the remote does not move).
2. **In-player switch: `UpNextController.switchTo`**. Next/Play now, countdown end, episode-list pick, watch-together move all end here. Refused: no `pendingSwitch`, the title keeps playing (never stopped/paused/seeked), OUT words over the picture, and the title is put in `cancelledIds` so the countdown does not restart every few seconds (the standing Next button stays; pressing it asks again).
Not gated on purpose: rotation / process-restore reopening an already-open player frame (`sameTitle`), `PlayerViewModel.open` itself.

## Warning
`DailyLimitWarning`: once per opened title, on `WARN` only (as web) - checked at `open` (new title) and on every 10 s save tick (`PlayerSession` got an `onTick` callback, default no-op; `watchedToday` re-read, local SQLite). Skips everything when no kid limit applies. Words: `DailyLimitWords` (fixture-held).
Display: the existing watch-together notice (`TogetherUi.notice`, phone pill under the top bar, TV pill at top centre), which shows while controls are hidden. `TogetherView.show` got a hold parameter (8 s, default 2 s). Shared slot: a together notice inside those 8 s replaces the warning (last wins).

## Files
Main: `feature/player/` DailyLimitGate (new, incl. `PlayGate`), DailyLimitWarning (new), LibraryPlayGate (new), UpNextController, PlayerSession, PlayerViewModel, PlayerViewModelOpen, together/TogetherController, together/TogetherUi; `ui-common` LibraryPositions (gate + scope; `rememberLibraryPositions(gate)`); `ui-mobile` LibraryFlow; `ui-tv` TvLibrary.
Tests: `DailyLimitTest`, `LibraryPlayGateTest` (feature/player), `LibraryPositionsPlayGateTest` (ui-common); `LibraryFlowFixture`, `TvAppFixture` register the new VM.

## Coverage
- Gate refuses at out; opens at ok/warn; non-kid / kid without limit never asked (`watchedTodayAsked` empty); core error opens: `DailyLimitTest`.
- Library open: refused / allowed after answer / double tap asks once / no-limit pushes immediately: `LibraryPositionsPlayGateTest`. Notice 10 s: `LibraryPlayGateTest`.
- Next, episode pick, together move at out (3 paths in one test), countdown at out refused once and not restarted, playing title not stopped, ok switches, non-kid switches synchronously: `DailyLimitTest`.
- Warning at open once, on crossing tick once, again for a new title, none for free/plenty, ticker calls it every 10 s: `DailyLimitTest`.
- Not unit-tested: the pill rendering of the warning/OUT words (existing pill tests cover rendering); real core `watchedToday` (device).
`cd android && ./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` -> exit 0.

## Lead's device checks
Set a kid to 30 min, seed ~25 min watched (web has a harness; or watch), then:
Tablet: as the kid (a) open a title at ~26 min => plays, warning pill ~8 s, controls hidden still shows it, not again on next tick; (b) pass the limit while playing => keeps playing; at the end the countdown refuses once, words over the picture, no loop; Next button shows the words, title not interrupted; (c) back to library with out: tap a card, Resume, Next up => stays put, words pill bottom ~10 s; (d) a grown-up profile: no behaviour change.
TV box: same; with the pill showing, press D-pad around the library and the player: focus must not move/strand (pill has no focus node, but check Up/Down from a shelf while it shows); in the player, the OUT words over the picture during the up-next card.
Watch together: kid joins a session whose title is refused => stays on current, words show.

## Open questions / concerns
- Refused library open still runs the caller's side effects (e.g. TV `restore.opened` focus note for Home); harmless on return, but say if you want it undone.
- Warning shares the together-notice slot (above). Separate slot only if you see a clash.
- Together: a refused move leaves the kid's screen on its title while the session moved on (web does the same).
- Version/docs/commit untouched, as instructed.

**Status:** DONE_WITH_CONCERNS
**Summary:** Limit enforced in playback on phone/tablet/TV via two doors (library `openPlayer`, player `switchTo`) plus a once-per-title warning on open/10 s tick; all checks exit 0.
**Concerns:** Device focus walk on TV not done; library refusal keeps caller side effects; warning shares the together notice slot.
