# B3 phase 04: core roles schema, sync keys, merge, export/import

2026-10-04. Written by the fullstack-developer subagent in worktree `worktree-agent-a7f7f734036079ca8`, fast-forwarded to main `0d539026` (0.105.1).
Plan: `260928-0047-profile-roles-pins-kids-age-limits`, phase 04. Built to the pre-flight rulings and the 2026-10-04 contract amendments.

**Status:** done (pending merge). Not pushed. Versions not bumped; the lead bumps at merge.

## Commits
| Hash | What |
|---|---|
| `47ed8f34` | refactor(core): pure moves with no behaviour change. `open` and `migrate` move out of `state/mod.rs` (199 → 160 lines) into `state/open.rs`. Kids marks move out of `rows.rs` (199 → 174) into `rows/kids_marks.rs`. Tests: 632 green before and after. |
| `3a8eb094` | feat(core): schema v8, role keys, merge, exchange, Kids-mark age, tests, fixture runners. |
| (next) | docs(plan): phase-04 status and this report. |

## Test-first record
- **RED 1 (compile):** all tests were written first. That includes the fixture runners for `profile-roles-merge.json` and `profile-roles-record-parse.json`. `cargo test --no-run` then failed with 35 errors in the lib tests. They included: no field `age` on `ListRow` (×10), no `kids_from_six`, no `kids_row`, no field `roles` on `MergedProfile`, and `set_kids` expecting `bool` (×15 mismatched types).
- **RED 2 (runtime):** schema v8, types and parse were in, with only the scaffolding needed to compile. Results: **lib 500 pass / 19 fail**, fixtures 9 pass / 1 fail.
  - All 8 exchange-role tests failed, plus 4 Kids-age exchange tests, 5 Kids-mark write tests and the three-device merge test.
  - `profile_roles_merge_fixtures_match_the_web_in_both_orders` failed on its first case: "a limit changed on two devices: the newer change wins".
  - `a_version_three_file_without_kids_gains_the_column_on_open` failed with `no such column: kids`. This is the predicted failure: the v8 `UPDATE … WHERE kids = 1` ran before the repair.
  - The parse fixture already passed at this point, because parse was in.
- **GREEN:**
  - `cargo test -p mediagram-core`: **660 pass / 0 fail** (baseline 632, plus 28 new tests).
  - Both new fixture runners run real cases, with no "skipping": 20 merge and 11 parse cases.
  - Every existing shared fixture passes unchanged: `record-parse`, `merge`, `lists-merge`, `stats-*`, `achievements`.
  - `cargo test --all`: **1716 pass / 0 fail** (4 ignored).
  - `cargo clippy --all-targets --all-features -- -D warnings`: clean.
  - `cargo test -p mediagram --test code_standards`: 2/2. The largest touched file is `schema.rs` at 191 lines.
- **Kotlin bindings unchanged.** Bindings were generated with `uniffi-bindgen` from the host `libmediagram_core.so`. After the same trailing-blank strip the script applies, they are byte-identical to `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`. The Android `.so` was not cross-compiled here; rebuild it at release.

## What was built
- **Schema v8** (`schema.rs`): contract §6 statements verbatim, the web's v12. The migration runs in one transaction through the existing runner and is idempotent: reopening is a no-op. **Repair before migrate** (`open.rs`, `repair.rs`): the `kids` repair now runs first and only at `user_version >= 3`. Below 3 the v3 migration adds the column, and an earlier `ALTER` would make it fail as a duplicate.
- **Wire** (`record/roles_record.rs`):
  - `ProfileRoles {admin, kids_age, parent, pin}` is `#[serde(flatten)]` on `ProfileState` and `MergedProfile`.
  - Each key is parsed on its own: `js_number` coercion, then the shipped `is_stamp`/`MAX_STAMP` bound. `kidsAge.updatedAt` may be `0` or a stamp.
  - The age is the JSON number 6 or 12 only, which matches JS `===` (`6.0` is accepted, `"6"` is not). Hash and salt must be lowercase hex of length 64 and 32.
  - `ListRow.age: Option<u8>` is read only by `kids_row`: the number 6, live marks only, so a tombstone's age is stripped at parse. Watchlist and editor's-choice rows never carry it.
- **Merge** (`merge/roles.rs`, `tie_break.rs`):
  - `roles::merge(records)` is a precomputed map like the web's `mergeRoles`.
  - Limits and PINs go through `keep`. A kid with no stated limit merges to `{12, 0}`.
  - `parent` is normalised and decided by device id, and only kids carry it. A PIN is carried only by grown-ups.
  - `admin` goes to the earliest claim among grown-ups only, so kids are skipped (contract §8). Ties go to the smaller name.
  - `keep_ranked` asks `kids_mark_rank` (live and `age == 6`) before the device id. This is the web's `keep(…, rank)`.
