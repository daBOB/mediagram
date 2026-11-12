---
title: "Never run cargo fmt on the game crates: they are not kept in rustfmt's style"
author: Andre
date: 2026-11-12
branch: feat/preisfrage
cost: { minutes: 1, tokens: 1105 }
id: l-7e5dc87b
scope: repo
status: active
src: agent
touches:
  - scripts/check.sh
  - crates/mediagram/tests/code_standards.rs
---

## Watch out
`crates/mediagram-game` and `crates/spieltisch-core` are written to a wider line than rustfmt's
default 100 columns, and nothing checks their formatting (`scripts/check.sh` runs clippy and tests,
not `cargo fmt --check`). `cargo fmt -p mediagram-game` reflows about 140 files, and the longer
files then break the 200-line limit that `crates/mediagram/tests/code_standards.rs` enforces
(durak.rs, kopfnuss.rs, room.rs, server.rs ... all went over).

## Decided
Format only the files you wrote yourself, or none, in those two crates. `crates/mediagram-cache`
is rustfmt-clean, so `cargo fmt -p mediagram-cache` is safe there.

In the code: if it happened, restore every file you did not mean to change with
`git checkout HEAD -- <file>` and replay your own edits on the clean copies.
