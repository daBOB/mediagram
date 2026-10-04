# B3 pre-flight — `260928-0047` profile roles, PINs, kids age limits vs main (0.102.1)

2026-10-04, planner subagent, read-only. Design still right; nearly every version/line/size moved.

## Conflicts → rulings (lead adopts the planner's rulings)
| # | Plan assumes | Today | Ruling |
|---|---|---|---|
| 1 | web state schema v10→v11 | v11 = stats (`web/src/state/schema.ts:242`) | roles = **v12**, new `roles-schema.ts` (`ROLES_GROUP`) |
| 2 | core `VERSION` 6→7 | 7 = stats (`state/schema.rs:134`) | roles = **v8**; "repair before migrate" fix still needed (`state/mod.rs:112-118`) |
| 3 | private readers in `sync-record.ts`; `parseListRows` | `objectRow`/`text_`/`parseRows` exported (used by `stats-record.ts:46`); `isStamp` `:246` | move `asArray`,`objectRow`,`numberFromScalar`,`text_`,`isStamp` → `record-scalars.ts`; Kids `age` via `kidsRow` wrapping today's `listRow`; `RoleKeys` in `roles-record.ts`; `ProfileState extends StatsRows, RoleKeys` |
| 4 | role stamps `isFinite && > 0` | shipped bounds `isStamp` / `MAX_STAMP` (`record.rs:48`) | `claimedAt`, `pin.updatedAt` pass `isStamp`; `kidsAge.updatedAt` 0 or `isStamp`; core `record/roles_record.rs` |
| 5 | own writes `max(now, stored+1)` | clamped (`store.ts:291`, `rows.rs:121,128` `MAX_STAMP-1`) | clamped form for `kids_age`, PIN, a Kids mark's age/removal |
| 6 | web `merge.ts` 184 | 198/200 | `merged.ts` + re-export; precomputed `mergeRoles(records)` like `mergeStats` |
| 7 | core `merge.rs` split needed | already split (`merge/merged.rs`) | add `merge/roles.rs` like `merge/stats.rs`; fixture runner `roles_only` like `stats_only` |
| 8 | `routes.ts` 267; new `route-json.ts` | 243/243; `route-shared.ts` exists | `profiles-routes.ts` imports `route-shared` |
| 9 | `writeWorthSyncing` edit next to `/preferences → false` | that line is gone (preferences sync since 0.89.0) | add only the `/unlock` exclusion |
| 10 | core `state/mod.rs` 194 | 199 | move `open`+`migrate` (`:112-152`) → `state/open.rs` (holds the repair reorder); `PinWait` a `Mutex` field on `StateDb` |
| 11 | `api/state.rs` 180 | 198 | `profile_roles.rs` split still works |
| 12 | `create_profile` callers: 2 tests | + `tests/viewing_stats_api.rs`, `tests/achievements_surface.rs` | add both to phase 05 |
| 13 | `FakeCore` 496, no-op `setKids`; `CoreContract` 99 | `FakeCore` 588 + stateful `core/testing/FakeWatchState.kt`; `CoreContract` 450 (`createProfile` helper `:139`, `setKids(…, Boolean)` `:283-310`) | phase 06 extends `FakeWatchState.setKids(setId, age)`/`kidsFromSix`, age contract cases; helper → `createFirstAdmin`/`createGrownUp` + re-read `profiles()`; logic in `FakeProfiles.kt` (detekt `LargeClass` 600) |
| 14 | repo calls `:255/271/306` | `chosenProfile` (`WatchStateRepository.kt:53,406`); calls `:276/289/326`; `CatalogViewModel.kt:62-68` | `manage()`→`reload()` republishes `chosenProfile`; filter reads `chosenProfile.value?.kidsLimit` |
| 15 | phase 07 rewrites `ProfileGate`/`TvProfileGate` whole | 0.99.9 `BackHandler(canStay → stay)` (`ProfileGate.kt:32`, `TvProfileGate.kt:44`) | keep it; PIN pad/Manage register their own Back after it |
| 16 | "Android Settings shows no profile" (Open #3) | Settings › Profile since 0.90.0 (`ProfileSettingsViewModel.kt:81`; web `settings-page.js:106`) | both read "Name · Kids · FSK N" (parity default); Open #3 closed |
| 17 | `KidsEmpty` at `CatalogScreen.kt:90`, `TvCatalogBody.kt:49` | `:116`, `:56` (TV browse work in flight) | rebase on that work |

No collisions: sync keys `admin`,`kidsAge`,`parent`,`pin`,`ListRow.age`; `SYNC_FORMAT` stays 1; uniffi `ProfileOutcome.NotFound` nested; new file names; achievements read `profiles.kids`.

## Line caps
Web ratchet `web/test/code-standards.test.ts:51-80`; Rust hard 200 (`crates/mediagram/tests/code_standards.rs:9`, `*_tests.rs` exempt).
`sync-record.ts` 328/328 → `record-scalars.ts` · `schema.ts` 243/243 → `roles-schema.ts` (+2: raise to 245 with a dated sentence, precedent) · `merge.ts` 198/200 → `merged.ts` · `routes.ts` 243/243 → `profiles-routes.ts` · `store.ts` 791/800 → `profiles.ts` · `server.ts` 269, `app.js` 695, `shelf-view.js` 287, `watch-state.js` 502 at caps (line-neutral edits) · core `state/mod.rs` 199 → `state/open.rs` · `rows.rs` 199 → `rows/kids_marks.rs` · `api/mod.rs` 199 don't touch · `schema.rs` 173 ok.

## Order / parallelism
Web worktree 01→02→03 (02+03 pushed together). Core 04 after 01 merges (needs `profile-roles-merge.json`), parallel to 02/03. Split 05: 05a rules/PIN/wait/ops (no uniffi) pushable alone; 05b uniffi + bindings + `.so` with 06. 06 after B1 C/D merge **and** watch-state 03 (rewrites the fixtures 06 would edit) **and** watch-state 04 (contract green on tablet). 07 after 06; 08 last. Version-bump files always conflict between worktrees: re-bump on rebase, push one at a time.

## Would reverse shipped decisions (flagged; rulings: keep what shipped)
- Phase 07 Tasks 3/6 whole-file gate rewrites drop Back = "Stay as I am" (0.99.9 `a999faea`) → keep.
- Phase 01 Step 3.5.6 new `listRow` drops the 2^53−1 bound (0.100.1 `ffea35b2`); role keys unbounded → bound them.
- Contract §7 unclamped `stored+1` undoes 0.99.12 `d7319e44` → clamped.
- Phase 02 `writeWorthSyncing` anchored on the old `/preferences` exclusion → don't restore it (0.89.0).

## Questions for the user
1. Start web + core (01–05a) now, ahead of the Android test cleanup? (rec. yes — no shared files)
2. New kid's default limit FSK 6? (rec. yes; plan Open #1)
3. Household admin PIN separate from the web's Settings admin token? (rec. separate)
Ruled without asking (parity default / shipped contract): Settings › Profile "Name · Kids · FSK N" on both; Kids-mark stamp clamped on both. Procedure note: claim admin on the web right after the web release, before any Android release (Open #5).
