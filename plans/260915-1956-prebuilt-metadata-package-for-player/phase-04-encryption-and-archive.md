---
phase: 4
title: "Encryption and archive"
status: pending
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 4: Encryption and archive

## Overview
Turn the staging directory into one encrypted file: a deterministic
`tar.gz`, then AES-256-GCM with a fresh nonce, plus the key handling and
the size ceiling that keep a television able to open it.

## Key insight
Two different things get hashed and they must not be confused.

| Hash | Covers | Purpose | Stable across runs? |
|---|---|---|---|
| manifest entries | each art file, plaintext | detect a corrupt member | yes |
| `latest.json` `sha256` | the encrypted file | detect a bad download | no, the nonce is fresh each run |

The inner `tar.gz` is deterministic, so an unchanged library produces an
identical archive and a player can compare the manifest cheaply. The
encrypted file changes every run by design, because a fresh nonce is
mandatory. Determinism claims in this plan always refer to the inner
archive.

## Requirements
- Functional: deterministic tar.gz; AES-256-GCM with a 96-bit random nonce
  prepended and a 128-bit tag; key from config as base64; a documented
  hand-decryption path.
- Non-functional: the package must fit comfortably in an Android TV app's
  heap, because the reader verifies the tag over the whole file.

## Architecture
```
staging/ --> tar (mtime 0, uid 0, gid 0, mode 0644, sorted) --> gzip
         --> [ nonce 12 bytes | ciphertext | tag 16 bytes ]
         --> prebuilt_mediagram_db_20260616.tar.gz.enc
```

Verified crate versions, checked against crates.io on 2026-09-15. The
research report's snippets target `aes-gcm` 0.10 and `flate2` 1.0; both are
outdated and the 0.11 API differs.

| Crate | Version | Use |
|---|---|---|
| `aes-gcm` | 0.11.1 | AES-256-GCM |
| `tar` | 0.4.46 | archive writer |
| `flate2` | 1.1.10 | gzip, miniz_oxide backend |
| `base64` | 0.23.1 | key in config |
| `rand` | 0.10.2 | `OsRng` for key and nonce |

Nonce and key come from `OsRng`, not `ThreadRng`: key material should come
from the operating system generator, and the cost is irrelevant here.

## The footgun the research missed
On Android, `CipherInputStream` swallows `AEADBadTagException` and returns a
truncated stream instead of failing. A player that decrypts through it
would silently accept tampered or corrupt data. The specification in phase
6 must require `Cipher.doFinal` over the whole ciphertext, which returns
plaintext only after the tag verifies.

That requirement is what sets the size ceiling. `doFinal` holds ciphertext
and plaintext in memory simultaneously, so a package costs a reader roughly
twice its size in transient heap. Android TV devices commonly cap an app's
heap near 192-256 MB, so the ceiling has to be well under that:

| Package size | Transient heap for `doFinal` | Verdict |
|---|---|---|
| 35 MB (300 titles, measured estimate) | ~70 MB | comfortable |
| 64 MB | ~128 MB | ceiling, warn above 48 MB |
| 256 MB | ~512 MB | exceeds a TV app's heap, would crash |

The export therefore refuses to produce a package over 64 MB and names the
largest artwork contributors. If a library ever outgrows that, the fix is a
format version 2 with chunked framing, not a bigger single blob; the
`format` field exists so that day is a version bump rather than a break.

## Related Code Files
- Create: `crates/mediagram/src/export/archive.rs` (deterministic tar.gz),
  `crates/mediagram/src/export/encrypt.rs` (AES-256-GCM, key parsing)
- Modify: `crates/mediagram/src/config.rs` (`package_key`),
  `crates/mediagram/Cargo.toml`, `config.example.toml`

## Implementation Steps
1. Write the archive: entries sorted by path, `mtime` 0, `uid`/`gid` 0,
   mode 0644, `manifest.json` first so a reader can stop early.
2. Assert determinism in a test: archive the same tree twice, compare
   sha256 of the gzip output.
3. Parse `package_key` from config as base64 into exactly 32 bytes, with an
   error naming the expected length when it is wrong. Never log it, and
   keep it out of `Debug` as `api_hash` and `tmdb_key` already are.
4. Add `mediagram gen-key`, printing a fresh base64 key and its `key_id`,
   so the operator never invents one by hand.
5. Encrypt: fresh 12-byte nonce from `OsRng`, prepend it, 128-bit tag.
6. Warn above 48 MB and refuse above 64 MB, naming the size and the
   largest artwork contributors so trimming is obvious.
7. Document the hand-decryption command, and test it in CI against a real
   package so the documentation cannot rot.

## Success Criteria
- [ ] The same staging tree produces a byte-identical tar.gz twice
- [ ] A round trip through encrypt then decrypt returns the exact bytes
- [ ] A flipped bit anywhere in the file makes decryption fail, never partially succeed
- [ ] The wrong key fails with a clear error, not a panic
- [ ] Two consecutive exports use different nonces
- [ ] A package over the ceiling is refused with an actionable message
- [ ] The key never appears in logs, errors or `Debug` output
- [ ] The documented hand-decryption command reproduces the staging tree

## Risk Assessment
- **Nonce reuse destroys GCM's guarantees**: reusing a key and nonce
  exposes the authentication key and allows forgery. Mitigation: the nonce
  is generated per encryption from `OsRng` and never stored or derived;
  a test asserts two runs differ.
- **Key loss means an unreadable package**, though never lost media: the
  library itself is in Telegram and `library.db` is local and pinned.
  Mitigation: say so plainly in the docs, and make `gen-key` print the key
  once with a note to store it before publishing.
- **Key rotation**: `key_id` in the pointer lets a player tell an old
  package from a new one. Rotating means re-exporting and updating the
  player; old archives stay readable only with the old key. Documented, not
  automated.
- **Rollback**: an attacker who can serve files, or a stale cache, could
  offer an older pointer. Mitigation: the reader algorithm requires
  refusing a `created_at` older than the package it already holds.
