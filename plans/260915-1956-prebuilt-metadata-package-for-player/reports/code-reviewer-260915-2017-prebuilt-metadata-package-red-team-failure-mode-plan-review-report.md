---
title: "Red-team plan review: prebuilt metadata package for the player"
reviewer: code-reviewer (failure-mode analyst / flow tracer)
date: 2026-09-15
plan: plans/260915-1956-prebuilt-metadata-package-for-player/
verdict: DONE_WITH_CONCERNS
---

# Red team: failure modes in the prebuilt metadata package plan

Scope: `plan.md` + `phase-01`..`phase-06`, verified against the live tree at
`crates/mediagram`, `crates/mlib-spec`, `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md`.

## Traced flows (flow tracer role)

**Snapshot path (phase 3 step 1 claim).** `push_index::push_snapshot`
(`crates/mediagram/src/commands/push_index.rs:39-54`) → `db::open`
(`index/db.rs:12-37`: creates dir, sets WAL, **runs every migration**, **writes
`meta.schema_version`**) → `snapshot::checkpoint` (`index/snapshot.rs:15-18`,
`PRAGMA wal_checkpoint(TRUNCATE)` via `execute_batch`, result row discarded) →
`snapshot::snapshot_to` (`index/snapshot.rs:24-42`: **writes
`meta.last_push_at` to the LIVE db**, unlinks a stale dest, `VACUUM INTO`).
Early returns: none in `checkpoint`; `snapshot_to` returns `Err` on non-UTF-8
dest, on failed unlink, on `VACUUM INTO`. Ordering guarantee: `VACUUM INTO`
reads through the pager, so committed WAL frames are captured with or without
the checkpoint — the consistency comes from the read transaction, not the
checkpoint. **Nothing in this path is read-only.**

**Set completion / source lifetime (phase 2 key insight).** `add::run`
(`commands/add.rs:126`) → `pipeline::run_set` (`upload/pipeline.rs:22-82`) →
per-part `mark_done` → on last part `sets::set_hash_and_complete` then
`remove_recorded_tmp` (`pipeline.rs:78`, body `157-168`: **deletes the
faststart temp file and its meta key**) → back in `add.rs:130-133`
`pending_parts().is_empty()` → `delete_meta(source:{set_id})` →
`add.rs:136-142` `push_index::push_after_set`. Early returns: `add.rs:128`
`upload_result.context("uploading set")?` aborts before any completion work.
**By the time `add` regains control, a remuxed source no longer exists.**

**Migration runner.** `db::open` → `for migration in MIGRATIONS { conn.execute(..) }`
(`index/db.rs:24-27`, unconditional, every open) → `set_meta("schema_version",
SCHEMA_VERSION)` (`db.rs:29-34`, unconditional overwrite).
`mlib_spec::schema::SCHEMA_VERSION = 1` (`crates/mlib-spec/src/schema.rs:4`);
`mlib_spec::SPEC_VERSION = 2` (`crates/mlib-spec/src/lib.rs:27`). No
version gate exists today.

---

## Finding 1: `ALTER TABLE sets ADD COLUMN art_key` bricks `library.db` on the second command

