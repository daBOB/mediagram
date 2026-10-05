# Code review: TV 4K follow-ups (backdrops, Baseline Profile, frame-rate matching)

**Review date:** 2026-10-05.

**Scope:** `git diff 74073713`: three worktree merges plus the `PlayerFactory.kt` cleanup. 24 files, +674/−20. The reviewer read the code only and ran no builds.

**Plan:** `plans/261005-1655-tv-4k-backdrops-profile-frame-rate/`.

## Acceptance

| Criterion | Verdict |
|---|---|
| (a) Backdrop width: TV 1280, phone 780, tablet 1280 | Met (`BackdropWidth.kt`, `BackdropWidthTest`) |
| (b) Re-fetch narrower backdrops | Met as written, but it overwrote custom backdrops (#1) |
| (c) Baseline Profile wiring | Wired. No profile is committed yet. The custom `benchmark` type does not get the profile (#5) |
| (d) TV frame-rate matching | Logic met. The switch between titles is flawed (#2) |
| (e) Journey is navigate-only | Safe, but it never reaches the wall or a title page (#3) |

## Findings

"Lead verified" means I checked the finding against the code myself before deciding.

| # | Severity | Finding | Decision |
|---|---|---|---|
| 0 | Critical | Profile generation uninstalls the target app after the run: AGP's default, with no `leaveApksInstalledAfterRun` set. That would wipe the TV box's data. The generator has no TV guard | **Fix.** Lead verified: there is no such property in the repo |
| 1 | High | `resolve_with` (`api/store/resolve.rs:127`) copies table-held custom backdrops to `<key>.jpg` with no width record. `is_held` then re-downloads TMDB's image over them, and the web player still prefers the table | **Fix:** skip table-held keys in `plan_fetch`/`fetch_into`. Lead verified |
| 2 | Medium | Empty tracks on `setMediaItem` restore the mode, and the real tracks switch it again. That is two HDMI blanks per title change | **Fix:** ignore empty tracks |
| 3 | Medium | The journey stops on `TvMoviesDepartmentPage`, which is not the wall, so wall scrolling gets no coverage | **Fix:** go through the "All N films" pill |
| 4 | Medium | `LibraryUpdateCoordinator` re-reads artwork only when `postersFetched > 0` | **Fix:** also when `backdropsFetched > 0` |
| 5 | Medium | The custom `benchmark` type reads no `src/release` profile | **No code change.** Measure on `benchmarkRelease` |
| 6 | Low | Images are overwritten in place, so a partial JPEG is possible | **Fix:** temp file plus rename, as `write_from_conn` does |
| 7 | Low | A failed re-fetch that keeps the old file counts as "fetched" | **Fix:** fetched = held after − held before |
| 8 | Low | Portraits (desktop) and phones' w780 backdrops re-download once, because no record exists yet | **Accepted** as a one-time cost |
| 9 | Low / design | "Smallest multiple wins" switches 60→30 Hz even though 60 already fits | **Fix (lead decision):** keep the current mode when it fits. This was the lead's own plan rule, not a user decision |
| 10 | Nit | Import order in `UpdateModule.kt` | **Fix** |

## Checked and fine

- **Export package:** no `.width` files reach it (posters only).
- **Artwork listing:** every listing of the artwork directory filters to `.jpg`.
- **Public contracts unchanged:** UniFFI, `FetchReport` fields, the mlib package format.
- **The `isTelevision` move:** complete, with no duplicate left.
- **`testTagsAsResourceId` on `TvShell`:** harmless.
- **Display-mode effects:** they never overlap.
- **Style:** no plan references in code.

## Open questions

- Does Media3's MKV extractor fill in `Format.frameRate`? If not, MKV titles never match. Check on a 24 Hz-capable display.
