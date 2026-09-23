# Android: age ratings (FSK) on the Kids shelf and in the player

Status: **done** 2026-09-23 (0.38.0) — parity follow-up to
`260923-1618-fsk-age-ratings-for-kids` (web done there).

## Decided (user, carried over from the web plan)
- FSK 12 and below: on Kids by itself; a mark cannot take it off.
- Above 12: never on Kids; the Kids toggle cannot mark it.
- Unrated: on Kids only when marked by hand.
- The web player is the reference (`web/public/lib/age-rating.js`).

## Found while scouting
- `shows::get` selected `certification` unconditionally; the channel's
  current index is v6 (another machine, older build), so every title lookup
  on the phone failed there. Reads must treat the column as optional, as the
  web does (`readRows(db, "certification" | "NULL")`).
- `package::SUPPORTED_SCHEMA` became `[7]`, refusing every v6 package; a
  reader accepts `OLDEST_READABLE_SCHEMA..=SCHEMA_VERSION`.
- The rating rides the catalog listing (`SetSummary.fsk`), keyed by poster
  key exactly as `web/src/routes.ts` does, so every episode carries its
  show's rating and a series is judged by any one of them.

## Phases
| # | Phase | Status |
|---|-------|--------|
| 01 | Core: optional-column reads; `SetSummary.fsk`; v6 packages accepted; tests; bindings regenerated | done |
| 02 | Android: `MediaSet.fsk`; `AgeRating.kt` (port of `age-rating.js`); Kids wall = films + series + marked-by-hand; player toggle by verdict; FSK on title and series headers | done |
| 03 | Tests, device check, docs, version | done |

## Out of scope
- The other uploading machine still pushes v6; until it is upgraded, the
  phone shows no ratings (and falls back to marks only). User action.

## Validation
- `scripts/check.sh`: clippy, cargo test, bun (1261 pass), gradle test + lint — all pass.
- New: `mediagram-core/tests/age_ratings_in_catalog.rs` (rating per set; v6
  index still lists and describes), `AgeRatingTest`, Kids cases in
  `KeptShelvesTest`, rated-title cases in `PlayerMarksTest`, `PlayerKidsLabelTest`.
- Device (tablet, channel index v6): catalog loads, Justice League's
  description loads from the v6 index, Kids tab shows the web's empty text.
  Rated rendering is not seen on device until a v7 index is in the channel.
