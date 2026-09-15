# Red-team plan review: prebuilt metadata package for the player

Reviewer: code-reviewer (assumption destroyer / scope auditor)
Date: 2026-09-15
Plan: `plans/260915-1956-prebuilt-metadata-package-for-player/`
Method: grep/read against `crates/`, empirical `cargo` resolution and compile probes, crates.io index metadata. No code changed.

Pre-flight on the two claims I was asked to attack:

- **Phase 2's source-path claim is TRUE but mis-cited.** `add` does delete `source:{set_id}` on completion (`add.rs:130-133`). `upload/pipeline.rs` does **not** touch that key — it only removes `tmp:{set_id}` (`pipeline.rs:157-168`). Phase 2 stands; see Finding 6 for what it got wrong instead.
- **Phase 2's TMDB claim is HALF FALSE.** `poster_path`/`backdrop_path` are indeed absent from `tmdb_types.rs` (verified, 105 lines, no such field). But "the export reuses the cache for image lookups" is false — see Finding 3.

---

## Finding 1: The version-gated migration machinery phase 2 depends on does not exist, and the plan never schedules building it

- **Severity:** Critical
- **Location:** Phase 2, "Implementation Steps" step 1; plan.md, "Cross-plan relationship" (lines 145-149)
- **Flaw:** Phase 2 step 1 says "Add `art_key TEXT` to `sets` as a schema migration". There is no migration framework capable of that. `MIGRATIONS` is a flat `&[&str]` of `CREATE TABLE IF NOT EXISTS` statements replayed unconditionally on every `db::open`, with no version gate:

  ```rust
  // crates/mediagram/src/index/db.rs:24-27
  for migration in mlib_spec::schema::MIGRATIONS {
      conn.execute(migration, [])
  ```

  Adding a column to `sets` requires `ALTER TABLE sets ADD COLUMN art_key TEXT`. SQLite has no `ADD COLUMN IF NOT EXISTS`, so replaying it on the second `db::open` raises `duplicate column name` and every subsequent command dies. Adding the column to the existing `CREATE TABLE IF NOT EXISTS sets(...)` string instead is a silent no-op on any database that already exists.

  plan.md:145-149 asserts "Whichever lands second takes the next version number and appends its own migration group". **Migration groups and version numbers do not exist today.** They are a *proposal* inside the tutorial spec (`2026-09-15-tutorial-course-support-design.md:112-127`), which is explicitly "approved but has no implementation plan yet" (plan.md:134-136). The package plan has quietly made itself depend on unwritten work from a plan that does not exist, while listing its own schema work as a one-line "Related Code Files" edit.

  The guard test also breaks: `schema.rs:48-54` asserts `MIGRATIONS.iter().all(|m| m.contains("IF NOT EXISTS"))`. An `ALTER TABLE` migration fails it.
- **Failure scenario:** Phase 2 lands before the tutorial work. Developer adds `art_key` to the `CREATE TABLE` string (the only thing the current structure permits). Fresh CI databases get the column, tests pass, review passes. Every existing user's `library.db` never gets it. `db::open` then writes `schema_version = 2` unconditionally (`db.rs:29-34`) regardless of whether anything applied, so the export stamps `"schema": 2` into the manifest. First `export-package` on a real machine: `SELECT art_key FROM sets` → `no such column: art_key`. Every export fails, forever, on exactly the machines that have a library worth exporting.
- **Evidence:** `crates/mediagram/src/index/db.rs:24-27` (unconditional replay), `db.rs:29-34` (unconditional `schema_version` write), `crates/mlib-spec/src/schema.rs:7-38` (flat `&[&str]`, all `IF NOT EXISTS`), `crates/mlib-spec/src/schema.rs:48-54` (test enforcing `IF NOT EXISTS`), `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:112-127` (`MIGRATIONS: &[&[&str]]` proposed, not built), `plans/.../plan.md:145-149`.
- **Suggested fix:** Either (a) make "convert `MIGRATIONS` to version-gated groups and rewrite `db::open` to read `meta.schema_version` and apply only newer groups, in one transaction" an explicit numbered step in phase 2 with its own effort estimate and its own test, and change the `IF NOT EXISTS` assertion to apply only to group 0; or (b) drop `art_key` entirely (see Finding 8) and remove the schema dependency from this plan.

---

## Finding 2: `snapshot_to` writes a fresh timestamp into the live database, so the "byte-identical tar.gz" success criterion is unachievable — and the export silently forges push bookkeeping

