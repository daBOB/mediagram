---
phase: 1
title: "Package format and manifest"
status: pending
priority: P1
effort: "0.5d"
dependencies: []
---

# Phase 1: Package format and manifest

## Overview
Define the package as a format before any code writes one: the archive
layout, the manifest inside it, the plaintext pointer beside it, and the
version fields that let a player decide whether it can read what it found.

## Key insight
Three version numbers already exist or are being introduced, and they mean
different things. Conflating them would strand a player.

| Field | Answers | Owner |
|---|---|---|
| `format` | Is this package layout readable? | this phase |
| `schema` | Is this `library.db` table layout readable? | `mlib_spec::schema::SCHEMA_VERSION` |
| `spec` | Which caption version produced these rows? | `mlib_spec::SPEC_VERSION` |
| `key_id` | Is the key I hold the one this was encrypted with? | this phase |

## Requirements
- Functional: typed manifest and pointer structures with a stable field
  order; a reader can reject an unreadable package before downloading it.
- Non-functional: the pointer is published in the clear, so it must reveal
  nothing about the library's contents.

## Architecture

Inside the archive:

```json
{
  "format": 1,
  "created_at": 1781827200,
  "schema": 2,
  "spec": 3,
  "sets": 312,
  "parts": 468,
  "art": [
    {"key": "tmdb-693134", "poster": "art/tmdb-693134-poster.jpg",
     "backdrop": "art/tmdb-693134-backdrop.jpg", "sha256": "…"}
  ]
}
```

Beside the archive, at a fixed URL, `latest.json`:

```json
{
  "format": 1,
  "created_at": 1781827200,
  "file": "prebuilt_mediagram_db_20260616.tar.gz.enc",
  "url": "https://example.com/prebuilt_mediagram_db_20260616.tar.gz.enc",
  "bytes": 48127744,
  "sha256": "…of the encrypted file…",
  "cipher": "aes-256-gcm",
  "key_id": "9f2c41ab",
  "schema": 2,
  "spec": 3
}
```

`key_id` is the first four bytes of `sha256(key)`, hex. It is not secret and
not a key check in the cryptographic sense: it lets a player say "this
package was made with a different key" instead of downloading fifty
megabytes and failing to decrypt. Everything descriptive stays inside the
encrypted archive.

Naming: `prebuilt_mediagram_db_YYYYMMDD.tar.gz.enc`, with `-2`, `-3` and so
on appended for a second export on the same day, so a file is never
silently replaced and a player can pin one.

## Related Code Files
- Create: `crates/mlib-spec/src/package.rs` (`PackageManifest`,
  `ArtEntry`, `LatestPointer`, `PACKAGE_FORMAT`, file-naming helper)
- Modify: `crates/mlib-spec/src/lib.rs` (export the module)

## Implementation Steps
1. Define the structures above with `serde`, field order fixed as written,
   and `PACKAGE_FORMAT: u32 = 1`.
2. Implement the dated file name helper, including same-day suffixes.
3. Implement `key_id(key: &[u8; 32]) -> String` over `sha256`.
4. Write the reader-side compatibility rule as a function on the pointer:
   a package is readable when `format` is known and `schema` is one the
   reader supports. Version comparison lives in one place, not scattered
   through the player's future code.
5. Round-trip tests, plus a test asserting the pointer serializes with no
   field that names a title, a set, a chat or a message.

## Success Criteria
- [ ] Manifest and pointer round-trip byte-identically
- [ ] `key_id` is stable for a key and differs for a different key
- [ ] Same-day exports produce distinct file names
- [ ] A pointer with an unknown `format` is rejected by the compatibility check
- [ ] A test proves the pointer contains no library content

## Risk Assessment
- Publishing anything descriptive in the clear undoes the encryption
  decision. Mitigation: the pointer's fields are enumerated here and a test
  fails if that set grows.
- Version conflation. Mitigation: the table above goes into the spec
  document verbatim in phase 6.
