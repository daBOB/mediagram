# Phase 4 — Box run, docs, version

**Status:** pending.

## Steps

1. Tests: `cargo test -p mediagram-game`, clippy, client `typecheck` and `bun test` (layout and mapping tests new).
2. Box: a full Kopfnuss game with bots and a browser phone (`box-game.mjs` from the Dreifelder run, scratchpad), frame
   log and `dumpsys gfxinfo` against the flat Kopfnuss numbers (2.5 % janky, p99 36 ms); GPU memory; load time to
   the first arena frame. Screenshots of every stage into `evidence/`.
3. Code review (independent reviewer), fixes.
4. Version bump in the three manifests (next free number on the branch at that time), changelog entry, plan status.
5. Ask the user before committing.

## Done when

Every stage matches the Studio mockups on the box, frame numbers meet phase 1's go line, and the flat fallback works
with WebGL off.
