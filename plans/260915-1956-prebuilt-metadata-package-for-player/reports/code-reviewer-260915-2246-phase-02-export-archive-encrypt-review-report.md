# Phase 2 review — export, archive and encrypt (`bb06c1a`)

Reviewer: code-reviewer · 2026-09-15 · read-only review, no code modified.

## Scope

- `crates/mediagram/src/export/{mod,encrypt,archive,posters,stage,titles,pointer,budget}.rs`
- `crates/mediagram/src/commands/export_package.rs`
- `crates/mediagram/src/index/snapshot.rs`, `src/config.rs`, `src/metadata/tmdb_types.rs`
- tests: `export_{encrypt,archive,posters,budget,titles_and_pointer,snapshot_is_read_only}.rs`
- 800 LOC production, 634 LOC test. Every file under the 200-line rule.

Verified against: `phase-02-export-archive-and-encrypt.md`, `plan.md` ("Defects fixed"),
`crates/mlib-spec/src/package/mod.rs`, `metadata/{resolve,tmdb_client,search}.rs`,
`index/db.rs`, `Cargo.lock`, and four empirical sqlite/WAL experiments.

## Overall assessment

The cryptographic core is sound and the module boundaries are right. Nonce generation,
AAD binding, key redaction and the tar member check are all correct, and the
`snapshot_to`/`copy_to` split is a clean, behaviour-preserving refactor. Three things
do not hold up: the headline read-only claim is falsified by one line the split left
behind, the size ceiling is never enforced on the bytes that matter, and the AAD the
cipher consumed is thrown away where phase 3 needs it.

---

## Critical

### C1. The export writes to `library.db`. `--dry-run` writes to it too.

`crates/mediagram/src/export/stage.rs:51` — `Staging::copy_index` calls
`snapshot::checkpoint(conn)` (`index/snapshot.rs:15-18`,
`PRAGMA wal_checkpoint(TRUNCATE)`) before `copy_to`. A checkpoint **writes WAL frames
into the main database file and truncates the `-wal` to zero**. `copy_to` is
side-effect-free; `copy_index` is not, and `copy_index` is the production path.

Empirically confirmed (three runs, one sqlite db each, a writer connection held open so
the WAL is hot — exactly a concurrent `add`):

| variant | copy correct | `library.db` byte-identical | `-wal` |
|---|---|---|---|
| checkpoint + `VACUUM INTO` (current code) | yes | **no** | 4152 → 0 |
| `VACUUM INTO` only | yes | yes | 4152 → 4152 |
| `mode=ro` + `VACUUM INTO` | yes | yes | 4152 → 4152 |

The checkpoint is not merely harmful, it is **unnecessary**: `VACUUM INTO` reads through
the WAL, so the copy contains the uncommitted-to-main rows either way (`copy rows = 1` in
all three variants). The risk-assessment line in the phase spec already says this — "the
checkpoint only folds the WAL in first" — it just is not the thing providing consistency.

