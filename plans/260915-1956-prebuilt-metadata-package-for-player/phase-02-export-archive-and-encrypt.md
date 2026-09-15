---
phase: 2
title: "Export, archive and encrypt"
status: completed
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 2: Export, archive and encrypt

## Overview
Assemble the payload and turn it into one encrypted file: a snapshot of
`library.db` that leaves the live index untouched, posters pulled from the
TMDB responses already cached on disk, a manifest, then tar, gzip and
AES-256-GCM.

## Key insight
The obvious reuse is wrong. `index::snapshot::snapshot_to` writes
`meta.last_push_at` into the **live** database before vacuuming
(`crates/mediagram/src/index/snapshot.rs:29-30`), and reaching it through
`index::db::open` also replays every migration and rewrites
`meta.schema_version` (`crates/mediagram/src/index/db.rs:24-34`). An export
built on those two would mutate the index it claims to be reading, in
`--dry-run` as much as in a real run, and would poison the freshness signal
that the pinned recovery copy depends on.

## Requirements
- Functional: `mediagram export-package [--out <dir>] [--dry-run]` produces
  the encrypted archive; manifest counts match the snapshot; the live
  database is byte-identical before and after.
- Non-functional: a concurrent `add` must neither block the export nor
  corrupt it; the package must stay small enough for a television to decrypt.

## Architecture
```
library.db --(read-only copy)--> staging/library.db
tmdb-cache/*.json --(poster_path)--> download w342 --> staging/posters/
                                                  --> staging/manifest.json
staging/ -> tar -> gzip -> AES-256-GCM(aad = pointer fields) -> *.tar.gz.enc
```

Crates, verified against crates.io on 2026-09-15:

| Crate | Version | Use |
|---|---|---|
| `aes-gcm` | 0.11.1 | AES-256-GCM |
| `tar` | 0.4.46 | archive writer |
| `flate2` | 1.1.10 | gzip |
| `getrandom` | 0.3.x | nonce and key bytes |

`getrandom` rather than `rand`: it is what `rand` calls anyway, it has no
API churn across the versions in play, and the plan needs exactly 12 or 32
random bytes. The `image` crate is not used; TMDB serves pre-sized images.

**Posters without a schema change.** `DiskCachedApi` stores the entire raw
TMDB response as JSON on disk (`metadata/tmdb_client.rs:126-149`), and those
payloads already contain `poster_path`. Adding the field to the typed
response with `#[serde(default)]` makes existing cache files usable with no
re-fetch. The export must request the **same path and query string** that
`resolve` used (`/movie/{id}` and `/tv/{id}` with
`append_to_response=external_ids`, `metadata/resolve.rs:128-132`), because
the cache key is a hash of path plus sorted query; a different query string
silently misses every entry and hits the network.

The export therefore works with no TMDB key at all, using only what is
cached. With a key it fills gaps. A per-title failure, including a 401 or a
rate limit, costs that title its poster and nothing more.

## Related Code Files
- Create: `crates/mediagram/src/commands/export_package.rs` (orchestration),
  `crates/mediagram/src/export/stage.rs` (staging tree, read-only snapshot),
  `crates/mediagram/src/export/posters.rs` (cache lookup and download),
  `crates/mediagram/src/export/archive.rs` (tar.gz),
  `crates/mediagram/src/export/encrypt.rs` (AES-256-GCM, key parsing)
