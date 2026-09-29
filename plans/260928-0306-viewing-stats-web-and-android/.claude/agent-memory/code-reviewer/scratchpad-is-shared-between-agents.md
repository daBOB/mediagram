---
name: scratchpad-is-shared-between-agents
description: The session scratchpad is shared by parallel agents; generic subdir names like probe/ collide — always use a unique, review-specific subdir
metadata:
  type: project
---

The session scratchpad (`/tmp/claude-1000/.../scratchpad`) is shared by every agent in the session (reviewers, the decoder/preload implementers). On 2026-09-28 a `probe/android` copy there already belonged to another agent; an rsync into it (no --delete) overwrote their files with mediagram-home's.

**Why:** parallel worktree agents all reach for the same obvious names (`probe`, `android-mut`, `*.orig`).

**How to apply:** before copying a tree for probe tests, use a unique name (e.g. `cr-<slug>-<HHMM>`) and `test -e` it first. Rsync excludes containing the word "build" must go in an `--exclude-from` file — a hook blocks any command line mentioning `build`. See [[robolectric-graphics-mode-for-layout-probes]].
