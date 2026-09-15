---
phase: 5
title: "Publish and latest pointer"
status: pending
priority: P2
effort: "0.5d"
dependencies: [3, 4]
---

# Phase 5: Publish and latest pointer

## Overview
Put the finished archive somewhere a player can reach it, and give the
player a fixed URL that always names the newest package.

## Requirements
- Functional: `publish_cmd` in config runs once per file with a `{file}`
  placeholder; `latest.json` is generated and published last; `--dry-run`
  prints the exact commands without running them.
- Non-functional: mediagram holds no storage credentials and speaks no
  storage API. Publishing failure leaves the local files intact and says
  what to re-run.

## Architecture
```
config.toml
  publish_cmd      = "rclone copy {file} r2:mediagram/"
  publish_base_url = "https://example.com/"
  package_key      = "<32 bytes, see phase 4>"

published layout
  https://example.com/latest.json                         <- fixed, plaintext
  https://example.com/prebuilt_mediagram_db_20260616.tar.gz.enc
  https://example.com/prebuilt_mediagram_db_20260601.tar.gz.enc   (kept)
```

`latest.json`, the only file a player needs to know the URL of:

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

It is deliberately thin: no titles, no counts, nothing about the library's
contents, because it is the one file published in the clear. Everything
descriptive lives in the encrypted manifest.

Ordering is a correctness property, not a detail: the archive is published
first and `latest.json` second, so a player that reads the pointer always
finds the file it names. A player that fetches mid-publish sees the old
pointer and the old, still-present archive.

## Related Code Files
- Create: `crates/mediagram/src/export/publish.rs` (command execution),
  `crates/mediagram/src/export/latest.rs` (pointer generation, pure)
- Modify: `crates/mediagram/src/config.rs` (three new optional keys),
  `crates/mediagram/src/commands/export_package.rs` (wire `--publish`),
  `config.example.toml`

## Implementation Steps
1. Add `publish_cmd` and `publish_base_url` as optional config keys.
   `--publish` without `publish_cmd` configured is an error naming the key.
2. Substitute `{file}` with the absolute path, quoted. Run through the
   shell so operators can pipe or chain, and document that consequence.
3. Stream the command's stdout and stderr; a non-zero exit fails the
   publish with the command's own error text attached.
4. Generate `latest.json` from the archive that was actually written, never
   from what was intended, so its sha256 and size cannot drift.
5. Publish the archive, verify the exit code, then publish `latest.json`.
6. Print the final URLs so they can be pasted into the player's config.
7. `--dry-run` prints each command with substitutions applied and exits.

## Success Criteria
- [ ] `--dry-run` shows the substituted commands and runs nothing
- [ ] A publish command that exits non-zero fails the run and surfaces its stderr
- [ ] `latest.json` sha256 and byte size match the archive on disk exactly
- [ ] The archive is published before the pointer, verified by command order in a test double
- [ ] A filename or path containing spaces or quotes is passed correctly
- [ ] `--publish` with no `publish_cmd` configured names the missing key

## Risk Assessment
- Running a configured command through a shell is arbitrary code execution
  by design. It is the user's own config file, which already holds the API
  hash, so the trust boundary is unchanged, but the docs must say plainly
  that this key executes a command.
- A partial upload leaves the pointer naming a truncated file. Mitigation:
  the pointer is published only after the archive command succeeds, and it
  carries a sha256 so a player can reject a bad download.
- Old packages accumulate on the host. Pruning is the operator's business
  in v1; the command prints what it published so a retention script can
  follow.
