# Age ratings (FSK) decide what the Kids shelf may hold

Status: **web done** 2026-09-23 — waits on the uploading machine running `mediagram metadata`; Android follow-up

## Decided (user)
- "Age verification" = **TMDB age ratings**, German FSK (0/6/12/16/18).
- **FSK 12 and below is kid-safe**: such titles are on the Kids shelf
  automatically, beside the ones marked by hand.
- **Unrated is not kid-safe**: it reaches Kids only when marked by hand.
- Marking a title rated above the limit (FSK 16/18) as Kids is refused.
- Not decided here, not built: a locked child profile / PIN.

## Constraints found while scouting
- The TMDB cache key includes the query: adding `release_dates` to the
  details request would invalidate ~540 cached payloads. The rating is its
  **own request** — `/movie/{id}/release_dates`, `/tv/{id}/content_ratings` —
  cached on its own.
- Uploads already record details per title (`upload/plan_set.rs:110`), so the
  rating rides the same step: every new upload carries it in the index, and
  the index reaches the web player (channel snapshot) and Android.
- A new `shows` column is schema **v7**. The web must keep reading **v6**:
  the channel snapshots come from the other machine, which stays on v6 until
  it is upgraded. The web knows v7, accepts v6+, reads the rating only where
  the column exists.

## Phases
| # | Phase | Status |
|---|-------|--------|
| 01 | Rust: `certification()` in mediagram-tmdb; `TitleDetailsRow.certification`; schema v7 column; core upsert/get; recorded by `add` and `metadata` | done |
| 02 | Web: rows carry `fsk`; film page + series header show it; Kids shelf = marked ∪ FSK ≤ 12; marking FSK 16/18 refused | done |
| 03 | Fill this machine's index with `mediagram metadata`; browser check; tests; docs; version | done |

## Rollout (user action)
Upgrade `mediagram` on the uploading machine and run `mediagram metadata`
once there; the next `push-index` carries every rating. From then on each
upload records its own.

## Follow-ups
- Android: show FSK, same Kids rule (Surface Parity) — `260923-2021-android-fsk-age-ratings`.
