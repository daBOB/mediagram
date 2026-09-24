# Validation — kids profiles (0.43.0)

Date: 2026-09-24. Branch desloppify/quality-20260923 at d06d22a.

## Automated
`scripts/check.sh` green at d06d22a: clippy -D warnings, cargo test --all (incl. line-limit), web lint + 1745 tests, Gradle testDebugUnitTest + lint.

## Web, stub harness (real player not started)
Stub: `web/public` served with stubbed `/api` — films rated 0, 6, 12, 16, 18, 12, one unrated, one unrated hand-marked (`/api/kids` → film-6), one unrated course lesson; profiles Andre (adult) and Mia (kids).

| Check | Result |
|---|---|
| Mia (kids): Movies count and list | 5 — FSK 0, 6, 12, 12, and the unrated hand-marked film; FSK 16/18, unmarked unrated and the course hidden (Tutorials 0) |
| Mia: search "film" (server returns all 9) | 5 results, same set |
| Header name opens "Who's watching?"; choose Andre | Movies 8, Tutorials 1; `/api/sets` fetch count unchanged (2 → 2): no re-download |
| New profile form | name field + "Kids profile — only FSK 12 and under" checkbox; created "Ben" as kids → POST carried kids:true; tile labelled KIDS |
| Phone width (390px) | tiles and KIDS labels lay out cleanly |
| Console errors (excluding stubbed 404s) | none |

Finding: the form's "Create" button renders as the browser's default grey button, out of keeping with the page's small-capital controls.

## Not yet verified
- Real Android device (kids profile created on the phone; filtered walls; Kids mark hidden).
- Cross-device: kids profile created on one device arriving labelled Kids on the other via the channel.
Both create a profile on the real account, which syncs to every device and cannot be removed by a sync — deferred to the user's go-ahead. Until then the cross-device leg is covered by the shared fixtures (web + Rust) and the import/merge unit tests.
