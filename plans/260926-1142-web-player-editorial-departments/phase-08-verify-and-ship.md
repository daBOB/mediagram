# Phase 8 — Verify and ship

**Priority:** P1. **Status:** done 2026-09-26 — results in plan.md § Phase 8 close-out.

1. Run `cd web && bun run preview` and check every screen at 375/768/1024/1440, dark and
   light, with `/browse`. Test the player dialog and keyboard navigation. Check that no
   page overflows horizontally.
2. `bun test` + lint for web. `cargo test` for Rust. Lighthouse on home with a
   profile selected (last time it measured the picker).
3. code-reviewer pass. Fix high and medium findings.
4. Docs: `web/DESIGN.md` still describes the 2026-09-18 paper theme. Rewrite it to
   match what shipped. Add a changelog entry.
5. Minor version bump in all three manifests (CLAUDE.md § Versioning).
6. Create the Android parity follow-up plan (§ Surface Parity) that lists every screen
   and the v9 fields.
7. User handoff: **both uploader machines on v9 before any `push-index`**. Then run
   `metadata` credits backfill + `posters` (portraits) + `push-index` on one machine.
