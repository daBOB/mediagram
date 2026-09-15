---
phase: 3
title: "Publish, pointer and spec"
status: pending
priority: P2
effort: "1d"
dependencies: [2]
---

# Phase 3: Publish, pointer and spec

## Overview
Get the archive to the URL, publish the pointer that names it, and write the
specification a player author implements against.

## Key insight
The previous design ran the publish command through a shell so operators
could pipe or chain. A POSIX shell reports the exit status of the **last**
element of a pipeline, and `pipefail` is off by default, so
`rclone copy … | tee -a publish.log` returns success when the upload failed.
The pointer would then be published for an archive that is not there. Argv
execution removes the class of bug entirely and matches how this codebase
already runs subprocesses (`media/remux.rs:38-45`).

## Requirements
- Functional: `publish_cmd` is an argv array with a `{file}` placeholder,
  run once per file, exit code checked; the pointer is published only after
  the archive succeeds; `--dry-run` prints the exact commands and runs none.
- Non-functional: mediagram holds no storage credentials and speaks no
  storage API; the child process does not inherit mediagram's secrets.

## Architecture
```
config.toml
  publish_cmd      = ["rclone", "copy", "{file}", "r2:mediagram/"]
  publish_base_url = "https://example.com/"
  package_key      = "<32 bytes base64, see phase 2>"

published layout
  https://example.com/latest.json                     <- fixed, plaintext
  https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc
```

Ordering is a correctness property: archive first, pointer second, so a
reader never sees a pointer to a file that is not there. A reader that
fetches mid-publish sees the previous pointer and the previous archive,
which is still present.

The child process runs with `MEDIAGRAM_*` variables removed from its
environment. The command needs its own storage credentials, not mediagram's
Telegram hash or TMDB key.

## Reader algorithm, as the spec will state it
1. Fetch `{base}/latest.json`. Reject an unknown `format`.
2. Reject a `created_at` older than the package already held, and any
   `created_at` implausibly far in the future. This is a cheap first filter;
   the real protection is step 6.
3. Stop if `key_id` does not match the held key, before downloading.
4. Skip the download when `sha256` matches the package already held. Compare
   the hash, never the filename.
5. Download, enforcing a size limit from `bytes`, and verify sha256.
6. Reconstruct the associated data from the pointer's five identifying
   fields and decrypt with `Cipher.doFinal` over the whole ciphertext.
   **Never `CipherInputStream`**: on Android it swallows
   `AEADBadTagException` and yields truncated plaintext. A failure means a
   wrong key, a corrupt file, or an edited pointer, and must be reported.
7. Unpack, refusing any member whose path is absolute, contains `..`, or is
   a link. Read `manifest.json` and confirm its `created_at` matches the
   pointer and its `schema` is supported.
8. Open `library.db` read-only, use `PLAYABLE_SQL` to list what is playable,
   and stream parts by `(chat_id, message_id)`.

Steps 6 and 7 are where a reader gets this wrong, so both carry their
rationale into the specification rather than being left as "unpack".

## Related Code Files
- Create: `crates/mediagram/src/export/publish.rs` (argv execution),
  `crates/mediagram/src/export/latest.rs` (pointer generation, pure),
  `docs/mlib-package-v1.md`
- Modify: `crates/mediagram/src/config.rs` (two new optional keys),
  `crates/mediagram/src/commands/export_package.rs` (`--publish`),
  `config.example.toml`, `docs/system-architecture.md`,
  `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`

## Implementation Steps
1. Add `publish_cmd` (array) and `publish_base_url` as optional config keys.
   `--publish` without `publish_cmd` is an error naming the key.
2. Substitute `{file}` in the argv array with the absolute path. No shell,
   so quoting and spaces are not a hazard.
3. Scrub `MEDIAGRAM_*` from the child environment; stream its output; a
   non-zero exit fails the publish with the command's own stderr attached.
4. Generate `latest.json` from the archive actually written, reading its
   size and hash from disk rather than from what was intended.
5. Publish the archive, check the exit code, then publish the pointer.
6. Print the final URLs so they can be pasted into the player's config, and
   warn that `publish_base_url` is not verified against the command's real
   destination.
7. Write `docs/mlib-package-v1.md`: layout, manifest and pointer schemas,
   the associated-data rule, the cipher framing, the reader algorithm above,
   and a security model stating plainly that the archive holds the private
   channel id and every message id, that the key is the only thing
   protecting it, and that the URL is not a secret.
8. Document key generation (`head -c 32 /dev/urandom | base64`) and the
   hand-decryption command, and test the latter against a real package.
9. README, architecture, roadmap and changelog entries.

## Success Criteria
- [ ] `--dry-run` prints the exact argv and runs nothing
- [ ] A publish command exiting non-zero fails the run and surfaces its stderr
- [ ] A command that would succeed only under a shell pipeline is not silently treated as success
- [ ] The child environment contains no `MEDIAGRAM_*` variable
- [ ] `latest.json` sha256 and byte count match the archive on disk
- [ ] The archive is published before the pointer, asserted through a test double
- [ ] A path containing spaces or quotes is passed as one argument
- [ ] The documented hand-decryption command reproduces the staging tree
- [ ] Every example in the spec is generated by a test, not typed
- [ ] Someone can implement a reader from the spec alone, with no Rust

## Risk Assessment
- **A partial upload** leaves the pointer naming a truncated file.
  Mitigation: the pointer follows a successful archive publish and carries a
  sha256 the reader checks. A publish command that returns before its
  transfer completes defeats this, which is why the docs say the command
  must be synchronous.
- **Caching**: the pointer sits at a fixed URL and is exactly what a CDN
  caches, while the archives are uniquely named and need no invalidation.
  The docs must tell the operator to serve `latest.json` with a short
  max-age.
- **A spec that drifts from the code** is worse than none, because a player
  author trusts it. Mitigation: fixture-generated examples, the pattern the
  caption spec already uses.
