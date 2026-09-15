---
title: "Prebuilt metadata package for the player"
status: pending
created: 2026-09-15
source: user request 2026-09-15 ("uploader prepares a prebuilt metadata package, publishes it to a simple URL")
relatedSpec: docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md
blockedBy: []
blocks: []
---

# Prebuilt metadata package for the player

The uploader exports one encrypted archive holding the library index plus
artwork, and publishes it to a static HTTPS URL. A player fetches a small
plaintext pointer, downloads the archive, decrypts it, and has a complete
browsable catalog without scanning the channel, without a TMDB key, and
without fetching a single image at runtime.

```
mediagram export-package --publish
        |
        +-- snapshot library.db        (VACUUM INTO, as push-index does)
        +-- collect art/               (TMDB images + captured frames)
        +-- manifest.json              (counts, schema, spec, per-item art map)
        |
        +-> tar.gz -> AES-256-GCM -> prebuilt_mediagram_db_20260616.tar.gz.enc
        +-> latest.json                (plaintext pointer: file, size, sha256)
        |
        +-- publish_cmd runs for each file
                |
                v
        https://example.com/latest.json
        https://example.com/prebuilt_mediagram_db_20260616.tar.gz.enc
```

## Phases

| # | Phase | Status | Priority | Effort | Depends on |
|---|-------|--------|----------|--------|------------|
| 1 | [Package format and manifest](phase-01-package-format-and-manifest.md) | pending | P1 | 0.5d | - |
| 2 | [Artwork capture at add time](phase-02-artwork-capture-at-add-time.md) | pending | P1 | 1d | - |
| 3 | [Export assembly](phase-03-export-assembly.md) | pending | P1 | 1d | 1,2 |
| 4 | [Encryption and archive](phase-04-encryption-and-archive.md) | pending | P1 | 1d | 1 |
| 5 | [Publish and latest pointer](phase-05-publish-and-latest-pointer.md) | pending | P2 | 0.5d | 3,4 |
| 6 | [Docs and player integration notes](phase-06-docs-and-player-integration.md) | pending | P2 | 0.5d | 5 |

Phases 1 and 2 are independent and can run in parallel. Phase 4 needs only
phase 1, so it can start while phase 2 is still running.

## Decisions (settled with the user, 2026-09-15)

- **Contents**: index snapshot plus artwork, so the player needs no TMDB
  key and fetches no images at runtime.
- **Publishing**: mediagram writes the files, then runs a configured
  command (`publish_cmd`) per file. rclone, scp, rsync and aws-cli all work
  without mediagram learning any storage API or holding storage credentials.
- **Protection**: the archive is encrypted. The package contains the private
  channel id and every message id, so a leaked URL must yield ciphertext
  only. Hosting stays a dumb static file server.
- **Discovery**: a fixed plaintext `latest.json` names the newest archive,
  its size and its sha256. The dated filename stays, so old packages remain
  fetchable and a player can pin one.
- **The Telegram pinned index stays.** It is the disaster-recovery copy and
  is unaffected; this package is an additional, player-facing convenience.

## Constraints discovered in the codebase

- `index::snapshot::{checkpoint, snapshot_to}` already produce a safe
  point-in-time copy of `library.db`; the export reuses them rather than
  inventing a second snapshot path.
- **Source files are gone by export time.** `add` records the source path in
  `meta` under `source:{set_id}` and deletes it when the set completes
  (`upload/pipeline.rs`, `commands/add.rs`), so artwork that must come from
  the video itself has to be captured during `add`, not during export. This
  is why artwork capture is its own phase and precedes assembly.
- The TMDB layer is a `TmdbApi` trait behind `DiskCachedApi`, so the export
  reuses the cache for image lookups. TMDB responses are currently parsed
  into a minimal struct with no `poster_path`/`backdrop_path`; phase 2 adds
  those fields.
- `mlib_spec::schema::SCHEMA_VERSION` and `mlib_spec::SPEC_VERSION` identify
  what a package contains; both go in the manifest and in `latest.json`.

## Verified dependencies

Checked against crates.io on 2026-09-15. The research report
(`plans/reports/researcher-260915-1956-prebuilt-metadata-package-crypto-and-packaging-report.md`)
cites `aes-gcm` 0.10.3 and `flate2` 1.0.31; both are outdated and the
`aes-gcm` 0.11 API differs from its 0.10 snippets.

| Crate | Version | Use |
|---|---|---|
| `aes-gcm` | 0.11.1 | AES-256-GCM |
| `tar` | 0.4.46 | archive writer |
| `flate2` | 1.1.10 | gzip |
| `image` | 0.25.10 | poster downscaling |
| `base64` | 0.23.1 | key in config |
| `rand` | 0.10.2 | `OsRng` |

## Key dependencies

- A static HTTPS host (any object store or web server) and whatever CLI
  tool publishes to it.
- A 32-byte package key shared between the uploader config and the player.
- TMDB key for artwork of movies and episodes; tutorials use captured
  frames instead.

## Size ceiling

A reader verifies the GCM tag over the whole file, holding ciphertext and
plaintext at once, so a package costs roughly twice its size in transient
heap on the player. The export refuses to exceed 64 MB and warns above
48 MB. The measured estimate for a 300-title library with posters and
backdrops is about 35 MB, so the ceiling is generous without risking an
out-of-memory failure on a television.

## Success (whole plan)

- `mediagram export-package` on a library with movies, episodes and
  tutorials produces an archive and a `latest.json` whose sha256 matches the
  archive on disk.
- Decrypting and unpacking the archive by hand yields a `library.db` that
  opens in `sqlite3`, an `art/` directory, and a `manifest.json` whose
  counts match the database.
- Running the export twice with no library changes produces a byte-identical
  inner `tar.gz`. The encrypted file differs every run because the nonce is
  fresh, which is required; see phase 4 for which hash covers what.
- A wrong key fails loudly on decrypt rather than producing garbage.
- `--dry-run` reports what would be written and published, touching neither
  the network nor the publish command.
- `cargo test` green; every file under `src/` within the 200-line rule.

## Cross-plan relationship

The tutorial/course design
(`docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md`) is
approved but has no implementation plan yet. Relationship is soft, in one
direction only:

- The export copies `library.db` wholesale, so it carries new columns
  automatically and needs no change when the tutorial schema lands.
- Artwork keyed by collection id (`cid`) and frame capture for lessons only
  become reachable once `add-course` exists. Phase 2 therefore treats art
  sources as pluggable and degrades to "no art for this set" rather than
  failing, so the two efforts can land in either order.
- **Both efforts add a schema migration**: the tutorial work adds
  `sets.chap`, phase 2 here adds `sets.art_key`. Whichever lands second
  takes the next version number and appends its own migration group; they
  do not conflict, but the second one to land must not reuse the first
  one's number.

## Open questions

Resolved in phase files where they belong. Nothing blocking at plan level.