- **Severity:** Critical
- **Location:** Phase 2, "Implementation Steps" step 1 (`phase-02-artwork-capture-at-add-time.md:65`)
- **Flaw:** The phase says "Add `art_key TEXT` to `sets` as a schema migration" and changes nothing else. The current runner re-executes **every** entry of `MIGRATIONS` on **every** `db::open` (`index/db.rs:24-27`) and relies on all statements being `CREATE ... IF NOT EXISTS` (`schema.rs:7-38`). SQLite has no `ADD COLUMN IF NOT EXISTS`. The plan never says the runner must become version-gated first; the cross-plan section (`plan.md:144-149`) explicitly treats the runner change as somebody else's job ("the second one to land must not reuse the first one's number").
- **Failure scenario:** Phase 2 ships. First `mediagram add` migrates and works. Every subsequent invocation of **any** command — `add`, `resume`, `verify`, `rescan`, `push-index`, `export-package` — calls `db::open`, hits `conn.execute("ALTER TABLE sets ADD COLUMN art_key TEXT")`, gets `duplicate column name: art_key`, and fails with `running migration: …` before doing anything. The library is not corrupt but the tool is dead until someone patches the binary. `resume` being dead is the worst case: pending sets with a live `source:{set_id}` meta key cannot be finished, and the source files may since have been moved.
- **Evidence:** `crates/mediagram/src/index/db.rs:24-27`; `crates/mlib-spec/src/schema.rs:7-38`; guard test that would also fail: `crates/mlib-spec/src/schema.rs:48-54` asserts every migration string contains `IF NOT EXISTS`.
- **Suggested fix:** Make phase 2 own the version-gated runner (the design already written in `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:113-127`) as step 0, applied **inside one transaction** with the version write in the same transaction; or, if that is deferred, use the `CREATE TABLE IF NOT EXISTS sets_art(set_id TEXT PRIMARY KEY, art_key TEXT)` side-table form, which stays idempotent under today's runner.

## Finding 2: cross-plan schema version collision — `db::open` downgrades `schema_version`, then re-applies a non-idempotent ALTER

