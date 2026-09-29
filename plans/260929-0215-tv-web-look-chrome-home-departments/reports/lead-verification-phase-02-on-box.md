# Phase 02 on the box — lead verification

Box `192.168.0.35:5555`, benchmark build **0.83.0** from `feat/tv-web-look-home` (48be6cbc),
`compile -m speed`, profile "TV test", 2026-09-29 ~13:30–14:00.

## Verified

- The cover's title, tagline and facts line sit below the bar.
- Up from the cover's Watch now lands on the selected Home pill; Down returns to Watch now.
- A lower row's heading stays clear of the bar when its row takes focus (Continue Watching,
  Recently Added).
- Title-page round trip from a fresh launch: Recently Added posters 1, 2 and 4, 3/3 each —
  Back returns focus to the same poster.

## Failing — sent back to the phase agent

- **Leaving the player lands on the Home pill** (2/2: Hercules, Der Astronaut – Project Hail
  Mary, ~10 s each on "TV test"). The list stays scrolled, cover off-screen. 0.82.0 put focus on
  the played title's plate, now first in Continue Watching. Suspect: the row reorders while the
  player is open, and the restore gate gives up on the moved item.
- From that state, a title-page round trip also returned to the pill (1/1).
- A pill that receives restored focus is one Back from the rail and two from leaving the app —
  on one run the script's follow-on keys did exit to the Google TV launcher (nothing selected
  there).

## Found and fixed separately

- The cover read "2h 60m": `humanDuration` rounded only the leftover minutes. Same code on the
  web player and Android; fixed in both as 0.82.3, with the Android Movies department's hours
  now rounded like the web's.

## Method notes

- An earlier "restore failed 3/3" was a bad test: the first poster below the cover is a
  Continue Watching plate, whose OK starts playback, so the script was measuring the player.
  Round-trip scripts now require a 320×480 portrait poster and stop at the first mismatch.

## Final round — 2026-09-29 evening, build d6aeb50b (0.83.0, merged)

The agent's 68b938dd (arrival focus granted once per visit) stopped the pill but left focus on
the played title's *old slot*. Three further causes, each fixed and checked on the box:

- **Cards matched by position.** The home rows drew cards with `forEachIndexed` and no
  `key(...)`, so a played card moving to the front left focus on whatever moved into its place.
  Keyed by title in all four rows; the focused card also scrolls back into view when its row
  reorders (`TvKeepFocusedCardInView.kt`). Test `aRestoredCardKeepsTheRemoteWhenItsRowReordersUnderIt`
  fails without each half.
- **Same title in two bands.** Backrooms (new upload, also Trending) returned to Features when
  opened from Recently added. Home now remembers the band last focused (`rememberSaveable`) and
  `homeTargetOf` prefers it; `TvHomeTargetsTest.aKeyTwoBandsCarryGoesBackToTheBandTheRemoteWasIn`.
- **Chrome restorer hijacking arrival.** After the remote passed through the bar, Watch now →
  play → Back landed on the pill. Logs showed Home's request succeeding and the bar taking focus
  inside it: the `focusRestorer()` over bar + page restores into the first child with a
  remembered focus, and the bar always has one. Removed; the rail's Right now goes to the side it
  was left from (`lastInBar`). Not reproducible in Robolectric (the harness restores nothing in
  either version) — verified on the box only.
- **Cover under the bar when entered from below.** Whole cover brought into view on focus.

Box results (profile "TV test"):

| Check | Result |
|---|---|
| Continue card 2 → play → Back | played title first and focused, 6/6 (Green Mile, Lockere Geschäfte, Hercules) |
| Bar route → Watch now → play → Back | Watch now, 5/5 |
| Backrooms from Recently added / from Features | back to the same stop, 3/3 and 2/2 |
| Title round trip after a player return | same poster, 2/2 |
| Rail Right from Watch now / from the pill | same stop |
| Rail Right from a Continue card | Watch now (page's first stop), not the card — pre-existing gap: the old chrome sent it to the pill. Follow-up for phase 03/04 |
| Cover entered from below | fully at the top, heading clear of the bar |

Measurements (same method as the baseline):

| Repro | Run 1 | Run 2 | Run 3 | Baseline 0.81.1 |
|---|---|---|---|---|
| R1 poster → title → Back | 0 Davey, 0 skipped, 17.4 % | 16.7 % | 18.2 % | 8.3–12.5 % |
| R2 Watch now → 10 s → Back | 0, 0, 8.3 % | 8.2 % | 6.8 % | 2.4–6.9 % |

About one extra janky frame per transition (~23 frames per R1 run); no Davey or skipped-frame
lines. Phase 04 (Home kept alive) is where this is meant to come back down.

Played on "TV test": Hercules, Der Astronaut – Project Hail Mary, The Green Mile, Lockere
Geschäfte, All Inclusive, Projekt: Peacemaker — ~10 s each, several times.

## Open

- Rail Right should return to the exact plate it was left from (spec, phase 01 success criteria);
  it now lands on the page's first stop.
