# Episode label parity

Stored `sets.episode` is TEXT holding the uploader's JSON (`mlib-spec` `Episode`, untagged u32 | [u32;2]): `"7"` or `"[3,4]"`. Real library: 7711 text rows; only shapes are plain integers and `[a,b]` ranges (e.g. `[1,2]`, `[17,18]`). No padded or `a-b` strings, no episode 0 (season 0 exists: `ep|0|1`). Android core parses with serde, so non-JSON/padded/`a-b` reads as unnumbered.

| stored (kind ep, season 2) | web before | Android | web after |
|---|---|---|---|
| `7` | S2E7 | S2E7 | S2E7 |
| `0` | S2E0 | S2E0 | S2E0 |
| `[3,4]` | S2E[3,4] (wrong) | S2E3-4 | S2E3-4 |
| `[5,5]` | S2E[5,5] | S2E5 | S2E5 |
| `[3,4]` tut | `[3,4]` | 3-4 | 3-4 |
| `04`, `4-5`, junk | raw string | "" | "" |

Same defect in web `episodeShort` (`S3 E[15,16]`); Android prints first number. Web now matches.
Fix on web (Android was right; web printed raw JSON): new `web/public/lib/episode-label.js`, re-exported from format.js (format.js was near the 200-line cap).
Tests: web format.test.ts, series-resume.test.ts; Android core:model EpisodeLabelTest.