Failure scenario: `mediagram add` is uploading a 40 GB set (hours). The operator runs
`mediagram export-package --dry-run` to see how big the package would be. The export
checkpoints the live index underneath the running upload, rewriting `library.db` and
truncating the WAL the uploader is using. Nothing corrupts — SQLite declines a TRUNCATE
checkpoint it cannot take safely — but every claim in the commit message, the phase spec
(success criterion 1, step 10 "writes nothing anywhere, including the live index") and
`plan.md` ("Defects fixed": *Export mutated the live index, even in `--dry-run`") is false
in precisely the concurrent case the design exists to support.

Second-order: `execute_batch` discards the `(busy, log, checkpointed)` row
`wal_checkpoint` returns, so a checkpoint that was refused because another process held
the lock is silently reported as success. Safe here only because the operation should not
be happening at all.

**Fix** (two lines, both):
```rust
// stage.rs:51 — delete this line
snapshot::checkpoint(conn)?;
```
```rust
// export_package.rs:34 — and make the guarantee structural
use rusqlite::OpenFlags;
let conn = Connection::open_with_flags(&live, OpenFlags::SQLITE_OPEN_READ_ONLY)
    .with_context(|| format!("opening {} read-only", live.display()))?;
```
The `with_context` message at `export_package.rs:35` already says "read-only"; today the
flags say otherwise. Read-only + WAL was verified to work both with and without a
pre-existing `-shm` (it needs write permission on the *directory*, which the export has).

Then update `index/snapshot.rs:2-3` (the module doc still describes checkpoint-then-vacuum
as the snapshot recipe) and `push_index.rs:42`, which can keep its checkpoint — folding the
WAL before a *push* is legitimate.

### C2. The size ceiling is never applied to the package that is actually produced.

`crates/mediagram/src/commands/export_package.rs:48` checks
`estimate_bytes(index_bytes, titles.len())` against `REFUSE_BYTES` (48 MiB,
`budget.rs:16`). Nothing ever compares the finished `sealed.len()` to
`mlib_spec::package::MAX_PACKAGE_BYTES` (64 MiB, `package/mod.rs:35`) — the only ceiling a
reader enforces (`pointer_is_readable` → `PointerError::TooLarge`, `package/mod.rs:190`).

The estimate is wrong in both directions and cannot substitute:

- **Index term over-counts.** `estimate_bytes` uses the raw `VACUUM INTO` output
  (`stage.rs:54-56`), but the archive is gzipped. A sqlite index of captions and hex
  digests compresses roughly 5-10×.
- **Poster term under-counts.** `POSTER_ESTIMATE = 32 KiB` (`budget.rs:11`, comment
  claims "Measured posters land near 25 KB" — no measurement exists anywhere in
  `plans/`). TMDB `w342` JPEGs commonly run 40-80 KB and gzip does not compress JPEG.

Failure scenario: 1400 titles, 3 MB index. Estimate = 3 MB + 1400 × 32 KiB = 46.75 MB →
under 48, passes. Actual = gzip(3 MB) ≈ 0.6 MB + 1400 × 55 KB ≈ 76 MB. The export prints
`wrote … (79691776 bytes, 1400 poster(s))` and exits 0. The player refuses the pointer
with `TooLarge` and the operator has no signal connecting the two. The inverse also
bites: a 60 MB index with 100 titles is refused up front although it would gzip to ~8 MB.

**Fix:** keep the pre-download estimate as an early *warning* only, and make the real
check the authoritative one, after `seal` at `export_package.rs:87`:
```rust
if sealed.len() as u64 > mlib_spec::package::MAX_PACKAGE_BYTES {
    bail!(
        "package is {} bytes, over the {} byte limit a reader can hold in memory; \
         re-run after trimming the library",
        sealed.len(), mlib_spec::package::MAX_PACKAGE_BYTES
    );
}
```
and drop the index term from `estimate_bytes` (or multiply it by an honest compression
factor), since counting uncompressed bytes against a compressed ceiling is what makes
`REFUSE_BYTES` both too strict and too loose. Phase spec step 4 also asks the refusal to
name the poster count; `export_package.rs:49-52` names only bytes.

---

## High

### H1. The pointer that produced the AAD is dropped; phase 3 must reconstruct it exactly.

`export_package.rs:81-90` builds `pointer::draft(created_at, &key)`, feeds
`associated_data(&pointer)` to `seal`, then discards both `pointer` and `created_at`.
`run` returns `Result<()>`.

Phase 3 must publish a `latest.json` whose `format`, `created_at`, `key_id`, `schema` and
`spec` are **byte-for-byte** the ones authenticated here. The only surviving trace of
`created_at` is the file name, which carries day resolution
(`package/naming.rs:18`, `YYYYMMDD`). If phase 3 calls `now_unix()` again, or recovers the
date from the name, every reader fails with `EncryptError::Tag` — whose message is
"wrong key, corrupt file, or edited pointer" (`encrypt.rs:32`), i.e. it will look like an
attack, not a bug. The failure is total (no reader can ever open the package) and silent
until a player tries.

**Fix now, before phase 3 exists:** have `run` return or persist the exact draft —
e.g. write `staging`-independent `pointer.json` next to the package, or change the
signature to `Result<LatestPointer>` and let phase 3 fill in `file`/`url`/`bytes`/`sha256`
on *that* value. `export_titles_and_pointer.rs:113` already asserts filling those four
fields does not change the AAD, so the contract is one step from being enforceable.

### H2. `Staging::fetch_posters` writes a caller-supplied string into a path; the phase-1
validator that exists for this is never called.

`crates/mediagram/src/export/stage.rs:76`:
```rust
let file = format!("{}/{}.jpg", POSTER_DIR, poster.key);
```
`poster.key` comes from `PosterRef`, a `pub struct` with `pub key: String`
(`posters.rs:20-24`), in `pub mod export` (`lib.rs:6`). `mlib_spec::package::poster_key_is_valid`
(`package/mod.rs:118`) was written in phase 1 for exactly this — `plan.md` "Defects fixed"
lists *Art filenames derived from an unvalidated `set_id` → Keys are validated against a
strict charset before touching a path* — and grep shows **zero callers outside
`crates/mlib-spec/tests/`**.

Not exploitable through the command today: `poster_key` (`posters.rs:82-87`) formats
`u64`, so it can only produce `tmdb-movie-<digits>` / `tmdb-tv-<digits>`. It is a trust
boundary held only by a private function two layers away, in a public API. The commit
message's "Poster paths and archive members are both validated before they reach a path"
is true of paths and members; keys are the third input and are not.

**Fix**, `stage.rs:75`:
```rust
for poster in refs {
    if !mlib_spec::package::poster_key_is_valid(&poster.key) {
        tracing::warn!(key = %poster.key, "poster key refused");
        continue;
    }
```

### H3. Poster downloads have no timeout and no response size limit.

`export_package.rs:117` `reqwest::Client::new()` — reqwest applies **no default request
or connect timeout**. `stage.rs:114` `response.bytes().await` buffers the entire body with
no cap.

Failure scenarios, both without any attacker:
1. A CDN edge accepts the connection and stalls. The export hangs forever at poster 7 of
   300. There is no `Ctrl-C` handling on this path, so the operator kills it — see M2.
2. A redirect or a misbehaving proxy returns a multi-GB body; the process OOMs holding it
   in `Vec<u8>` alongside the staging tree.

Aggravated by sequential fetching: `stage.rs:75` loops one `await` at a time, so a
300-title library is 300 serial round trips, and one slow host stalls the whole run.

**Fix:**
```rust
let http = reqwest::Client::builder()
    .timeout(std::time::Duration::from_secs(20))
    .connect_timeout(std::time::Duration::from_secs(10))
    .build()?;
```
and cap the body — check `response.content_length()` against a few hundred KB, or stream
with `std::io::copy` into a `Take`. Fetching with `futures::stream::iter(..).buffer_unordered(8)`
(the `futures` crate is already a dependency) would also turn 300 round trips into ~40.

### H4. The manifest advertises the compile-time schema version, not the one in the
database being exported.

`export_package.rs:72` sets `schema: mlib_spec::schema::SCHEMA_VERSION`, and
`pointer::draft` (`pointer.rs:24`) does the same for the pointer. But the export
deliberately does **not** go through `index::db::open` (`export_package.rs:27-29`), and
`db::open` is the only thing that writes `meta.schema_version` (`index/db.rs:29-34`).
So the exported `library.db` can carry a different `meta.schema_version` than the manifest
and pointer claim.

Failure scenario: the operator restores a `library.db` from a backup taken under an older
build and exports without running any other command first. `pointer.schema` says the
current version, `pointer_is_readable` (`package/mod.rs:173`) accepts it, and the player
opens a database whose actual layout it cannot read — after a successful tag verification,
which is the one place a reader is entitled to trust the contents.

**Fix:** read it from the snapshot, which is already open at `export_package.rs:41`:
```rust
let schema: i64 = crate::index::db::get_meta(&snapshot, "schema_version")?
    .and_then(|v| v.parse().ok())
    .context("snapshot has no recorded schema_version")?;
```
and use that for both the manifest and the draft pointer. At minimum, bail when it does
not equal `SCHEMA_VERSION`.

---

## Medium

### M1. Two concurrent exports destroy each other's staging directory.

`export_package.rs:37` `Staging::create(&data_dir, "export-staging")` — a fixed name, and
`create` begins with `remove_dir_all` on any existing directory (`stage.rs:33-36`).

Failure scenario: a cron export and a manual export overlap. The second run deletes the
first's staging tree mid-flight; `pack_dir` then fails at `std::fs::read`
(`archive.rs:29`) with "reading … for the archive", or `collect_files` enumerates a file
that vanishes before it is read. Then the second run's `Drop` removes the tree while the
first run's `Drop` also fires, logging "staging dir left behind". Consistent with the
known "no cross-process lock" property of this codebase, but here it is trivially avoided.

**Fix:** `format!("export-staging-{}", std::process::id())`, or a `ulid` (already a
dependency), plus sweeping stale `export-staging-*` on start.

### M2. A killed export leaves a plaintext copy of the index on disk.

`Drop for Staging` (`stage.rs:99-107`) runs correctly on every `?` return and on panic —
no `panic = "abort"` anywhere in the workspace, and no `std::process::exit` in the crate,
both grep-verified. It does **not** run on SIGINT/SIGTERM/SIGKILL, and the export has no
signal handler even though `tokio`'s `signal` feature is enabled
(`crates/mediagram/Cargo.toml:37`).

Failure scenario: Ctrl-C during the 300-poster serial download (likely, given H3) leaves
`~/.local/share/mediagram/export-staging/library.db` — the private channel id and every
message id, in the clear — until the next export. Exposure is bounded: 0700 dir, 0600
file, same uid, sitting beside the equally-plaintext `library.db` (mode 0644, verified).
So this is hygiene, not escalation, and the phase spec's step 9 "Remove the tree on
success and on failure" is met for in-process failures.

Also: `Drop` swallows a partial `remove_dir_all` failure into a `tracing::warn!` and `run`
still returns `Ok`, so a half-removed tree is reported as a clean export.

**Fix:** install a `tokio::signal::ctrl_c` race around the poster loop so the future is
cancelled and `Staging` drops normally; and sweep stale staging directories in
`Staging::create` (already half-done — it clears the exact name, not siblings).

### M3. A poster that fails after its file is created stays in the archive but not in the
manifest.

`stage.rs:109-117` `download` writes the body (`:115`) and *then* `restrict` (`:116`).
Any error after `std::fs::write` opens the file — ENOSPC mid-write, or `set_permissions`
failing — returns `Err`, so `fetch_posters` skips the manifest entry (`stage.rs:82-85`),
but the partial `.jpg` remains in `staging/posters/` and `pack_dir` packs everything it
finds (`archive.rs:18`).

This breaks phase spec success criterion *"every file in `posters/` is in the manifest"*
and silently inflates the package past the budget that was computed for it.

**Fix:** `let _ = std::fs::remove_file(dest);` in the error arm at `stage.rs:82`.

### M4. Zero successful posters is reported as a successful export.

`fetch_posters` swallows every per-title failure (`stage.rs:82-85`). Correct per the phase
spec for *a* title; wrong for *all* titles.

Failure scenario: DNS or a corporate proxy blocks `image.tmdb.org`. The export prints
`wrote … (0 poster(s))`, publishes, and the player replaces a good catalog with a
poster-less one. Nothing distinguishes "this library genuinely has no TMDB ids" from
"every download failed".

**Fix:** track attempted-vs-succeeded and `bail!` (or require `--allow-missing-posters`)
when `refs` was non-empty and `entries` is empty.

### M5. `unpack_to` has no decompressed-size budget.

`archive.rs:69` `std::io::copy(&mut entry, &mut out)` with no limit, behind a gzip decoder.
`MAX_PACKAGE_BYTES` caps the *compressed* file only. A 48 MB gz of zeros expands to tens of
GB.

The doc comment at `archive.rs:47-48` argues the bytes are authentic post-decryption — true,
and the correct framing — but `unpack_to` is the reference implementation the Android
reader will mirror, and today it is *only* reachable from tests (grep: no production
caller). A publisher with a stale or broken export becomes a device-filling bug.

**Fix:** thread a running total and `bail!` past `MAX_PACKAGE_BYTES`, or wrap each entry in
`Read::take(remaining)`.

### M6. The archive hardcodes mode 0644 and `unpack_to` ignores modes.

`archive.rs:33` `header.set_mode(0o644)`; `archive.rs:67` `File::create` → 0666 & umask.
The staging side is meticulous about 0600/0700 (`stage.rs:39`, `:71`, `:120`), and the
phase spec's risk assessment says the plaintext "is the same class of secret as the session
file". The unpacked `library.db` on the reader lands world-readable.

**Fix:** `header.set_mode(0o600)` and have `unpack_to` apply the header mode after writing.

### M7. `config.example.toml` never mentions `package_key`.

The phase spec lists `config.example.toml` under "Modify"; the commit does not touch it.
`package_key(cfg)` (`export_package.rs:101-103`) tells the operator to run
`head -c 32 /dev/urandom | base64` but not where the result goes, and the example file
that documents every other key omits this one. No `docs/` file mentions `export-package`
at all, against the documentation-management rule.

---

## Low

- **L1. `--out` pointing into the staging directory silently discards the package.**
  `export_package.rs:89` defaults to `data_dir/export`, but accepts any path.
  `--out ~/.local/share/mediagram/export-staging` writes the package inside the tree that
  `Drop` deletes on the next line. `run` prints "wrote …" and the file does not exist.
  Reject an `out` that is inside `staging.path()`.
- **L2. `getrandom` failure is reported as a cipher failure.** `encrypt.rs:53` maps it to
  `EncryptError::Cipher` ("package encryption failed"). An RNG that cannot produce a nonce
  is the one failure that deserves its own loud variant.
- **L3. A cached payload that will not deserialize is dropped with no log.**
  `posters.rs:63` `serde_json::from_value(value).ok()?` — the `warn!` at `:59` only covers
  the fetch error. `DetailsResponse.id` is a required field (`tmdb_types.rs:71`), so a
  truncated cache file costs a poster invisibly.
- **L4. `distinct_titles` silently drops a negative `tmdb`.** `titles.rs:26-28` `continue`s
  with no warning, unlike the unknown-kind arm two lines below which does warn.
- **L5. `pack_dir` follows symlinks out of the staging directory.** `archive.rs:95`
  `path.is_dir()` follows, and `fs::read` at `:29` follows. A symlink planted inside
  `export-staging` is archived with the *content of its target*. Requires same-uid write
  access to a 0700 directory, so it is defence-in-depth only, but `collect_files` should
  use `entry.file_type()` (which does not follow) and skip anything that is not a regular
  file. An untracked probe test in the tree reaches the same conclusion — see T5.
- **L6. No key zeroization anywhere.** `Cargo.lock:32-44` confirms `aes-gcm` 0.11.1 is
  built with default features (`aes`, `alloc`, `getrandom`) and **not** `zeroize`, so
  `AesGcm` does not implement `ZeroizeOnDrop` and the GHASH key is left in freed memory.
  On top of that: `parse_key`'s decoded `Vec<u8>` (`encrypt.rs:42`), the `[u8; 32]` in
  `run` (`export_package.rs:25`), the base64 `String` held in `Config` for the whole
  process, and the copy `Key::<Aes256Gcm>::from(*key)` makes on the stack
  (`encrypt.rs:55`, `:81` — `*key` copies, `From<[u8;32]>` copies again) all die
  unzeroized. The key **cannot** reach a log: `EncryptError` never quotes input
  (`encrypt.rs:26-35`), `Config`'s manual `Debug` redacts it (`config.rs:36-56`), and no
  `println!` touches it — all verified. So the residual exposure is a core dump or swap,
  for a symmetric key that by design also ships to every player. Fix if cheap:
  `aes-gcm = { version = "0.11.1", features = ["zeroize"] }` and `Zeroizing<[u8;32]>`.
- **L7. `plan.md`'s justification for cutting deterministic tar is now stale.** It reads
  "Unachievable: `snapshot_to` writes `last_push_at` before vacuuming, so the payload
  differs every run" — the export no longer calls `snapshot_to`. The cut may still be
  right (YAGNI), the reason is not.
- **L8. Spec deviation, benign.** Phase spec step 1 says keep the timestamp write in
  `push_index`; the implementation kept `snapshot_to` intact and added `copy_to` beside it.
  Grep-verified that `push_index.rs:42,45` behaves identically to before the split. The
  chosen shape is lower-risk than the specified one; noting it only so the spec is not
  read later as unimplemented.
- **L9. `--dry-run` prints one line, not the "table of what would be included with
  totals" step 10 asks for** (`export_package.rs:60-63`).

---

## Verified correct (asked about, no defect found)

- **Cache-key claim — holds.** `posters.rs:56` sends `/movie/{id}` or `/tv/{id}` with
  `[("append_to_response", "external_ids")]`; `resolve::fetch_details`
  (`resolve.rs:129-132`) sends the identical path and query. `DiskCachedApi::cache_key`
  (`tmdb_client.rs:109-122`) hashes path + sorted query and **does not include `api_key`**,
  which is appended later inside `TmdbClient::get_json` (`tmdb_client.rs:52`). So a warm
  cache hits, with or without a key. Every non-`--manual` resolve populates it: the
  explicit-id, `/find`, and search paths all funnel through `fetch_details`
  (`search.rs:46`). One caveat: on a *miss* with `tmdb_key = None`,
  `TmdbClient::with_cache("")` (`export_package.rs:115`) still issues a real request with
  an empty key, gets 401, and skips — network traffic and latency where the doc comment at
  `posters.rs:6-7` implies none. Harmless, worth a sentence in the comment.
- **`is_image_path` — could not be defeated.** `posters.rs:70-80`. Requires a leading `/`,
  a non-empty remainder with no further `/`, no `..`, and `[A-Za-z0-9._-]` only. That
  rejects `%2e%2e`, `?`, `#`, `\`, newlines, NUL, all non-ASCII (`is_ascii_alphanumeric`),
  and protocol-relative `//evil.com/x.jpg` (the remainder contains `/`). The result is
  concatenated onto a fixed `https://image.tmdb.org/t/p/w342` (`posters.rs:17`) and cannot
  alter the host, path depth or query. Critically, `poster_path` **never reaches a file
  name** — `stage.rs:76` uses `poster.key`, not `poster.path` — so traversal is
  structurally unreachable even if the filter were bypassed. Only gap: no length bound, so
  a 1 MB `poster_path` becomes a 1 MB URL; the server 414s and the title loses its poster.
- **`poster_key` — not reachable with anything hostile.** `posters.rs:82-87` formats a
  `u64` from `distinct_titles`, which itself matches on a fixed `"movie"`/`"ep"` string.
  Output is always `tmdb-(movie|tv)-<digits>`. (The missing validator is H2 — an API
  hygiene issue, not a reachable one.)
- **`unpack_to` member checks — correct.** `check_member_path` (`archive.rs:76-87`)
  allows only `Component::Normal`, rejecting absolute paths, `..`, `.` and Windows
  prefixes; `archive.rs:59` then rejects every non-regular-file entry type, so symlinks
  and hardlinks are refused before `dest.join`. Order is right (path check first).
- **Nonce handling — correct.** `getrandom::fill` straight from the OS per seal
  (`encrypt.rs:50-53`), never derived, never stored, 96-bit random with a message count in
  the single digits per key.
- **No sidecar leakage into the archive.** Verified empirically: `VACUUM INTO` produces a
  `journal_mode=delete` database, so opening the snapshot at `export_package.rs:41` and
  querying it creates no `-wal`/`-shm` in the staging directory for `pack_dir` to pick up.
- **`push-index` is unchanged.** `git show bb06c1a -- index/snapshot.rs` is a pure
  extraction: `snapshot_to` still does `set_meta("last_push_at")` then the same
  remove-then-`VACUUM INTO`. `push_index.rs:42,45` untouched.
- **AAD construction is stable across the draft/published split**, and `associated_data`
  restricts itself to integers plus a lower-hex `key_id` (`package/mod.rs:96-106`), which
  is what makes it reproducible by a non-serde JSON writer.

---

## Test quality

### T1. The read-only test tests the one function that was never the problem.

`tests/export_snapshot_is_read_only.rs:14-30` hashes `library.db`, calls
`snapshot::copy_to`, and re-hashes. It never calls `Staging::copy_index` — the production
path, and the one that checkpoints (C1). It also calls `checkpoint` itself at `:17` and
drops the connection at `:18` *before* taking the baseline hash, so the WAL is already
empty when the measurement starts. Both the setup and the subject are chosen to avoid the
defect. Rewrite against `Staging::copy_index` with a second connection holding an
uncheckpointed write open; it fails today.

### T2. `an_estimate_counts_the_index_and_every_poster` asserts nothing.

`tests/export_budget.rs:14`:
```rust
assert_eq!(with_ten - with_none, 10 * (with_ten - with_none) / 10);
```
`10 * x / 10 == x` for every `x` divisible by 10 — and any `POSTER_ESTIMATE` that is a
multiple of 10 satisfies it, as does an implementation that ignores the poster count
entirely for any value where `with_ten == with_none`… which is `0`, also divisible by 10.
The comment claims it checks "the assumed size, not some rounding artefact". It should be
`assert_eq!(with_ten - with_none, 10 * 32 * 1024);`.

### T3. `FakeApi` ignores the query, so the cache-key contract is untested.

`tests/export_posters.rs:32` `async fn get_json(&self, path: &str, _query: &[(&str, String)])`.
Red-team finding 12 ("TMDB cache miss on a different query string", High) has no
regression test: changing `posters.rs:55` to `append_to_response=images` keeps every test
green while making every export hit the network. Assert the query inside `FakeApi`, or
better, drive `resolve_posters` through a real `DiskCachedApi` over a `tempdir` seeded by
`resolve::fetch_details` — that also covers the untested criterion *"an export with no
`tmdb_key` and a warm cache still produces posters"*.

### T4. `stage.rs` has no tests at all.

122 lines, and the only module that touches permissions, the filesystem and the network.
Untested phase-spec success criteria, all of them in this file:
- *Staging files are 0600 and the directory 0700* (`stage.rs:39`, `:71`, `:120`)
- *Every manifest poster exists on disk and every file in `posters/` is in the manifest*
  (M3 shows this is violable)
- *Remove the tree on success and on failure* — `Drop` is never exercised
- the `copy_index` half of *`sha256` … identical before and after* (T1)

A `Staging::create` + assert-mode + drop + assert-gone test is ten lines.

### T5. An untracked probe test file is in the working tree.

`git status` shows `?? crates/mediagram/tests/edge_cases_probe_export.rs` (265+ lines).
It is **not** in `bb06c1a` and would not survive a clean checkout, so the "362 tests pass"
baseline is not reproducible from the commit. This is the fifth recurrence of this pattern
in this repo. Its own `archive_symlink_inside_staging_is_skipped` case ends with the
comment *"Currently symlinks ARE included in the archive (they are not explicitly
excluded)"* — an accurate, unresolved finding (L5) sitting in an untracked file. Either
`git add` it (after fixing that test, whose name asserts the opposite of what it verifies)
or delete it.

