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
| 01 | [Library update module](phase-01-library-update-module.md) | media | pending |
| 02 | [Library pages projection](phase-02-library-pages-projection.md) | media | pending |
| 03 | [Kids mode stops asking the general pack](phase-03-kids-mode-general-pack.md) | Spieltisch, behaviour | pending |
| 04 | [One catalogue entry per game](phase-04-game-catalogue-entry.md) | Spieltisch | pending |
| 05 | [The Ask module](phase-05-ask-module.md) | Spieltisch | pending |
| 06 | [The card table](phase-06-card-table.md) | Spieltisch | pending |
| 07 | [The TV table shell](phase-07-tv-table-shell.md) | Spieltisch | pending |
| 08 | [The Room owns the end of a game](phase-08-room-owns-game-end.md) | Spieltisch | pending |

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

(filled per phase: commit, version, tests run, anything learned)