- **Severity:** Critical
- **Location:** plan.md "Success (whole plan)" (lines 124-126); Phase 3 step 1; Phase 4 "Key insight" table and Success Criteria
- **Flaw:** The plan states as a whole-plan success criterion: "Running the export twice with no library changes produces a byte-identical inner `tar.gz`", and phase 4 builds a whole conceptual table around "The inner `tar.gz` is deterministic, so an unchanged library produces an identical archive". Phase 3 step 1 reaches this by reusing `index::snapshot::snapshot_to`. That function's *first action* is a write:

  ```rust
  // crates/mediagram/src/index/snapshot.rs:24-31
  pub fn snapshot_to(conn: &Connection, dest: &Path) -> Result<()> {
      let now = SystemTime::now()...as_secs() as i64;
      db::set_meta(conn, "last_push_at", &now.to_string())
  ```

  Every call mutates `meta.last_push_at` on the **live** connection to wall-clock now, then `VACUUM INTO`. Two exports one second apart therefore produce two different `library.db` files by construction. The inner `tar.gz` can never be byte-identical. I separately confirmed that `tar` 0.4.46 + `flate2` 1.1.10 *are* byte-deterministic with hand-built headers (mtime 0, uid/gid 0, mode 0644) — gzip header `1f 8b 08 00 00 00 00 00 00 ff` on repeated runs 2 s apart. The archive layer is fine; the payload is the problem, and the plan never noticed because it treated `snapshot_to` as read-only.

  Second, worse defect: this is a **shared-state mutation crossing a lifetime boundary**. `last_push_at` has a documented meaning — "Unix timestamp recorded into the snapshot just before it is vacuumed out, so the pushed copy carries its own push time" (`docs/mlib-spec-v2.md:254`). `export-package` is not a push. Running an export rewrites the record of when the index was last pushed to Telegram, with no push having occurred.
- **Failure scenario:** Operator runs `export-package` nightly at 02:00 and `push-index` weekly. `last_push_at` now reads 02:00 daily. Any future freshness check, retention decision, or disaster-recovery triage based on that key concludes the Telegram pinned index is current when it is a week stale. Meanwhile the CI test asserting determinism (phase 4 step 2, which archives "the same tree twice") passes — because it tests the *staging tree*, not the *export*, so the defect ships green.
- **Evidence:** `crates/mediagram/src/index/snapshot.rs:24-31` and `:29-30`; `docs/mlib-spec-v2.md:254`; `crates/mediagram/tests/index_snapshot.rs:64` (existing test asserts the live value is written); `crates/mediagram/src/commands/push_index.rs:42-45` (the only current caller, where the write is correct); plan.md:124-126; phase-03:46-47; phase-04:25-29.
- **Suggested fix:** Split `snapshot_to` into `snapshot_to(conn, dest)` (pure: checkpoint + `VACUUM INTO`) and a `record_push_time(conn)` that `push_index` calls first. Then either drop `last_push_at` from the export path entirely, or normalise it to a fixed value inside the snapshot copy. Restate the determinism criterion as "byte-identical given an unchanged library *and* a normalised snapshot", and make the determinism test run the real export twice, not the archiver twice.

---

## Finding 3: "the export reuses the cache for image lookups" — `DiskCachedApi` physically cannot hold an image, and `TmdbClient` cannot reach the image host

- **Severity:** High
- **Location:** plan.md "Constraints discovered in the codebase" (lines 76-79); Phase 3 step 4; Phase 2 "Key insight"
- **Flaw:** The plan asserts "The TMDB layer is a `TmdbApi` trait behind `DiskCachedApi`, so the export reuses the cache for image lookups" and phase 3 step 4 says "Fetch TMDB images for `tmdb-*` keys **through the existing disk cache**". Three independent blockers:

  1. The trait has exactly one method and it returns JSON: `async fn get_json(&self, path, query) -> Result<Value>` (`tmdb_client.rs:19`). There is no byte-returning method to implement against.
  2. `TmdbClient` hardcodes `BASE_URL = "https://api.themoviedb.org/3"` (`tmdb_client.rs:11`) and appends `api_key` to every request (`:52`). TMDB images live on `https://image.tmdb.org/t/p/{size}{path}` — different host, no api_key, and the base URL is nominally obtained from `/configuration`. Grep confirms neither `image.tmdb` nor `/t/p/` nor a `configuration` call exists anywhere in `crates/`.
  3. The response path calls `resp.json()` and **errors on non-JSON**: `"tmdb response for {path} was not JSON"` (`tmdb_client.rs:81-85`). The cache writes `{key}.json` via `serde_json::to_vec` (`:146`) and reads via `serde_json::from_slice` (`:131`). A JPEG round-trips through neither.

  So "reuse" is not a reuse. It is a new HTTP client, a new cache namespace, and a new binary-safe cache format — unbudgeted work inside a 1d phase that already contains six other steps.

  Compounding: `DiskCachedApi` has **no TTL and no invalidation** (`:126-148` — any readable file wins forever). A cached poster is cached until someone deletes the directory by hand.