- **Severity:** Critical
- **Location:** `plan.md`, "Cross-plan relationship" (`plan.md:138-149`) and Phase 1 "Architecture" (`phase-01-package-format-and-manifest.md:42,65`)
- **Flaw:** Two independent claims are wrong. (a) "They do not conflict, but the second one to land must not reuse the first one's number" — the approved tutorial spec pins `SCHEMA_VERSION = 2` with `MIGRATIONS[1] = ALTER TABLE sets ADD COLUMN chap TEXT`. If `art_key` lands first and takes version 2, every database already at version 2 **skips** the `chap` group when the tutorial code lands, because a gated runner only applies groups above the recorded version. The column never appears; every `sets` read/write naming `chap` fails at runtime on exactly the installs that were up to date. Renumbering alone does not fix this — the *recorded* version in an existing DB is the payload. (b) `db::open` writes `meta.schema_version = SCHEMA_VERSION` **unconditionally** (`db.rs:29-34`), so any run of an older binary (an operator's second machine, a rolled-back release, a `cargo install` from an older tag to make a package) **downgrades** the recorded version on a newer database. The next run of the new binary then re-applies `ALTER TABLE sets ADD COLUMN chap` → `duplicate column name` → permanently unopenable database with no repair command in either plan.
- **Failure scenario:** Operator upgrades, adds a course, downgrades to the previous release for one `export-package` run (or runs the older binary on a shared `data_dir` over NFS/syncthing). `schema_version` drops 3→2. Upgrade again: `db::open` fails forever. Recovery requires hand-editing `meta` with `sqlite3`, which is nowhere documented.
- **Evidence:** `crates/mediagram/src/index/db.rs:29-34`; `crates/mlib-spec/src/schema.rs:4` (`SCHEMA_VERSION = 1`); `crates/mlib-spec/src/lib.rs:27` (`SPEC_VERSION = 2`); tutorial spec `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:113-127` and `:211-213`. Note also that every manifest/pointer example in this plan already prints `"schema": 2, "spec": 3` (`phase-01…:42-43,65-66`, `phase-05…:49-50`) — values that do not exist in the tree and that phase 6 requires to be fixture-generated and asserted equal to real output (`phase-06…` success criteria).
- **Suggested fix:** Make the version-gated runner a shared prerequisite owned by whichever lands first, make `db::open` **refuse** a `schema_version` greater than the binary's constant instead of overwriting it, and reserve version numbers explicitly in both documents (e.g. `art_key` = 2, `chap` = 3) rather than "whoever is second takes the next one".

## Finding 3: the "read-only" export writes to the live database — including in `--dry-run` — and poisons `last_push_at`

- **Severity:** High
- **Location:** Phase 3, "Implementation Steps" steps 1 and 7 (`phase-03-export-assembly.md:46-47,59-61`); `plan.md` "Constraints discovered in the codebase" (`plan.md:60-63`)
- **Flaw:** The plan's premise is that `index::snapshot::{checkpoint, snapshot_to}` "already produce a safe point-in-time copy" and can simply be reused. Traced: `snapshot_to` **writes** `meta.last_push_at` into the live database before vacuuming (`index/snapshot.rs:29-30`), and reaching it requires `db::open`, which runs migrations and rewrites `meta.schema_version` (`db.rs:24-34`). So `export-package` mutates the index, and `--dry-run` — specified as performing "no writes outside a temp directory" — mutates it too.
- **Failure scenario:** `last_push_at` is documented as "Unix timestamp recorded into the snapshot just before it is vacuumed out, so the pushed copy carries its own push time" (`docs/mlib-spec-v2.md:254`), and the staleness design has the TV app comparing it (`plans/reports/brainstorm-260914-1954-…-report.md:99`). A nightly `export-package` cron refreshes `last_push_at` every night while `push-index` has been failing for a week (its failure path is a warning-and-continue in several places). Both the pinned Telegram index — the declared disaster-recovery copy (`plan.md:53-55`) — and every player looking at that field believe the recovery copy is one day old. It is seven days old. This is a silent corruption of the only freshness signal the recovery path has.
  Second consequence, same root cause: the whole-plan success criterion "Running the export twice with no library changes produces a byte-identical inner tar.gz" (`plan.md:124-126`) is **unachievable by construction**. Every export rewrites `meta.last_push_at = now` in the live DB *before* vacuuming, so the `library.db` member of the archive differs on every run, and the CI test written against that criterion will be flaky-by-design or will be quietly weakened to "the same staging tree twice" (`phase-04…:106`), which tests nothing about the export.
- **Evidence:** `crates/mediagram/src/index/snapshot.rs:24-42`; `crates/mediagram/src/index/db.rs:12-37`; `crates/mediagram/src/commands/push_index.rs:39-45`; `docs/mlib-spec-v2.md:254`; `plan.md:124-126` vs `phase-04-encryption-and-archive.md:106`.
- **Suggested fix:** Split `snapshot_to` into `vacuum_into(conn, dest)` (pure copy) and the `last_push_at` write that `push_index` does explicitly; have the export call the pure form, open the live DB **read-only** (`OpenFlags::SQLITE_OPEN_READ_ONLY`, no migrations), and stamp its own `meta.last_export_at` *inside the snapshot* after copying if it wants a timestamp.

## Finding 4: art capture "after a successful upload" reads a file the pipeline has already deleted, and `resume` never captures at all

- **Severity:** Critical
- **Location:** Phase 2, "Implementation Steps" step 4 (`phase-02-artwork-capture-at-add-time.md:72-73`)
- **Flaw:** Control returns to `add.rs:130` only after `run_set` finished, and `run_set`'s completion branch calls `remove_recorded_tmp`, which deletes the faststart remux temp and forgets the path (`upload/pipeline.rs:75-79`, `157-168`). `source_path` in `add::run` is exactly that temp whenever a remux happened (`add.rs:51-53`, `remux.rs:19-60`). Capturing "after a successful upload" therefore runs `ffmpeg -i <deleted path>` for every MP4 that needed faststart. Separately, `resume` also drives sets to completion (`commands/resume.rs:76-80`) and phase 2 places capture only in `add`, so every set finished by `resume` — i.e. every set whose first `add` was interrupted, the exact population most likely to exist — gets no art, and `source:{set_id}` is deleted at completion so the path is unrecoverable.
- **Failure scenario:** Operator adds 40 MP4 rips; 25 need faststart. All 25 upload fine, all 25 art captures fail with ENOENT, logged as warnings per the phase's own "never fails an upload" rule. Six months later the first `export-package` ships a package where 25 titles have no image and there is no command in this plan to backfill them, because `add` is the only capture site and the source pointer is gone.
- **Evidence:** `crates/mediagram/src/upload/pipeline.rs:75-79` and `:157-168`; `crates/mediagram/src/commands/add.rs:51-53,126-133`; `crates/mediagram/src/commands/resume.rs:52-80`; `crates/mediagram/src/media/remux.rs:36,60`.
- **Suggested fix:** Capture inside `run_set` immediately before `remove_recorded_tmp` (one site, covers `add` and `resume`), or capture from `args.file` (the user's original, which mediagram never deletes) rather than `source_path`. Either way add `mediagram art backfill <set_id> <file>` so a missed capture is repairable; the plan currently has no idempotent recovery for art at all.

## Finding 5: frame capture inherits a subprocess pattern with no timeout, and sits in front of the index push

- **Severity:** High
- **Location:** Phase 2, "Implementation Steps" step 3 (`phase-02-artwork-capture-at-add-time.md:71`), read with step 4
- **Flaw:** "Reuse the subprocess pattern in `media::remux`" — that pattern is `tokio::process::Command…output().await` with **no timeout and no kill** (`media/remux.rs:38-45`). An ffmpeg that never exits blocks the caller forever. Because step 4 puts capture after upload but the phase does not say whether it precedes `push_index::push_after_set` (`add.rs:136-142`), the default reading puts a hang between "set is complete" and "index is pushed".
- **Failure scenario:** The source lives on an SMB/NFS mount that goes unreachable mid-`add` (or the file is truncated by another process). Parts already uploaded, set marked `complete`, `set_hash` written. ffmpeg enters uninterruptible sleep seeking to 10%; `add` hangs; the operator eventually SIGKILLs it. Result: the set is complete locally, **the index was never pushed** (so the Telegram recovery copy does not know about it), and the art is missing with no backfill path. The phase's "a failure to produce art never fails an upload" guarantee does not hold, because a hang is not a failure.
- **Evidence:** `crates/mediagram/src/media/remux.rs:38-45` (no `tokio::time::timeout`, no `kill_on_drop`); `crates/mediagram/src/commands/add.rs:136-142`; grep for `timeout` under `crates/mediagram/src/media/` returns nothing.
- **Suggested fix:** Specify `tokio::time::timeout(30s, child.wait_with_output())` + `kill_on_drop(true)` + `-nostdin` for capture, and state explicitly that capture runs **after** `push_index::push_after_set`, so a stuck ffmpeg can never delay the recovery copy.

## Finding 6: shell execution destroys the archive-then-pointer ordering guarantee

- **Severity:** Critical
- **Location:** Phase 5, "Implementation Steps" steps 2, 3, 5 (`phase-05-publish-and-latest-pointer.md:73-79`) and the ordering claim at `:58-62`
- **Flaw:** Step 2 decides to "Run through the shell so operators can pipe or chain", step 3 keys failure on the exit code, step 5 publishes the pointer once the archive command "succeeds". A POSIX shell reports the exit status of the **last** element of a pipeline; `pipefail` is not on by default in `sh`. Any of the documented usages — `rclone copy {file} r2:mediagram/ | tee -a publish.log`, `aws s3 cp {file} s3://… && echo ok`, an `rclone` invocation with `--transfers`/`--check-first` that returns 0 while retrying in the background, `nohup … &` — yields exit 0 with no object at the destination. Nothing in phase 5 verifies the object exists afterwards, and `publish_base_url` is configured independently of `publish_cmd`'s destination, so the URL in the pointer is an unchecked guess (`rclone copy {file} r2:mediagram/pkgs/` + `publish_base_url = "https://example.com/"` → permanent 404). Caches are unaddressed: `latest.json` is at a fixed URL (`:60-66`), which is exactly the object a CDN will cache under a default TTL, while the archive names are unique and will not be cached — the inverse of what the design needs.
- **Failure scenario:** Nightly publish. The bucket is full / the token expired. `rclone` writes an error, `tee` exits 0, the run reports success, `latest.json` is published naming an archive that does not exist. Every player fetching the pointer now 404s on download; the rollback guard (phase 6 step 2) has already recorded the new `created_at` in some players, so they will also refuse the *older*, still-valid package. Fleet-wide outage from a green run.
- **Evidence:** `phase-05-publish-and-latest-pointer.md:73-79`; success criteria at `:88-93` verify order in a test double only, never the remote state; no `HEAD`/`GET` verification step anywhere in the plan (`grep -n "verify\|HEAD\|curl" phase-05*.md` → only exit-code checks); the codebase has no HTTP client usage outside TMDB (`crates/mediagram/src/metadata/tmdb_client.rs:44-92`).
- **Suggested fix:** Run `sh -euo pipefail -c` (or refuse pipelines and exec argv directly), then **verify by fetching**: `HEAD {publish_base_url}{file}` must return 200 and the advertised byte length before `latest.json` is generated. Require the docs to state a `Cache-Control: no-cache` (or short max-age) for `latest.json` and `immutable` for archives, and make the reader treat a 404 on the pointed-at file as "keep the package you have" rather than a hard failure.

## Finding 7: same-day filename reuse silently replaces a published archive, and the reader's filename short-circuit pins players to it

- **Severity:** High
- **Location:** Phase 1, "Architecture" naming paragraph (`phase-01-package-format-and-manifest.md:76-79`); Phase 6, reader algorithm step 4 (`phase-06-docs-and-player-integration.md:42-43`)
- **Flaw:** The claim "a file is never silently replaced and a player can pin one" rests on a suffix counter (`-2`, `-3`) whose only possible input is local state — the contents of `--out`. Phase 3 requires the staging directory to be removed on success and failure, and a 35-64 MB artifact per run is exactly what an operator deletes or what CI discards between runs. The remote is never listed. Meanwhile the reader's step 4 short-circuits on **filename equality**, not on `sha256`, so a reused name is never re-downloaded.
- **Failure scenario:** Operator exports, publishes, `rm`s the local `.enc` to save space, adds three titles, exports again the same day. The name is identical (`…_20260616.tar.gz.enc`), the remote object is overwritten. A player that is mid-download gets a body whose bytes span two different ciphertexts → sha256 mismatch → the pointer is rejected, and on retry the *new* pointer's sha256 no longer matches the *old* pointer's cached filename either. A player that already holds the old file skips the download entirely (step 4) and never sees the three new titles, forever, even though `created_at` advanced. Both failure modes are silent.
- **Evidence:** `phase-01-package-format-and-manifest.md:76-79`; `phase-03-export-assembly.md:70` ("The staging directory is removed on success and on failure"); `phase-06-docs-and-player-integration.md:42-43`; no remote listing capability exists — `publish_cmd` is fire-and-forget (`phase-05…:73-79`).
- **Suggested fix:** Name the archive by content, not by date: `prebuilt_mediagram_db_{created_at}_{sha256[..12]}.tar.gz.enc`, which is collision-free without any local bookkeeping. Change reader step 4 to compare `sha256`, not `file`.

## Finding 8: the rollback guard trusts an unauthenticated field and an unvalidated clock

- **Severity:** High
- **Location:** Phase 6, reader algorithm step 2 (`phase-06-docs-and-player-integration.md:37-41`); Phase 4 risk "Rollback" (`phase-04-encryption-and-archive.md:127-131`)
- **Flaw:** `created_at` lives in the plaintext, unsigned `latest.json` (`phase-01…:58`, `phase-05…:42`). The stated threat is "an attacker who can serve files, or a stale cache" — precisely the actor who also writes `created_at`. Replaying yesterday's ciphertext under a new filename with `created_at` bumped to now defeats the guard completely; the archive decrypts and its tag verifies, because it is a genuine package. Step 7 reads `manifest.json` but compares only `schema` — the authenticated `created_at` that phase 1 puts *inside* the manifest (`phase-01…:41`) is never cross-checked against the pointer. Conversely there is no upper bound on `created_at`: nothing in the codebase validates `SystemTime` (`index/snapshot.rs:25-28`, `commands/add.rs:90-93`, `commands/push_index.rs:60-63` all use `unwrap_or_default()` on the epoch duration and no sanity range).
- **Failure scenario A (attack):** stale CDN edge or hostile host serves last month's archive with a fresh pointer; every player "upgrades" backwards and loses a month of titles, with the guard reporting success. **Scenario B (Murphy):** the uploader's RTC is wrong for one export (VM restore, dead CMOS battery, bad NTP) and publishes `created_at = 1913-…` or `2091-…`. In the second case every player records the future timestamp and then refuses every legitimate package for the next 65 years. Phase 6 documents no reset, no override, and no "trust the newest `key_id`+`schema`" escape hatch.
- **Evidence:** `phase-06-docs-and-player-integration.md:37-49`; `phase-01-package-format-and-manifest.md:41,58`; `crates/mediagram/src/index/snapshot.rs:25-28`; `crates/mediagram/src/commands/add.rs:90-93`; `crates/mediagram/src/commands/push_index.rs:60-63`.
- **Suggested fix:** Move the rollback decision behind the GCM tag: after `doFinal`, require `manifest.created_at == pointer.created_at` and apply the monotonicity check to the manifest value; reject a pointer whose `created_at` is more than a small skew ahead of the device clock; have the exporter refuse to write a `created_at` older than the previous export's (it can read the last one from the DB) or further than an hour from the newest `sets.created_at`.

## Finding 9: the 64 MB ceiling fires after all the work, has no remediation, and the heap model is optimistic by ~50%

- **Severity:** High
- **Location:** Phase 4, "Implementation Steps" step 6 (`phase-04-encryption-and-archive.md:100-101`) and the heap table (`:70-78`); `plan.md:107-113`
- **Flaw:** Ordering: steps 1-5 tar, gzip and encrypt; step 6 then refuses. By then phase 3 has already fetched every poster over the network and written roughly twice the package size to disk (`phase-03…:76-79`). Worse, the refusal is terminal: there is no `--no-backdrops`, no art-size budget, no pruning command — phase 2 explicitly defers pruning ("Art cache growth is unbounded… pruning is out of scope for v1", `phase-02…:96-98`), and the export re-fetches TMDB art into the cache on every run, so manually deleting files does not stick. A library that crosses the ceiling cannot produce a package at all until format v2 exists. Separately the "roughly twice its size" model is wrong for the decrypt direction: Java/Conscrypt GCM buffers the entire ciphertext internally before the tag can be verified, so `doFinal` costs input array + internal buffer + output array ≈ 3×, i.e. ~192 MB at the 64 MB ceiling, which meets or exceeds the 192-256 MB cap the same section cites — before gunzip/untar, and before considering that a single 64 MB contiguous `byte[]` can fail to allocate on a fragmented TV heap even with nominal headroom.
- **Failure scenario:** Library grows to 420 titles. Nightly export downloads 60 MB of posters over 20 minutes, writes 130 MB of staging + archive, encrypts, then exits non-zero with "package is 71 MB, over the 64 MB ceiling; largest contributors: …". Every subsequent run repeats the same 20 minutes and the same failure. The operator's only lever is deleting files from an art cache the next run repopulates. Meanwhile players keep the last good package indefinitely with no signal that exports are failing.
- **Evidence:** `phase-04-encryption-and-archive.md:70-78,95-103`; `phase-03-export-assembly.md:52-58,76-79`; `phase-02-artwork-capture-at-add-time.md:96-98`; `plan.md:105-113`.
- **Suggested fix:** Compute the projected size from the art map **before** downloading (poster/backdrop counts × measured average, known from the art cache) and fail or auto-degrade at that point; make degradation the default — drop backdrops, then re-encode posters smaller, then omit art for the largest offenders — and record in the manifest which sets have no art so the player can show a placeholder. Re-derive the ceiling from a 3× model (≤ 48 MB hard ceiling), or specify a streaming/chunked reader in v1 rather than promising a v2.

## Finding 10: the TMDB image path rests on a cache that was never populated for those keys, degrades on a hard error, and the art key collides across TMDB namespaces

- **Severity:** Medium
- **Location:** Phase 3, "Implementation Steps" step 4 (`phase-03-export-assembly.md:52-54`); `plan.md:64-68`; Phase 2 art key table (`phase-02-artwork-capture-at-add-time.md:47-51`)
- **Flaw:** Three compounding errors. (a) "the export reuses the cache for image lookups": `DiskCachedApi` keys entries on `sha256(path + sorted query)` (`metadata/tmdb_client.rs:109-122`), and the entries `add` wrote are `/search/movie?query=…&year=…` and `/movie/{id}?append_to_response=external_ids` (`metadata/search.rs:31-35`, `metadata/resolve.rs:125-132`). An export that calls `/movie/{id}` with a different query string — or at all, for titles resolved by search without a details fetch — misses the cache and hits the network for every title. (b) On a miss the live client is used, and `TmdbClient::get_json` retries 429 only `MAX_RETRIES = 3` times and then `bail!`s, propagating a hard error (`tmdb_client.rs:12,68-88`); phase 3's success criterion "TMDB unreachable degrades to a package with fewer images, not a failure" requires per-item error swallowing that step 4 never states. `add` builds the client with `cfg.tmdb_key.as_deref().unwrap_or("")` (`commands/add.rs:35`), so an export on a machine with no `tmdb_key` produces a 401 per title, not an empty result. (c) The key `tmdb-{id}` (`phase-02…:50`) ignores `kind`, but TMDB's movie and TV id spaces are independent and `sets.tmdb` holds the show id for episodes (`index/set_row.rs:52`). Movie 550 and TV 550 both map to `tmdb-550`; whichever is fetched first wins the file and the other title displays the wrong poster.
- **Failure scenario:** First export on a 300-title library: 600 uncached requests, TMDB rate-limits at ~50 req/s, the fourth 429 aborts the whole export after 20 minutes of work — and re-running starts over because nothing partial was retained. When it does succeed, one series shows a movie poster.
- **Evidence:** `crates/mediagram/src/metadata/tmdb_client.rs:11-12,68-88,109-122,126-149`; `crates/mediagram/src/metadata/search.rs:31-35`; `crates/mediagram/src/metadata/resolve.rs:125-132`; `crates/mediagram/src/commands/add.rs:30-35`; `crates/mediagram/src/index/set_row.rs:52`.
- **Suggested fix:** State the exact endpoint+query the export uses so it matches what `add` cached, wrap every per-title fetch so a failure records "no art" and continues (with a summary count), require `tmdb_key` explicitly for `--with-art` instead of sending an empty key, and make the key `tmdb-movie-{id}` / `tmdb-tv-{id}`.

---

## Unresolved questions

1. Which effort is expected to land first, and does the reviewer of the tutorial spec agree to reserve `SCHEMA_VERSION = 3` for `chap` (Finding 2)? This must be decided before either phase 2 or the tutorial migration is written.
2. Is `export-package` allowed to write to `library.db` at all? If yes, `--dry-run`'s "no writes" promise must be reworded; if no, `snapshot_to` must be split (Finding 3).
3. Does the manifest's single `spec` field mean "the highest `sets.spec_version` present" or "the exporter's `SPEC_VERSION`"? A library will hold mixed per-row values (`schema.rs:22`, `set_row.rs:74`) and phase 1 leaves it ambiguous.
4. Is there an intended `art backfill` / re-export repair command, or is a missed capture accepted as permanent (Finding 4)?
5. `main.rs:63` loads and validates the config before dispatching any subcommand, and `config::load` bails unless `api_hash` and `channel` are non-empty (`config.rs:86-88`). `gen-key` (`phase-04…:98-99`) and `export-package --dry-run` therefore refuse to run on a machine that has no Telegram credentials — intended, or should these two bypass the Telegram validation?