- Modify: `crates/mediagram/src/index/snapshot.rs` (a copy variant that
  writes nothing), `crates/mediagram/src/commands/push_index.rs` (record
  `last_push_at` itself, keeping today's behaviour),
  `crates/mediagram/src/metadata/tmdb_types.rs` (`poster_path`),
  `crates/mediagram/src/config.rs` (`package_key`), `Cargo.toml`,
  `config.example.toml`

## Implementation Steps
1. Split `snapshot_to` into a pure copy (`checkpoint` then `VACUUM INTO`,
   no `meta` write) and keep the timestamp write in `push_index`, which is
   the command that actually pushes. Existing behaviour is preserved where
   it belongs and the export gets a genuinely read-only path.
2. Open the live database for the export with a plain connection rather
   than `db::open`, so no migration runs and no version is rewritten.
3. Query sets, counts and the distinct `(kind, tmdb)` pairs from the
   **snapshot**, never the live database.
4. Estimate the package size from the poster count before downloading
   anything: warn above 24 MB, refuse above 48 MB, naming the count. The
   check has to come first, because refusing after fetching hundreds of
   images wastes the entire run.
5. Resolve each poster through the cached TMDB client, download `w342` into
   `staging/posters/`, and record it in the manifest. Errors are per title.
6. Write `manifest.json`, then tar (manifest first) and gzip.
7. Parse `package_key` from config as base64 into exactly 32 bytes, with an
   error naming the expected length. Keep it out of `Debug` as `api_hash`
   and `tmdb_key` already are (`config.rs:33-47`).
8. Encrypt with a fresh 12-byte nonce from `getrandom`, prepending the
   nonce, with the phase 1 associated data and a 128-bit tag.
9. Create the staging directory and every file in it with mode 0600, and
   the directory 0700, matching how the session directory is already
   protected (`telegram/client.rs:172-187`). Remove the tree on success and
   on failure.
10. `--dry-run` prints the table of what would be included with totals, and
    writes nothing anywhere, including the live index.

## Success Criteria
- [ ] `sha256` of `library.db` is identical before and after an export, and after `--dry-run`
- [ ] `meta.last_push_at` and `meta.schema_version` are unchanged by an export
- [ ] `push-index` still records `last_push_at` exactly as it does today
- [ ] Manifest set count equals `SELECT COUNT(*) FROM sets` in the snapshot
- [ ] Every manifest poster exists on disk and every file in `posters/` is in the manifest
- [ ] An export with no `tmdb_key` and a warm cache still produces posters
- [ ] TMDB returning 401 or 429 for one title costs that title its poster only
- [ ] A movie and a show sharing a TMDB id get different posters
- [ ] The size check refuses before any download when the estimate is over
- [ ] Round trip encrypt then decrypt returns the exact bytes
- [ ] Decryption fails when any associated-data field is altered
- [ ] A flipped bit anywhere fails, never partially succeeds
- [ ] Two consecutive exports use different nonces
- [ ] Staging files are 0600 and the directory 0700
- [ ] The key never appears in logs, errors or `Debug` output

## Risk Assessment
- **Nonce reuse destroys GCM's guarantees.** Mitigation: a fresh nonce per
  encryption from the OS generator, never stored or derived; a test asserts
  two runs differ.
- **A concurrent `add`** could leave the snapshot mid-write. Mitigation:
  `VACUUM INTO` runs in a read transaction, which is what actually provides
  the consistency; the checkpoint only folds the WAL in first.
- **Key loss** makes the package unreadable, though never the media: the
  library is in Telegram and `library.db` is local and pinned. Documented,
  with the key generated by a documented one-liner the operator stores
  before publishing.
- **Plaintext on disk** during staging is the same class of secret as the
  session file, hence the same permissions and prompt removal.

## Completion notes (2026-09-15)

Built test-first across six modules. Review and edge-case rounds followed,
adding 41 probes.

**The read-only guarantee was wrong when first committed.** `copy_index`
called `wal_checkpoint(TRUNCATE)` before vacuuming, and a checkpoint folds
write-ahead pages into the main file, which changes it. The first end-to-end
check missed this because closing the last connection checkpoints anyway, so
the only observable case is the concurrent one the design exists for.
Measured directly: with a second connection holding the index open, the
checkpoint changed the file; a read-only connection running `VACUUM INTO`
alone left it byte-identical and still captured every row through the
uncheckpointed log. The checkpoint is gone and the connection now opens with
`SQLITE_OPEN_READ_ONLY`, so the export is incapable of writing rather than
merely careful not to. A test reproduces the original defect.

Also applied from the review:
- The reader's ceiling is now enforced on the bytes actually produced, not
  only on the estimate. The estimate over-counts the uncompressed index and
  under-counts posters, so it can pass while the real package exceeds what a
  reader will accept.
- The draft pointer is written beside the package. Phase 3 completes it
  rather than recomputing `created_at`, which would produce a tag failure
  indistinguishable from an attack.
- `poster_key_is_valid`, added in phase 1, had no caller; it now gates every
  key before it reaches a path.
- Poster downloads have a timeout and a size cap.
- `pack_dir` skips symlinks, which otherwise pulled outside files into the
  package.

Independently verified: a package decrypts under Python's `cryptography`
using associated data rebuilt from the pointer, the manifest is the first
member, its counts match the database, and the index inside opens as SQLite.
That is the contract a player implements, checked across two implementations.

404 tests pass, 1 ignored; clippy and rustfmt clean.
