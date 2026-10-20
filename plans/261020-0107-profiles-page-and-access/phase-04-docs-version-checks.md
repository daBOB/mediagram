# Phase 04 — Docs, version, checks

**Status:** pending.

### Task 4.1: Docs and version

- `docs/web-player.md`: the Settings tabs table gains Profiles (who sees it, what it holds).
- `docs/project-changelog.md`: a new top entry, **Added** (web player, Android phone, tablet and TV).
- Version: the next minor above `main`, all three manifests by regex; `cargo update --workspace --offline` for `Cargo.lock`.
- **Commit** `docs: the Profiles page in Settings` and the version bump with it.

### Task 4.2: Checks

- `scripts/check.sh` passes (clippy, cargo test, bun lint + test, gradle test + lint).
- Final whole-branch review; the user decides the merge.
