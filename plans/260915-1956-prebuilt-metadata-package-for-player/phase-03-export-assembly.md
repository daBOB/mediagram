---
phase: 3
title: "Export assembly"
status: pending
priority: P1
effort: "1d"
dependencies: [1, 2]
---

# Phase 3: Export assembly

## Overview
Assemble the package payload: a consistent snapshot of `library.db`, the
artwork each set resolves to, and a manifest describing both. Produces a
staging directory; phases 4 and 5 archive, encrypt and publish it.

## Requirements
- Functional: `mediagram export-package [--out <dir>] [--dry-run]` produces
  the staging tree; the manifest counts match the snapshot; every art file
  referenced by the manifest exists and every file present is referenced.
- Non-functional: the snapshot must not capture a half-written database; a
  concurrent `add` must neither block the export nor corrupt it.

## Architecture
```
<staging>/
  manifest.json
  library.db        VACUUM INTO copy, WAL checkpointed first
  art/
    tmdb-693134-poster.jpg
    set-01JQ8F2K9M4XZ-poster.jpg
```

Assembly order matters: snapshot first, then derive the art map from that
snapshot rather than from the live database, so the manifest can never
reference a set the snapshot does not contain.

## Related Code Files
- Create: `crates/mediagram/src/commands/export_package.rs` (orchestration
  only), `crates/mediagram/src/export/stage.rs` (staging tree),
  `crates/mediagram/src/export/art_map.rs` (sets to art files, pure),
  `crates/mediagram/src/export/fetch_art.rs` (TMDB image download)
- Modify: `crates/mediagram/src/main.rs`, `crates/mediagram/src/lib.rs`

## Implementation Steps
1. Reuse `index::snapshot::checkpoint` then `snapshot_to` into the staging
   directory, with a per-process temp name as `push_index` does.
2. Open the snapshot read-only and query sets, counts, schema and spec
   versions from it. Everything downstream reads the snapshot, never the
   live database.
3. Derive the art map from the snapshot's `art_key` column. Missing art is
   recorded as absent, not as an error.
4. Fetch TMDB images for `tmdb-*` keys through the existing disk cache,
   writing them into `art/`. Downloads are capped in count and total size,
   both reported.
5. Copy locally captured art (`set-*`, `cid-*`) from the art cache.
6. Write `manifest.json` per the phase 1 schema, with counts taken from the
   snapshot and a sha256 per art file.
7. `--dry-run` prints the table of what would be included, with totals, and
   performs no downloads and no writes outside a temp directory.

## Success Criteria
- [ ] Manifest set count equals `SELECT COUNT(*) FROM sets` in the snapshot
- [ ] Every manifest art entry exists on disk and every file in `art/` is in the manifest
- [ ] An `add` running concurrently with the export leaves the snapshot valid and openable
- [ ] A library with no artwork at all still exports successfully
- [ ] TMDB unreachable degrades to a package with fewer images, not a failure
- [ ] The staging directory is removed on success and on failure

## Risk Assessment
- A large library makes the first export slow because every poster is
  fetched. Mitigation: images are cached on disk, so later exports fetch
  only what is new; the command reports how many it fetched.
- Disk space: staging plus archive roughly doubles the package size on
  disk. Mitigation: report required space up front; stage under the data
  directory, not `/tmp`, which is often a tmpfs sized to RAM.