- **Kids marks** (`rows/kids_marks.rs`):
  - `set_kids(conn, set_id, Option<u8>)` and `kids_from_six`.
  - Every change of age is stamped `MAX(now, MIN(stored, MAX_STAMP−1) + 1)`, **6→12 included** (the port note), and so is a re-mark after a removal. A removal is clamped the same way, past the mark it takes off.
  - Marking again at the same age changes nothing.
- **Exchange:**
  - `export_kids` sends `age: 6` on live marks only.
  - `import_kids` takes an equal-time row that differs: removed where this device's mark is live, or a different live age. A tombstone's leftover age is never compared.
  - The age is written with the row, and an imported removal clears it, as the web does.
  - `exchange/roles.rs`: export follows §7. Import is corrective (newer, or equal and different). `parent_id` is set only while NULL, matched by normalised name, never to itself. The merged admin is set here and cleared everywhere else, and a merge that names no admin changes nothing.
  - The kids upgrade is `kids_age = COALESCE(kids_age, 12)`. Role import runs after the profile loop.
- **API:** the `api/state.rs` `set_kids` body changed to `marked.then_some(12)`. Its signature and docstring are unchanged.

## Compatibility story (verified against the real 0.105.1 core)
This was a one-off probe, run in the scratchpad and not committed. `git archive main` of the core crates was built with a probe test inside the old crate, and the new core ran a temporary test that was deleted before commit.
1. **New core writes** a document with an admin and a PIN on André, Mia as a kid at FSK 6 with parent André, and Kids mark `x` "from 6".
2. **The 0.105.1 core reads it.** It accepts the document: its `parse_record` reads only the keys it names, so the role keys and `age` are dropped. Its own Mia stays a kid, André is created and `x` arrives, which it reads as from 12. That was **2 changes**, and its second round changed **0**. Its echo carries `x` at the same `updatedAt` without `age`, from device `zz-old-tv`, which sorts after `laptop`. That device order is the trap case.
3. **The new core takes the echo back. 0 changes in both rounds:** FSK 6, the admin, the PIN and "from 6" on `x` all survive. The Kids-mark rank keeps the from-6 mark against the age-less echo whatever the device ids.
- **The other direction:** an old build's kid (`kids: true`, no `kidsAge`) merges to `{12, 0}` and loses to any limit a parent set. This is pinned by fixture cases and by `two_devices_settle_on_one_admin_and_the_parents_limit_in_one_round`, which also proves that two admins become one and that the second round is quiet.
- **Database:** the new columns are additive. A build that does not know v8 never names the new columns.

## Decisions and deviations (for the lead)
1. **An imported Kids removal clears `age`.** The phase file said a removal leaves the column alone. The web now writes `NULL` (`lists-exchange.ts`, "a removal's included"), and the core matches the web. A leftover age on a tombstone is ignored either way.
2. **Contract §8 "the core's `Profile` applies the same rule" is not done here.** `Profile` has no `admin`/`has_pin` fields yet, and adding them changes the uniffi surface, which belongs to phase 05. The merge half is done here (claims on kids are skipped). Phase 05 must derive `admin = !kids && admin_claimed_at IS NOT NULL` and `has_pin = !kids && pin_hash IS NOT NULL`.
3. **`profiles::create(…, kids = true)` still leaves `kids_age` NULL.** The web's `insertProfile` writes 12. I did not change this, because phase 05 owns `state/profiles*` and replaces creation with `create_kid(…, age)`. Every reader treats NULL as 12 (export, and the COALESCE upgrade). The only effect: a kid created by import counts one extra change, when `{12, 0}` is written over NULL.
4. **The old `set_kids(set_id, true)` now means "mark from 12".** On a live "from 6" mark it moves the mark to 12. This matches the web's `PUT /api/kids/:id` with no age. Android only sends `true` for an unmarked title, so in practice nothing changes until phase 05's `set_kids(set_id, age)`.
5. **Fixture runner:** one projected `run_merge_fixture(file, project)` now serves `merge`/`lists-merge` (`without_roles`, because a merged kid now carries a limit), `stats-merge` (`stats_only`) and `profile-roles-merge` (`roles_only`). That replaces a duplicated loop.

## Concerns
- Name and device tie-breaks compare Rust `String`s (UTF-8 byte order), while the web compares JS strings (UTF-16 code units). The two orders differ only between characters above U+FFFF and those in U+E000–U+FFFF. The difference already exists for device ids and `display_name`, and the admin tie (smaller name) now inherits it. Not fixed.

**Status:** DONE_WITH_CONCERNS
**Summary:** Phase 04 is built test-first in two code commits. Core 660, workspace 1716, clippy and code standards are all green, all 31 role fixture cases pass, and the Kotlin bindings are unchanged. Interop with the real 0.105.1 core is verified both ways.
**Concerns:** Contract §8's `Profile` half, and making a new kid at 12, are left to phase 05. An imported removal clears `age`, which follows the web rather than the phase file.