### T6. Other untested behaviours

- No test for `commands::export_package::run` end to end; `--dry-run` writing nothing is
  claimed only in the commit message.
- No golden-bytes assertion on `associated_data`. `tests/export_encrypt.rs:7` hardcodes
  the expected JSON as a literal but never compares it to `associated_data()` output.
  Reordering the fields of `AssociatedData` (`package/mod.rs:74-81`) would break every
  already-shipped reader with the whole suite green. One `assert_eq!` closes it.
- No test that the budget verdict is evaluated before any download (code order only).
- No test that `unpack_to` refuses an *absolute* member path (only `..` at
  `export_archive.rs:63`), nor that it refuses a symlink member in the tracked suite.

---

## Recommended actions, in order

1. **C1** — delete `snapshot::checkpoint` from `Staging::copy_index`; open the live
   connection `SQLITE_OPEN_READ_ONLY`. Rewrite T1 against `copy_index` with a hot WAL.
2. **C2** — enforce `MAX_PACKAGE_BYTES` on `sealed.len()` after encryption; demote the
   pre-download estimate to a warning and stop counting uncompressed index bytes.
3. **H1** — return or persist the exact `LatestPointer` that produced the AAD; do not let
   phase 3 rebuild `created_at`.
4. **H2** — call `poster_key_is_valid` in `Staging::fetch_posters`.
5. **H3** — timeouts and a body cap on the poster client; consider `buffer_unordered`.
6. **H4** — read `schema_version` from the snapshot instead of the compile-time constant.
7. **T2, T3** — fix the tautological budget assertion; assert the TMDB query in `FakeApi`.
8. **M1-M7** as scheduling allows; **T4/T5** before phase 3 starts.
9. Correct the commit message's "byte-identical before and after, including in dry-run"
   claim, and `plan.md`'s "Defects fixed" row for the same, once C1 lands.

