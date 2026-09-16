---
phase: 3
title: "Catalog anywhere: the package reader"
status: pending
priority: P1
effort: "1d"
dependencies: []
---

# Phase 3: Catalog anywhere: the package reader

## Overview
The player fetches the published encrypted package, decrypts it, and reads the
`library.db` inside. This is the other half of running anywhere: phase 2 gets
the bytes of a video from Telegram, this gets the catalog that says which
videos exist.

## Key insight
Nothing here needs designing. `mlib-package-v1` is normative and already
specifies the reader algorithm, and `mediagram export-package` already
publishes packages that satisfy it. This phase implements the reader that was
always the format's other half and has never existed.

The format's own rule is the one to hold on to: **a reader must never decide
"I already have this" from `sha256` alone.** That field is attacker-controlled
— a hostile host can set it to the digest of the copy the reader holds, and a
reader that skips on a match never runs the cipher. Freshness is decided from
`created_at` and the decrypted manifest, after authentication.

## Requirements
- Functional: given a base URL and the package key, the player ends up with a
  `library.db` it can query, and posters it can show.
- Functional: a package that fails authentication is refused and the previous
  catalog is kept. A player with a stale catalog is useful; a player with a
  forged one is not.
- Non-functional: refuse over 64 MB (the tag covers the whole file, so it is
  held whole to verify); never unpack a member that escapes the target
  directory.

## Architecture
```
{base}/latest.json ──> pointer ──> refuse unknown format / wrong key_id
                          │
                          ├─ download {url}, enforce the ceiling
                          ├─ verify sha256 (integrity, not authenticity)
                          ├─ rebuild AAD from five pointer fields
                          ├─ AES-256-GCM open  ──> tar.gz
                          └─ unpack ──> library.db + posters/
```

The associated data is the minified JSON of exactly `format`, `created_at`,
`key_id`, `schema`, `spec`, in that order. A different JSON writer produces
different bytes and the open fails, which is why the spec constrains `key_id`
to lowercase hex — there is nothing in those five fields a writer could escape
differently.

## Related Code Files
- Create: `web/src/package/pointer.ts` (fetch, validate, refuse),
  `web/src/package/open.ts` (AAD, AES-256-GCM, size ceiling),
  `web/src/package/unpack.ts` (tar.gz, path-escape refusal),
  `web/src/package/refresh.ts` (the algorithm end to end, atomic swap)
- Create: `web/test/package.test.ts`
- Modify: `web/src/catalog.ts` (open whichever `library.db` is current)

## Implementation Steps
1. Pointer fetch and validation, refusing in the spec's order: unknown
   `format`, unknown `cipher`, `key_id` mismatch, `bytes` over the ceiling —
   each **before** downloading anything.
2. Download to a temporary file, verify `sha256`. Integrity only; the tag is
   what proves authenticity.
3. Rebuild the associated data and open the cipher. Node's `crypto`
   `aes-256-gcm` with `setAAD`, which Bun implements.
4. Unpack, refusing any member whose resolved path leaves the target
   directory, and any symlink. The Rust `unpack_to` already does this and its
   test cases port directly.
5. Atomic swap: unpack beside the live catalog, then move. A refresh that
   fails mid-way must leave the player with the catalog it had.
6. Freshness from `created_at` and the manifest, never from `sha256`.
7. A round-trip test against a package produced by the real
   `mediagram export-package`, not a fixture written by hand. The two
   implementations must agree or one of them is wrong.

## Success Criteria
- [ ] A package exported by `mediagram export-package` opens, unpacks, and its
      `library.db` answers `PLAYABLE_SQL`
- [ ] A tampered ciphertext byte fails to open
- [ ] A pointer with any of the five identifying fields altered fails to open
- [ ] A pointer whose `sha256` matches the held copy but whose `created_at`
      is older does not short-circuit the check
- [ ] A package over the ceiling is refused before download
- [ ] A tar member pointing outside the target is refused
- [ ] A failed refresh leaves the previous catalog intact and queryable

## Risk Assessment
- **The key is the only protection.** The package carries the private channel
  id and every message id. Losing the key costs the package, never the
  library, but a leaked key exposes the library's shape to whoever holds it.
- **An unsigned pointer.** A hostile or stale host can withhold updates,
  though it cannot pass off content it cannot authenticate. Known limit of
  format 1, recorded in the format document, not fixed here.
- **Two implementations of one format.** Rust writes, TypeScript reads. The
  round-trip test against a real export is what keeps them honest; a fixture
  would only test the reader against itself.
