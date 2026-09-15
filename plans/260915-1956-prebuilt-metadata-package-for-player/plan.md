---
title: "Prebuilt metadata package for the player"
status: pending
created: 2026-09-15
revised: 2026-09-15
source: user request 2026-09-15 ("uploader prepares a prebuilt metadata package, publishes it to a simple URL")
relatedSpec: docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md
blockedBy: []
blocks: []
---

# Prebuilt metadata package for the player

The uploader exports one encrypted archive holding the library index plus
posters, and publishes it to a static HTTPS URL. A player fetches a small
plaintext pointer, downloads the archive, decrypts it, and has a complete
browsable catalog without scanning the channel and without a TMDB key.

```
mediagram export-package --publish
        |
        +-- snapshot library.db       (read-only copy, no writes to the index)
        +-- posters from the TMDB cache already on disk
        +-- manifest.json
        |
        +-> tar.gz -> AES-256-GCM (pointer fields as associated data)
        +-> latest.json
        |
        +-- publish_cmd runs per file, argv only, exit code checked
                |
                v
        https://example.com/latest.json
        https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc
```

## Phases

| # | Phase | Status | Priority | Effort | Depends on |
|---|-------|--------|----------|--------|------------|
| 1 | [Package format and manifest](phase-01-package-format-and-manifest.md) | pending | P1 | 0.5d | - |
| 2 | [Export, archive and encrypt](phase-02-export-archive-and-encrypt.md) | pending | P1 | 1d | 1 |
| 3 | [Publish, pointer and spec](phase-03-publish-pointer-and-spec.md) | pending | P2 | 1d | 2 |

## Decisions (settled with the user)

- **Contents**: index snapshot plus posters, so the player needs no TMDB key.
- **Publishing**: mediagram writes the files and runs a configured command
  per file. No storage API, no storage credentials in mediagram.
- **Protection**: the archive is encrypted. It carries the private channel
  id and every message id, so a leaked URL must yield ciphertext only.
- **Artwork source**: TMDB only, read at export time from the responses
  already cached on disk. Nothing in the upload path changes. Content with
  no TMDB id simply has no poster.
- **Scope**: the URL package is kept, but everything the red team proved
  unnecessary or broken is cut (see below).
- **The Telegram pinned index stays.** It remains the recovery copy; this
  package is an additional, player-facing convenience.

## Cut after the red team review

| Cut | Why |
|---|---|
| Artwork capture during `add` | Would have read a file `run_set` already deleted, and `resume` never got a hook, so interrupted uploads silently lost art forever |
| `sets.art_key` column and its migration | `MIGRATIONS` is a flat list replayed on every open, guarded by a test asserting `IF NOT EXISTS`; an `ALTER TABLE` there breaks every command on an existing database. The value is derivable in one SQL expression anyway |
| Deterministic tar and its test | Unachievable: `snapshot_to` writes `last_push_at` before vacuuming, so the payload differs every run. No consumer ever sees the inner archive |
| Per-file sha256 in the manifest | The GCM tag already covers every byte; a corrupt member cannot reach a reader |
| `gen-key` subcommand | Config loads and validates before dispatch, so the command meant to fill an empty config required a valid one. A documented one-liner replaces it |
| `image` crate | TMDB serves pre-sized images, so nothing needs decoding or resizing. The crate also requires Rust 1.88 against this workspace's declared 1.87 |
| Same-day filename counter | Its only input was local state, so a cleaned output directory silently overwrote a published archive. The ciphertext hash prefix names files instead |
| Backdrops | Most of the package size for the least benefit; posters alone keep the package small |

## Defects fixed in the revised phases

| Defect | Fix | Phase |
|---|---|---|
| Export mutated the live index, even in `--dry-run` | A snapshot variant that writes nothing, opened without the migration-running helper | 2 |
| Rollback guard trusted a value the attacker writes | The pointer's identifying fields are the AEAD associated data, so a replayed archive fails its tag | 1, 2 |
| Shell publish reported success for failed uploads | Argv only, no shell, exit code checked, child environment scrubbed of `MEDIAGRAM_*` | 3 |
| Size ceiling fired after all the work | Estimated before any download, from the poster list | 2 |
| `tmdb-{id}` collided across TMDB's movie and TV id spaces | Keys carry the kind: `tmdb-movie-{id}`, `tmdb-tv-{id}` | 1 |
| Art filenames derived from an unvalidated `set_id` | Keys are validated against a strict charset before touching a path | 1 |
| Crate APIs specified against versions that do not compile | Nonce and key bytes come from `getrandom`; no `rand` dependency | 2 |

