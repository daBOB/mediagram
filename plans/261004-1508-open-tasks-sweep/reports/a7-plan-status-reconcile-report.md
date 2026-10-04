# A7: plan status reconcile (2026-10-04)

Docs-only. Each plan.md got one `Status reconciled 2026-10-04:` line (before `## Phases`) and status cells fixed. Checkbox counts ignored. Phase files untouched.

| Plan | Old status | New status | Evidence |
|---|---|---|---|
| 260916-1936-web-player-bun-stack | in-progress (table all complete) | completed | transcode `2f942339`, LAN/remote `1e9208e4`, package `add25bd4`, docs `3f260e89` (2026-09-17); `web/src/transcode` exists |
| 260919-0034-android-foundation | phase 4 Blocked, 6 Not started | in-progress; phase 4 superseded, phase 6 remains | TV surface 0.64.0 `24264ecf` replaced phase 4; no `ByteTruthTest` / `export-part-digests.sh` on main |
| 260920-2025-android-system-menu | all 7 Not started | completed | merge `b5775ea4` (0.15.0, 2026-09-21); `mediagram-tmdb` crate exists |
| 260920-2128-powerful-video-player | no status; 05 not started | in-progress; phase 05 remains | no skip-intro/chapter code in web, crates, android; others done in git (`d4118e4a`, `85453033`) |
| 260920-2221-watch-state-across-devices | 02 "off by default, first push not run" | completed; 02 done (opt-in env) | Android first round took in web state (changelog 09-19-to-23); `MEDIAGRAM_SYNC_STATE` still gates web sync (`system-architecture.md:599`) |
| 260921-1751-android-external-cache | banner superseded, table Not started | superseded (table too) | successor `260925-2046`, merge `714c6a6a` (0.57.0) |
| 260923-1356-web-server-follows-channel-index | in progress, 05 "live pin pending" | completed | `29678015` (0.38.0); changelog 09-19-to-23 describes live install, SSE, covers fetched |
| 260923-2336-desloppify-quality | in-progress | unchanged, note added | merged `e3379446`; 3 milestones unticked; no later desloppify commits |
| 260924-1705-kids-profiles | all phases pending | completed (0.43.0) | `d06d22a8`, `22cb9b98`..`72eed933`; stub validation report |
| 260924-2239-android-tv-surface | phase 6 pending | completed | 0.64.0 `24264ecf`, merge `14f12be8`, docs `79881ce3` |
| 260925-1923-web-player-redesign | reopened | superseded | magazine redesign `9edda2f0` (0.55.0): "dark-first with a light paper variant", TMDB backdrops; then 0.62.0 |
| 260925-2245-android-magazine-parity | implemented on a branch | completed | `95f379ce`, merge `714c6a6a` (0.57.0); TV half 0.83.0 `4fcf8f91` |
| 260925-2245-channel-index-merge | phases 1-4 done, 5 not run | completed (5 superseded) | 0.56.0 changelog; 0.65.0 merge-first publish; 0.67.0 `e3a8db50` |
| 260926-1142-web-player-editorial-departments | 1-8 done, 9 follow-up | completed | 0.62.0 `cdd3471c`; phase 9 merge `93485f0e` (0.63.0) |
| 260927-0146-channel-index-module | phases done, no overall | completed | 0.67.0 `e3a8db50` |
| 260927-0302-upload-session-module | 04 "done, in review" | completed | 0.68.0 `3f2d906b` |
| 260927-0409-library-session-module | all todo | completed | 0.68.5 `c7cdeeff`; `library-session.js` exists |
| 260927-1303-address-module | done | completed (note) | 0.68.10 `658def54` |
| 260927-1344-plays-next-... | done | completed (note) | 0.68.11 `183619e1`, 0.68.12 `67b73b22` |
| 260927-1507-android-core-seam | done | completed (note) | 0.68.13 `f98dec21` |
| 260927-1636-android-catalog-reads | done | completed (note) | 0.68.14 `0a9bd249` |
| 260927-2117-android-film-preload | pending (phases done) | completed | 0.73.0 `9f0321a3` .. 0.75.2 |
| 260929-0215-tv-web-look-chrome-home-departments | pending (1-3 completed, 4 withdrawn) | completed | 0.82.0 `6d2dc721`, 0.83.0 `4fcf8f91`, 0.84.0 `4eff62cd` |
| 260928-0306-viewing-stats-web-and-android | completed | unchanged | already accurate |
| 261002-0213-android-self-update | completed | unchanged | already accurate |
| 260928-2042-anime-department | completed | unchanged, release versions noted | 0.76.0, 0.77.0, 0.77.1, 0.78.0 |

## Unverified
- 260923-2336-desloppify-quality: left in-progress; no evidence the last three milestones ran.
- 260923-1356 phase 05 live pin: marked done from changelog evidence, not from a recorded live-pin observation.
- 260924-1705-kids-profiles: marked completed; real-device and cross-device legs were never run (per its validation report).
- 260920-2221 phase 03 links to `../260922-2135-android-watch-state-sync`, which has no directory under plans/.
- 260925-2046-external-cache-volume-and-lan-chunk-server (not in scope) still says "done — awaiting merge" but merged as `714c6a6a`.