- **Failure scenario:** Implementer takes phase 3 step 4 literally, calls `api.get_json("/t/p/w500/abc.jpg", &[])`. Request goes to `api.themoviedb.org/3/t/p/w500/abc.jpg?api_key=...`, returns a 404 JSON body, `bail!`s. Implementer then writes a second HTTP stack mid-phase with no design review, most likely without `reqwest::Error::without_url()` — which `tmdb_client.rs:65,84` is careful to apply precisely because "the URL carries the api key". The new image path has no api_key so the leak is benign there, but the pattern is copied and the discipline is lost.
- **Evidence:** `crates/mediagram/src/metadata/tmdb_client.rs:11` (BASE_URL), `:19` (trait surface), `:44-56` (url construction + api_key), `:81-88` (JSON-only response handling), `:126-148` (JSON-only cache), `:39-41` + `crates/mediagram/src/commands/add.rs:35` (only call site). Grep for `image.tmdb|/t/p/|configuration` in `crates/` returns zero relevant hits.
- **Suggested fix:** Rewrite the constraint in plan.md to state the truth: the TMDB layer is JSON-only and an image fetcher is *new code*. Add explicit steps to phase 3 for an `art/` binary cache (separate directory from `tmdb-cache`), a pinned image base URL with a documented fallback, and a size/count cap per the existing step 4. Re-estimate phase 3 above 1d.

---

## Finding 4: The `aes-gcm` 0.11 and `rand` 0.10 APIs the plan specifies do not exist — verified by compiler

- **Severity:** High
- **Location:** Phase 4, "Verified crate versions" table and Implementation Steps 4-5; plan.md "Verified dependencies"
- **Flaw:** The plan makes a point of this: it calls out that the research report's snippets "target `aes-gcm` 0.10 and `flate2` 1.0; both are outdated and the 0.11 API differs", then specifies `rand` 0.10.2 for "`OsRng` for key and nonce" and step 5 "fresh 12-byte nonce from `OsRng`". It verified the version *numbers* against crates.io and never verified the *API*. Both named symbols are gone.

  Compiled against exactly the pinned versions:

  ```
  error[E0432]: unresolved import `rand::rngs::OsRng`
   --> no `OsRng` in `rngs`

  error[E0599]: no associated function or constant named `generate_nonce` found
    for struct `AesGcm<Aes256, U12>` in the current scope

  warning: use of deprecated associated function `aes_gcm::KeyInit::generate_key`:
    use the `Generate` trait impl on `Key` instead
  ```

  And the feature name in the plan's mental model is also wrong — cargo rejects it outright:

  ```
  package `msrvtest` depends on `rand` with feature `os_rng` but `rand`
  does not have that feature.
  help: there is a feature `sys_rng` with a similar name
  ```

  `rand` 0.10 renamed the OS generator to `SysRng` behind the `sys_rng` feature. `aes-gcm` 0.11 removed `AeadCore::generate_nonce` and deprecated `KeyInit::generate_key` in favour of `crypto_common::Generate::{generate, try_generate}` on `Key`/`Nonce`, and `crypto-common`'s `rand_core` support is an **optional feature** that must be enabled explicitly. Phase 4's dependency table lists no features at all.

  Note the one thing that *is* fine: `crypto-common` 0.2.2 requires `rand_core ^0.10`, matching `rand` 0.10.2's `rand_core ^0.10`, so there is no rand_core trait split. That was the obvious trap and the plan happens to avoid it.
