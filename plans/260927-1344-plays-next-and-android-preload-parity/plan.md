# What plays next, and Android preload parity (architecture review candidate G)

The review proposed a "playback start" module (play, openTitle, preloadAfter,
pendingOpen). Fact-finding on main 9be2a141 shrank it: "what plays next" was
already one block with one decider (`nextAfter`/`nextInQueue`), the preload
reused it, and `pendingOpen` has three writers (play, close, pagehide — not a
route change, as the review claimed). A module around them would be shallow.

## Decisions (user-confirmed 2026-09-27, "follow your recommendation")

| # | Decision |
|---|----------|
| Q1 | Small real fix, not the full module: the preload choice (with the next it must agree with) becomes a pure function next to the playback code, tested DOM-free; `app.js` keeps `play` and `pendingOpen` |
| Q2 | Keep the episodes-only rule (decided with the preload, df88713c: "Series episodes only; not lessons, not hand-built lists"); pin it, name the flood-limit reason |
| Q3 | Android matches the web (the reference, and the recorded decision): no preload from a list; the next two positions, only those that are episodes. Own commit after G, verified on tablet `caad49da` |
| Q4 | The early next-title byte warm stays as is (not measured to cost anything) |

## Phases
| Phase | Status |
|-------|--------|
| 01 Web: `lib/playback/plays-next.js` (`playsNext`, `requestPreload`), tests, `app.js` ceiling 772 → 742; 0.68.11 | done |
| 02 Android: `PlayerViewModelPreload` matches `playsNext`; unit tests; tablet check; 0.68.12 | done |

## Facts (Android, before)
- `PlayerViewModelPreload.kt:24-50` preloads when the open title is an episode, walking
  the run and skipping non-episodes until it has two; a hand-built list's run is walked too.
- The web takes the next two positions of the show (not of a list), and preloads nothing
  when the open title is not an episode.

## Review (2026-09-27)
- Web (0.68.11): code review found the behaviour identical to main across every
  case; applied its two notes (the first test now pins "no more than two"; the
  comment says the server keeps two, not refuses). A pre-existing quirk it
  noticed and left alone: a lesson or documentary whose collection shares a
  series' name matches the series first and loses Play next.
- Android (0.68.12): unit tests for `:feature:player`, `:feature:catalog`,
  `:ui-common`, `:ui-mobile`, `:ui-tv` pass; the two new rules were broken on
  purpose and each failed its test (hand-picked run preloads; a switch keeps a
  show's run).
- Tablet `caad49da`, test profile, native core rebuilt: the app launches; an
  episode opened from its show (30 Rock S3E2) preloads — `SeriesPreload:
  preload: Die verrückte Freundin held` (S3E3), the cache growing ~50 MB/15 s
  while paused. The list case was not reached on the device: the Collections
  page would not scroll past "+ New list" to the test list ("probe", S3E2 +
  S3E7, present in `state.db`), so it rests on the unit tests. Whether that
  scroll stop predates this change was not checked.
