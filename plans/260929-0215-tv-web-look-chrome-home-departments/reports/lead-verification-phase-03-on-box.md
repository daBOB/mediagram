# Phase 03 on the box — lead verification

Box `192.168.0.35:5555`, benchmark build **0.84.0**, `compile -m speed`, profile "TV test",
2026-09-30 00:30–02:30. Three rounds: the agent's build (4eff62cd), then two fix rounds
(568b95d2, 298a7104). D-pad only; OK pressed on pills and on one Anime film (title page).

## Found and fixed

| Round | Finding | Fix |
|---|---|---|
| 1 | Up from a page's top row went by geometry (Series card → Movies pill, far-right poster → search) | 1d64d9dd (lead): the page's Up rule never ran — a focus target keeps one `onExit`, the region's Left rule won; both rules now in the region's single handler. Broken since phase 01, hidden by the old heroes' explicit Up |
| 1 | Grid pages (Anime, Series, Tutorials) scrolled plates behind the bar | 568b95d2 / 298a7104: shared bar-clearance spec, provided around the whole grid |
| 1 | Anime "Series"/"Films" drawn as small labels; web uses `deptRow` | band heading, as Continue watching |
| 1 | Collections: Down from the pill did not enter the page; hero quote over the title | single `LazyColumn`; quote kept above the words or dropped |
| 2 | Up across the "Films" heading jumped to the last show (Mila Superstar) | `sectionCrossingsOf`: Up/Down between sections keep the column |
| 2 | Collections "Franchises" heading behind the bar after Down | arrival scroll honours the clearance |

## Verified on the final build (298a7104)

- Every pill: press keeps focus; Down enters; Up returns to that pill.
- Heroes: kicker, title, line ("912 films · 1.760 hours" — German grouping, as the web's
  `toLocaleString()`), art, bar blend.
- Movies: Featured → Acclaimed → Recently added; title round trip and rail Right return to the
  same poster (the row re-scrolls it first in view).
- Series / Tutorials: Continue your series|courses, category row (Trading), Every show grid
  clear of the bar going down and up.
- Anime: Continue watching, Series, Films; Dragonball Z ↔ Alice ↔ Porco Rosso keep column 2
  both ways, headings visible, plates clear of the bar; film → title page (Angel's Egg), Back
  returns to it.
- Documentaries: category rows (China, Geschichte, Politik), folder rows (Bin Laden, Die
  amerikanische Revolution), headings clear of the bar. OK not pressed (plates play).
- Collections: quote above the title, Down → James Bond with its heading clear, Up → pill.

## Not done

- Tablet side by side: tablet not connected.
- Home rail Right from a Continue card still lands on Watch now — phase 04.
- Movies/Documentaries use the same `scrollToItem` arrival as Collections did; no heading was
  seen behind the bar there, left as is.