- **Failure scenario:** Phase 4 is scheduled at 1d and can start in parallel with phase 2 ("Phase 4 needs only phase 1"). The implementer opens with a compile error on line 1 of `encrypt.rs`, has no reference snippet (the plan explicitly discredited the research report's snippets without replacing them), and burns the budget reverse-engineering a security-critical API from rustdoc. Highest-risk outcome: reaching for `generate_key(&mut rng)` because the compiler *suggests* it as the near-miss for `generate_nonce`, silently producing a 32-byte value where a 12-byte nonce belongs, or falling back to a non-CSPRNG.
- **Evidence:** Empirical `cargo build` against `aes-gcm = "0.11.1"`, `rand = "0.10.2"`, edition 2024, rust-version 1.87. crates.io index: `aes-gcm` 0.11.1 → `aead ^0.6`; `aead` 0.6.1 → `common ^0.2`; `crypto-common` 0.2.2 → `rand_core ^0.10` *optional*; `rand` 0.10.2 → `rand_core ^0.10`. Plan citations: phase-04:49-58, phase-04:97-99, phase-04:45-48, plan.md:90-97.
- **Suggested fix:** Replace phase 4's table with feature-complete specs (`aes-gcm = { version = "0.11.1", features = ["rand_core"] }`, `rand = { version = "0.10.2", features = ["sys_rng"] }`) and write the three lines of 0.11 API into the phase as a snippet (`Nonce::<U12>::try_generate(&mut SysRng...)` or equivalent), verified by a throwaway compile *before* the phase is marked ready. The plan already accepted responsibility for API currency when it discredited the research report; it must finish the job.

---

## Finding 5: `image = "0.25.10"` violates the workspace MSRV, and no step in any phase actually uses the crate

- **Severity:** High
- **Location:** plan.md "Verified dependencies" (line 95); Phase 4 dependency table (by omission)
- **Flaw:** Two problems in one row.

  (a) MSRV. The workspace declares `rust-version = "1.87"` (`Cargo.toml`). `image` 0.25.10 declares `rust_version = 1.88.0`. With `resolver = "3"` cargo says so verbatim:

  ```
  Locking 120 packages to latest Rust 1.87 compatible versions
    Adding image v0.25.10 (requires Rust 1.88.0)
  ```

  Because the plan pins `0.25.10` exactly, the resolver has nowhere to downgrade to and admits an MSRV-incompatible package. Relaxing to `"0.25"` makes cargo pick 0.25.9 instead — so the *pinned number in the plan is simply the wrong one*. Nobody catches this locally: this machine runs rustc 1.98.1. It surfaces only on a CI job or contributor that honours the declared 1.87. (The same probe flagged `aes v0.9.3` as requiring 1.89; the resolver correctly pinned 0.9.2, so `aes-gcm` is unaffected.)

  (b) Dead dependency. `image` is justified as "poster downscaling" (plan.md:95). Grep the phases: phase 2 does its downscaling **in ffmpeg** ("scaled so the long edge is at most 1000 pixels", step 3), and phase 3 step 4 fetches TMDB images and writes them into `art/` with no resize step at all. **No implementation step in any of the six phases calls the `image` crate.** It is a dependency with no consumer — which is also why Finding 7's size math is broken.
- **Failure scenario:** CI adds an MSRV job (or a contributor pins 1.87 via rust-toolchain.toml) and the build fails with "package `image` cannot be built because it requires rustc 1.88.0". The fix looks like a one-character bump to `rust-version`, which silently raises the MSRV of the whole workspace including `mlib-spec` — a published-interface change made by accident, to add a crate nothing calls.
- **Evidence:** `/home/andre/Workspace/mediagram/Cargo.toml` (`rust-version = "1.87"`, `resolver = "3"`); crates.io index `image` 0.25.10 `rust_version 1.88.0`, 0.25.9 `rust_version 1.85.0`; empirical `cargo generate-lockfile` output quoted above. Plan: plan.md:95; phase-02:70-71 (ffmpeg does the scaling); phase-03:53-55 (no resize).
- **Suggested fix:** Drop the `image` row unless a phase step is added that resizes fetched TMDB artwork (which Finding 7 argues is mandatory anyway). If kept, pin `image = "0.25"` and add an MSRV check to CI so the declared 1.87 stops being decorative.

---

## Finding 6: The capture hook is in the one place that is not guaranteed to run, and points at a file the pipeline has already deleted

- **Severity:** High
- **Location:** Phase 2, step 4 ("Call the capture from `add` after a successful upload") and "Related Code Files"
- **Flaw:** Phase 2 correctly identifies that frames must be captured during `add`. It then picks the wrong seam, twice.

  (a) **`resume` also completes sets, and phase 2 does not touch it.** `resume_one` runs the identical `run_set` and deletes the identical `source:` key on completion:

  ```rust
  // crates/mediagram/src/commands/resume.rs:80-83
  if parts::pending_parts(conn, &set.set_id)?.is_empty() {
      db::delete_meta(conn, &source_key)?;
  ```

  Phase 2's "Related Code Files" lists `commands/add.rs` and nothing else. Any set whose `add` was interrupted — precisely the large multi-part uploads, which are the ones a user most wants a poster for — completes via `resume`, loses its source path, and can never be captured. There is no backfill command and `rescan` is additive (it recovers rows from captions, not frames).

  (b) **For remuxed sources, the file is gone before the hook fires.** `add` records `tmp:{set_id}` when a faststart remux was written (`add.rs:117-120`) and passes `source_path` — the temp file — into `run_set`. `run_set` deletes it *inside itself*, on completion, before returning:

  ```rust
  // crates/mediagram/src/upload/pipeline.rs:75-79
  if parts::pending_parts(conn, &set.set_id)?.is_empty() {
      ...
      remove_recorded_tmp(conn, &set.set_id).await;
  }
  ```

  `add.rs:126` awaits `run_set`; the capture at step 4 runs *after* that, at `add.rs:130+`. For every trailing-`moov` MP4, `source_path` names a deleted file by then. Per step 4 the failure is "wrapped so any failure is a warning" — so it degrades to a warning nobody reads and a permanently missing poster, not a visible bug.

  The irony: the plan's own Key insight is "the source video is deleted from the index's knowledge the moment a set completes" — it diagnosed the disease and then placed the cure on the far side of the same boundary.
- **Failure scenario:** User adds a 40 GB remuxed MP4. Upload succeeds. Log line: "capture failed: No such file or directory". Set has no art. User re-runs `add` to retry — dedup skips it. Nothing in the CLI can ever produce art for that set again. Meanwhile `Success Criteria` "A file added with `--manual` produces a captured JPEG on disk" passes in CI, because test fixtures are small faststart MP4s that never trigger a remux.
- **Evidence:** `crates/mediagram/src/commands/resume.rs:76-83`; `crates/mediagram/src/upload/pipeline.rs:75-79` and `:157-168` (`remove_recorded_tmp`); `crates/mediagram/src/commands/add.rs:117-120` (`tmp:` recorded), `:126` (`run_set` awaited), `:130-133` (post-completion block); phase-02:72-73 and :56-62.
- **Suggested fix:** Move capture *before* `run_set` (right after `SetRow::from_caption`, where `source_path` is guaranteed live and the upload has not started), or into `run_set` itself just before `remove_recorded_tmp`. Either way it then covers `resume` for free. Add a success criterion asserting capture works for a trailing-`moov` fixture that triggers a remux, and one asserting a `resume`-completed set gets art.

---

## Finding 7: The 35 MB figure is unsourced and low by 2-4x, gzip buys nothing on JPEG, and the 64 MB hard refusal makes the tutorial use case unshippable

- **Severity:** High
- **Location:** plan.md "Size ceiling" (lines 107-114); Phase 4 size table (lines 70-81)
- **Flaw:** plan.md calls it "The measured estimate" and phase 4's table row reads "35 MB (300 titles, **measured estimate**)". "Measured estimate" is not a thing. Grep of the plan directory and `plans/reports/` finds no measurement, no fixture, no sample, no cited source — the only three occurrences of the number are the two assertions themselves. The entire 64 MB ceiling, the 48 MB warning, the "comfortable" verdict and the decision to defer chunked framing to a hypothetical format v2 all rest on this one unbacked number.

  35 MB / 300 titles = **117 KB per title for a poster *and* a backdrop**. TMDB `w500` posters run ~50-120 KB; `w1280` backdrops run ~150-400 KB. Realistic is 250-500 KB per title, i.e. **75-150 MB for 300 titles** — straight through the 64 MB hard refusal. The estimate is only reachable if artwork is aggressively downscaled, and per Finding 5 **no phase step downscales fetched TMDB artwork**.

  Second unstated assumption: the archive is `tar.gz`, and the plan reasons about gzip as if it helps. The payload is ~99% JPEG by volume. Gzip on JPEG yields roughly 0%. The `.gz` in the name is buying tar padding removal and nothing else, so package size ≈ raw art size with no headroom.

  Third, the scale case you asked about. A 2000-lesson tutorial library: until `add-course` exists, each lesson is its own set added via `add --manual` with no provider id, so each resolves to `set-{set_id}` and gets **its own captured frame** (phase-02 priority table, row 3). At ≤1000 px long edge that is ~150-300 KB per lesson → **300-600 MB**. The export does not degrade — phase 4 step 6 *refuses*. The plan's answer is "format version 2 with chunked framing", i.e. the feature does not work for the workload described by the very spec this plan names as its sibling (`relatedSpec` in the frontmatter). Phase 2's `cid-{cid}` collapse to one cover per course is explicitly gated behind "once `add-course` exists" (step 6), so the mitigation is unavailable precisely while the problem is live.
- **Failure scenario:** Operator with a normal 300-title movie/TV library runs the first `export-package`. It fetches ~600 images over some minutes, assembles ~90 MB, and then **refuses to write anything** with "package exceeds 64 MB". Every retry re-refuses. The only documented remedy is "trim your artwork", which no command implements. The feature is dead on arrival for its primary user and the failure appears only at the very last step, after all the network cost has been paid.
- **Evidence:** plan.md:107-114 and :112-113 ("measured estimate", no source anywhere in `plans/`); phase-04:70-81; phase-02:45-54 (priority table, row 3 = per-set frames); phase-02:76-78 (`cid-` path gated on `add-course`); phase-03:53-55 (fetch with no resize); plan.md:95 vs phase-02:70-71 (nothing calls `image`); `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:11-16` (one lesson = one set).
- **Suggested fix:** Replace the invented number with a real measurement before phases 3-5 start: pull 20 real TMDB posters+backdrops, record the median, multiply. Add an explicit downscale-and-recompress step to phase 3 step 4 with a stated target (e.g. poster `w342`, backdrop `w780`, JPEG q80) — that is what makes `image` a real dependency and what makes any ceiling reachable. Change the over-ceiling behaviour from hard refusal to automatic degradation (drop backdrops first, then posters, then report) so the command always produces a usable package. Drop the `.gz` claim or state plainly that compression is ~0% on this payload.

---

## Finding 8: Scope audit — `art_key` duplicates state already derivable from existing columns, and phase 2 quietly needs two more columns it never declares

- **Severity:** Medium
- **Location:** Phase 2, steps 1 and 5; "Related Code Files"
- **Flaw:** Step 1 adds `sets.art_key` and justifies it as "Recording it avoids re-deriving priority rules in two places and lets the export build its art map with one query". Every input to that priority table is already a column on `sets`:

  ```
  crates/mlib-spec/src/schema.rs:11   tmdb INTEGER, tvdb INTEGER, imdb TEXT,
  crates/mlib-spec/src/schema.rs:18   duration INTEGER, variant TEXT, group_key TEXT,
  ```

  - `tmdb-{id}` ⟵ `sets.tmdb` (exists, `sets.rs:9-11` in `COLUMNS`)
  - `cid-{cid}` ⟵ `sets.group_key`, which the tutorial spec explicitly designates as the `cid` home: "`sets.group_key` holds `cid`. `group_key` is already declared and never populated; this is its intended use" (spec:28, :130)
  - `set-{set_id}` ⟵ the primary key

  So `art_key` is a **cached derivation of three existing columns** — denormalised state that can drift (a set edited to gain a tmdb id keeps a stale `set-*` key; a course reassigned keeps a stale `cid-*`), for which no reconciliation path is specified. The stated benefit, "one query", is a `CASE WHEN group_key IS NOT NULL THEN ... WHEN tmdb IS NOT NULL THEN ... ELSE ...` expression — one query either way. Given Finding 1, this column costs a whole migration framework to buy nothing.

  Separately, step 5 says "Parse `poster_path` and `backdrop_path` from TMDB responses and **store them on the set row**". That is two more columns on `sets`. Step 1 declares one column. "Related Code Files" says "store the chosen art key", singular. The actual schema delta is 3 columns, and 2 of them are undeclared work inside a phase whose only schema step names 1.

  Third gap: no backfill. Sets added before phase 2 have `NULL` poster_path. Phase 3 step 3 derives the art map "from the snapshot's `art_key` column", so those sets are silently art-less forever unless the export falls back to re-querying TMDB by `sets.tmdb` — which no step describes and which would require `tmdb_key` to be configured at export time, a requirement stated nowhere.
- **Failure scenario:** A user upgrades, runs `export-package`, and gets a package with zero artwork because every pre-existing row has `art_key IS NULL`. No error, no warning — phase 3 step 3 specifies "Missing art is recorded as absent, not as an error". The headline feature ("a complete browsable catalog") ships empty for every existing library and the design says that is correct behaviour.
- **Evidence:** `crates/mlib-spec/src/schema.rs:8-23` (`tmdb`, `group_key` present), `crates/mediagram/src/index/sets.rs:9-11` (`COLUMNS` includes both), `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:28` and `:130` (`group_key` reserved for `cid`), phase-02:65-67 (one column), phase-02:74-75 (two more, undeclared), phase-02:56-62, phase-03:50-52.
- **Suggested fix:** Drop `art_key`; resolve the key with a `CASE` expression over `group_key`/`tmdb`/`set_id` in one place (`art::key_for`, which step 2 already creates). If poster_path caching is genuinely wanted, put it in the existing `meta` table (`art:{tmdb_id}` → path) rather than widening `sets` — no migration, no drift, and it is already the documented home for "state that doesn't belong in `sets`/`parts`" (`db.rs:1-3`). Add an explicit step for how pre-existing rows acquire art.

---

## Finding 9: The manifest's `spec` and `schema` fields report build-time constants as if they described the rows, and every example value in the plan is wrong

- **Severity:** Medium
- **Location:** Phase 1, "Key insight" table and Architecture JSON; Phase 5 `latest.json` block; Phase 6 reader algorithm step 7
- **Flaw:** Phase 1's table defines `spec` as answering "**Which caption version produced these rows?**", owned by `mlib_spec::SPEC_VERSION`. But `SPEC_VERSION` is a single compile-time constant (`crates/mlib-spec/src/lib.rs:27`), while spec version is recorded **per row**: `sets.spec_version INTEGER NOT NULL` (`schema.rs:22`, in `COLUMNS` at `sets.rs:11`). A library built across a v1→v2 upgrade holds a mix. Stamping the binary's constant into the manifest answers a different question than the one the table claims ("what version is the uploader" vs "what produced these rows") and a player that trusts it will mis-parse the older rows.

  Every concrete value in the plan is also wrong today. Actual: `SCHEMA_VERSION = 1` (`schema.rs:4`), `SPEC_VERSION = 2` (`lib.rs:27`). The plan writes `"schema": 2, "spec": 3` in three separate places (phase-01:44-45, phase-01:64-66, phase-05:49-50). Phase 6's success criterion is "Every manifest and pointer example in the doc is generated by a test that asserts equality with real output" — which is the right instinct, but the plan text those docs will be written from already contains numbers that no fixture can produce.

  Related, smaller: phase 1's non-functional requirement is that the pointer "must reveal nothing about the library's contents", with a success criterion "A test proves the pointer contains no library content" implemented as an enumeration of field *names*. `"bytes": 48127744` is a direct proxy for library size and therefore title count; `created_at` leaks upload cadence. A field-name test cannot catch either, so the criterion certifies a property it does not check.
- **Failure scenario:** Player author implements `schema >= 2` support from `docs/mlib-package-v1.md`, ships, and rejects every real package because they all carry `schema: 1`. Or: phase 2 lands, `SCHEMA_VERSION` goes to 2 for the `art_key` column, the tutorial work independently takes 2 for `chap` (spec:112), and two different database layouts both claim `"schema": 2` in the wild. The plan's own cross-plan note says "the second one to land must not reuse the first one's number" — with nothing enforcing it.
- **Evidence:** `crates/mlib-spec/src/lib.rs:27` (`SPEC_VERSION = 2`), `crates/mlib-spec/src/schema.rs:4` (`SCHEMA_VERSION = 1`), `crates/mlib-spec/src/schema.rs:22` (per-row `spec_version`), `crates/mediagram/src/index/sets.rs:11`; phase-01:21-26, :44-45, :64-66, :30-32, :102; phase-05:49-50; phase-06:51-52.
- **Suggested fix:** Change `spec` to `min_spec`/`max_spec` computed from `SELECT MIN(spec_version), MAX(spec_version) FROM sets` on the snapshot, so it describes the rows as the table promises. Strip the invented example numbers from all three phase files now, before phase 6 copies them into a normative doc. Either accept and document that `bytes` leaks approximate library size, or move it inside the encrypted manifest and have the reader use `Content-Length`.

---

## Finding 10: Every safety property of this design lives in an unwritten player, and nothing in this plan can test or enforce any of them

- **Severity:** Medium
- **Location:** Phase 4 "The footgun the research missed" and Risk Assessment (rollback); Phase 6 reader algorithm steps 2, 6; Phase 6 Success Criteria
- **Flaw:** The plan's load-bearing guarantees are all reader-side obligations expressed as prose:

  - **Rollback protection** (phase-04:128-131, phase-06:36-39): "the reader algorithm **requires** refusing a `created_at` older than the package it already holds". The exporter cannot require anything. `latest.json` is unsigned plaintext; a hostile host or a stale CDN serves an old pointer and a conforming-but-simpler player accepts it. The plan lists this under "Mitigation" as though it were implemented.
  - **`CipherInputStream` ban** (phase-04:60-65, phase-06:46-50): correct and valuable, and entirely unenforceable from this side. A player that ignores it silently accepts truncated plaintext — the exact failure the section exists to prevent — and the export has no way to detect it.
  - **The heap ceiling** (phase-04:66-81): "Android TV devices commonly cap an app's heap near 192-256 MB" is cited with no source and drives a hard numeric limit in shipped code (Finding 7). Nobody has run `doFinal` on a real device with a real package.
  - **Phase 6 success criterion** "Someone can implement a reader from the spec alone, with no Rust" — has no evaluator. It will be checked by the author of the spec, against their own spec.

  This is not an argument against writing the spec. It is an argument against the plan's current confidence: `Success (whole plan)` lists six criteria, all of which test the *exporter*, while the four properties that actually protect a user are untested by construction. The plan reads as though they are settled.

  Sharpest instance: phase 5 step 2, "Run through the shell so operators can pipe or chain", combined with phase 5's success criterion "A filename or path containing spaces or quotes is passed correctly". Shell interpolation of `{file}` and correct handling of quotes are in direct tension; "substitute `{file}` with the absolute path, quoted" is not a specification (quoted how — POSIX single-quote escaping, or `format!("\"{}\"", path)`, which breaks on `$` and backtick). The archive filename is generated by mediagram so the practical risk is low, but `--out <dir>` (phase-03:18) is user-supplied and flows into the same string.
- **Evidence:** phase-04:60-65, :66-81, :128-131; phase-06:36-39, :46-50, :76-82; phase-05:70-73, :88; phase-03:18; plan.md:116-130 (six exporter-only criteria).
- **Suggested fix:** Retitle the reader obligations as "Normative requirements on a conforming reader (unverified until a player exists)" and add an explicit "Unverified until phase 7 (player)" block to plan.md listing all four, so nobody later reads them as delivered. Source or drop the Android heap number. For phase 5, specify the exact quoting function (`shell-escape`-style single-quote wrapping) rather than the word "quoted", and add a test with a path containing `'`, `$` and a space.

---

## Verified as sound (not findings)

Stated only to prevent re-litigation:

- `tar` 0.4.46 + `flate2` 1.1.10 produce byte-identical output on repeated runs with hand-built headers (`mtime`/`uid`/`gid` 0, mode 0644). Gzip header is `1f 8b 08 00 00 00 00 00 00 ff` both times. The archive layer's determinism claim holds; only the payload breaks it (Finding 2).
- `aes-gcm` 0.11.1 (→ `crypto-common` 0.2.2 → `rand_core ^0.10`) and `rand` 0.10.2 (→ `rand_core ^0.10`) share one `rand_core`. No trait-version split. The obvious trap is avoided.
- Phase 4 step 3's claim that "`api_hash` and `tmdb_key`" are already kept out of `Debug` is true — manual `impl Debug` at `crates/mediagram/src/config.rs:33-47`, tested at `:159-166`.
- Phase 2's claim that `add` deletes `source:{set_id}` on completion is true (`add.rs:130-133`), though the plan's second citation (`upload/pipeline.rs`) is wrong — that file only handles `tmp:` (`pipeline.rs:157-168`). Fix the citation.
- `tmdb_types.rs` genuinely has no `poster_path`/`backdrop_path`. Because serde ignores unknown fields by default, already-cached TMDB JSON on disk *does* contain them, so adding the fields makes existing caches usable without a re-fetch. The plan does not say this; it is a free win worth recording.
- `index::snapshot::checkpoint` does what the plan says (`PRAGMA wal_checkpoint(TRUNCATE)`, `snapshot.rs:15-18`). Only `snapshot_to` is misread.
- All six pinned crate versions exist and are unyanked on crates.io as of 2026-09-15. Only `image` 0.25.10 breaks MSRV.

---

## Unresolved questions

1. Does `export-package` require `tmdb_key` to be configured? Nothing in phases 3-6 says so, but Finding 8 shows pre-existing rows have no stored `poster_path`, so either the export re-queries TMDB (needs the key) or old libraries get no art. Which?
2. Is `package_key` meant to have a `MEDIAGRAM_PACKAGE_KEY` env override? `config.rs:96-115` provides overrides for `api_hash`/`tmdb_key`/`data_dir`. Without one, a 32-byte AES key sits in a config file that nothing chmods — `crates/mediagram/src/telegram/client.rs:178,186` enforce 0700/0600 on the session dir but nothing does so for `config.toml`.
3. If phases 1-6 land before the tutorial work, who owns rewriting `MIGRATIONS` into version groups (Finding 1)? It is currently unassigned in both plans.
4. Phase 3 step 2 says "Open the snapshot read-only" — via `db::open`? That runs migrations and writes `schema_version` into the snapshot (`db.rs:24-34`), mutating the artifact. Needs an explicit read-only open path.