## Plan status

Phase 2 is substantially implemented — 10 of 10 implementation steps have code, and 9 of
15 success criteria are met and tested. Not ready to mark completed: criteria 1 and 10
("writes nothing anywhere, including the live index") are actively false (C1), the size
criterion is unenforced (C2), and criteria for staging permissions, manifest/disk
correspondence and warm-cache posters have no test (T3, T4). Recommend phase 2 stay
`pending` until C1, C2, H1, H2 and T1-T3 are addressed. Phase 3 should not start before
H1 — it is the one that becomes irreversible once a package is published.

## Unresolved questions

1. `REFUSE_BYTES` (48 MiB) was chosen by the user against `MAX_PACKAGE_BYTES` (64 MiB).
   C2 does not propose changing that number — it proposes adding the missing check on the
   real bytes. If fixing the estimate's compression model shifts what libraries are
   accepted, that is a user decision, not an audit one.
2. Should `--dry-run` copy the index at all? It currently VACUUMs a full copy to disk just
   to read counts. `COUNT(*)` on the live read-only connection would be cheaper, but the
   phase spec explicitly requires querying the snapshot (step 3). Flagging, not proposing.
3. `POSTER_ESTIMATE = 32 KiB` cites "measured posters land near 25 KB" with no measurement
   in `plans/` — same pattern as the plan's unsourced "~35 MB". Where does the number come
   from?