## Pre-existing bug found during review, not caused by this plan

`caption_codec::parse` is a bare `serde_json::from_str` with no field
validation (`crates/mlib-spec/src/caption_codec.rs:75-89`), and `rescan`
writes `caption.set` straight into `sets.set_id`
(`crates/mediagram/src/index/set_row.rs:47`). Any message in the channel can
therefore set an arbitrary `set_id`, including one containing path
separators. Nothing in the shipped code writes that value to a path today,
so it is latent rather than exploitable, but this package would have made it
reachable. Phase 1 validates keys defensively; the parser itself should be
fixed separately, in the crate that owns it.

## Verified dependencies

Checked against crates.io on 2026-09-15.

| Crate | Version | Use |
|---|---|---|
| `aes-gcm` | 0.11.1 | AES-256-GCM |
| `tar` | 0.4.46 | archive writer |
| `flate2` | 1.1.10 | gzip |
| `base64` | 0.23.1 | key in config |
| `getrandom` | latest 0.3.x | nonce and key bytes |

`image` and `rand` are not needed. The research report
(`plans/reports/researcher-260915-1956-prebuilt-metadata-package-crypto-and-packaging-report.md`)
cites `aes-gcm` 0.10.3 and `flate2` 1.0.31; both are outdated and its 0.10
snippets do not match the 0.11 API.

## Size ceiling

A reader verifies the GCM tag over the whole file, so a package costs
roughly twice its size in transient heap, and more on Android where the
cipher buffers internally. Posters only, at TMDB's w342 size, put a
300-title library near 8 MB. The export warns above 24 MB and refuses above
48 MB, checked before anything is downloaded.

## Success (whole plan)

- `export-package` produces an archive and a `latest.json` whose sha256 and
  byte count match the archive on disk.
- Decrypting by hand with the documented command yields a `library.db` that
  opens in `sqlite3`, a `posters/` directory, and a manifest whose counts
  match the database.
- The live `library.db` is byte-identical before and after an export,
  including with `--dry-run`.
- A wrong key, a flipped bit, or a pointer whose fields were edited all fail
  loudly instead of yielding partial data.
- A publish command that fails makes the run fail, and no pointer is
  published for an archive that is not there.
- `cargo test` green; every file under `src/` within the 200-line rule.

## Red Team Review

### Session — 2026-09-15
**Findings:** 38 raised across 4 reviewers (security adversary, failure mode
analyst, assumption destroyer, scope and complexity critic), deduplicated to
16 distinct issues.
**Severity breakdown:** 6 Critical, 6 High, 4 Medium after deduplication.
**Disposition:** 14 accepted, 2 surfaced to the user as decisions.

| # | Finding | Severity | Disposition | Applied to |
|---|---------|----------|-------------|------------|
| 1 | Package duplicates the pinned index | Critical | User decision: keep the URL package | plan.md |
| 2 | `art_key` migration breaks every command | Critical | Accept, cut the column | cut |
| 3 | Art capture reads a deleted file; `resume` uncovered | Critical | Accept, cut capture | cut |
| 4 | Shell publish reports false success | Critical | Accept, argv only | 3 |
| 5 | `set_id` from captions reaches a path | Critical | Accept, validate keys | 1 |
| 6 | Rollback guard trusts attacker-written data | Critical | Accept, bind via AAD | 1, 2 |
| 7 | Export mutates the live index | High | Accept, read-only snapshot | 2 |
| 8 | Determinism criterion unachievable | High | Accept, criterion removed | cut |
| 9 | Cross-plan schema version collision | High | Accept, no migration needed now | cut |
| 10 | Size ceiling fires after the work, heap model optimistic | High | Accept, estimate first, lower ceiling | 2 |
| 11 | `tmdb-{id}` collides across movie and TV | High | Accept, key carries kind | 1 |
| 12 | TMDB cache miss on a different query string | High | Accept, reuse the exact cached path | 2 |
| 13 | Per-file sha256 redundant under AEAD | Medium | Accept, cut | cut |
| 14 | `gen-key` cannot run without a config | Medium | Accept, cut | cut |
| 15 | `image` crate breaks MSRV 1.87 | Medium | Accept, dependency removed | cut |
| 16 | Example `schema`/`spec` values wrong | Medium | Accept, examples marked illustrative | 1 |

Reports: `reports/` in this directory.

## Open questions

1. Should `latest.json` be signed? The AEAD binding makes a replayed archive
   fail, so signing now only protects against denial and metadata edits an
   attacker gains nothing from. Deferred unless a second reader appears.
2. Key rotation is manual: re-export, update the player. No versioning
   scheme until there is more than one reader.
