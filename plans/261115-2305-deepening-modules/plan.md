# Deepening seven modules

From the architecture review of 2026-11-15 (candidates 1–7) and the grilling
that settled every decision with the user the same evening. Each phase turns
shallow modules into one deep module, tested through its own interface.

## How it lands (user's decisions)

- One phase per module, each its own commit with a patch bump of its line:
  media for 01–02, Spieltisch for 03–08.
- A refactor commit never changes what a viewer or player sees. A behaviour
  change is its own commit, so it can be judged alone.
- Replace, don't layer: once the new interface's tests cover a behaviour,
  tests that only reached the old shallow modules are deleted. Rule tests stay.
- Media phases go straight to `main` (no other session is in the Android code).
  Spieltisch phases run in the worktree
  `/home/andre/Workspace/spieltisch/mediagram-deepening` on
  `refactor/spieltisch-deepening` and merge into `main` after each phase,
  `scripts/check.sh` after each merge.
- Work runs through all phases without a check-in; it stops only for a test
  that can't be fixed at its cause, a surprise that reopens a decision, or a
  device need.
- Behaviour changes get a `docs/project-changelog.md` entry; refactors don't.

## Phases

| # | Phase | Line | Status |
|---|---|---|---|
| 01 | [Library update module](phase-01-library-update-module.md) | media | done 928831b6 (0.180.10) |
| 02 | [Library pages projection](phase-02-library-pages-projection.md) | media | done bfeed078 (0.180.11) |
| 03 | [Kids mode stops asking the general pack](phase-03-kids-mode-general-pack.md) | Spieltisch, behaviour | done 3369b756 (0.192.6) |
| 04 | [One catalogue entry per game](phase-04-game-catalogue-entry.md) | Spieltisch | done 123be90e (0.192.7) |
| 05 | [The Ask module](phase-05-ask-module.md) | Spieltisch | done 2af1246c (0.193.2) |
| 06 | [The card table](phase-06-card-table.md) | Spieltisch | done 2b2269fd (0.193.3, ring) + b6cc847e (0.193.4) |
| 07 | [The TV table shell](phase-07-tv-table-shell.md) | Spieltisch | refactor done 18b9de0d (0.193.5); Race/Cards switch waits for a funstation fps check |
| 08 | [The Room owns the end of a game](phase-08-room-owns-game-end.md) | Spieltisch | done d8f07854 (0.193.6) + 2530cd04 (0.193.7, behaviour) |

The two lines run side by side; within a line, phases run in this order
(07 before 08: the shell reads each game's ranking through a hook that 08
removes).

## Glossary changes made during the grilling (CONTEXT.md)

- **Library update** added: a player reading the newest channel index and
  fetching the artwork it lacks.
- **Ask** / **Grace** sharpened: a question put to every player at once is not
  an ask; it closes once every present player has answered, the house never
  answers it, and Grace is only for asks (user: keep the behaviour).

## Out of scope

- Candidate 8 (real core in JVM tests): reverses the Kotlin-only checkout
  decision; not taken up.
- Web watch state twinned with the core's: reverses the bun server decision.
- Marking kid-safe questions in the general pack: later content work.
- Pulling the web's department hero/lead selection into pure functions.

## Implementation log

- 01: LibraryUpdate replaces the coordinator; refresh returns its outcome. Start over and Sign out forget the installed library (found in review: the app-wide module would otherwise show the old account's library). Gradle unit tests of core/data, feature/{catalog,system,setup,player,games}, ui-tv, ui-mobile, hilt compile: pass.
- 02: libraryPagesOf in feature/catalog; fixture web/test/fixtures/library-pages/cases.json read by web and Kotlin. Picks follow the clock only on input changes (decided). Gradle + bun test 4045: pass.
- 03: kids mode blocks Zündstoff and the General quiz; Mixed/Films ask kids films only; lobby says when too few. 604 tests.
- 04: CatalogueEntry per game; recorded lobby/blocked/first-scene run identical before/after. Also brought host.rs back under 200 lines (03 had pushed it to 202).
- 05: ask.rs; GameCtx::house_at gone (absent_since only). Screens, buzzes, Grand Allee log, 2 800 seeded sim records byte-identical; sim 0.82 s. Letztes Wort (merged to main meanwhile by another session) moved onto it too. Glossary: several asks can be open at once (auction bidders).
- Main moved under us: Letztes Wort 0.193.0/0.193.1 (another session); versions continue from main's.
- 06: card_table.rs (seats, ring of 6, say / say_next, sort_hand, button) held by Mau-Mau, Schwimmen, Durak; dealt() constructors + card_game_driver.rs. 228 seeded games' TV and phone output identical (279 MB, same sha256).
- 07: client/src/tv/table/ (TableShell controller, TvTableShell, stage-host, tv-sound); wrappers 14-19 lines; CardsSound is cues on TvSound. Headless Chrome ran all three boards into the ceremony, no console errors. Open: Race/Cards adopting compileAsync + disposeScene needs a frame check on funstation. Found, not fixed: the Heimlauf and Grand Allee winner jingle is scheduled now minus the output lag, so at the box's 320-430 ms lag it is skipped.
- 08: Game reports seats() + ranking(); the Room builds Final, the next-round wait and TvView.ranking (no title: ADR 0002). Step 1 identical over 64 games x 16 kinds; step 2 (behaviour): "of" = players ranked, late joiners wait, Dreifelder's own wait wording gone.
- check.sh passes on main f0b157a5 with all of it.

