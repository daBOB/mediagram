# Phase 5 — Verify and ship

- Stub harness only; **never start the real player** (auth key, uploads in flight).
- `bun test`, `bun run lint`, `cargo test` for the touched crates.
- Screens at 375/768/1024/1440 × dark/light for home, film, series, search and the
  player dialog. Check overflow, contrast and keyboard navigation.
- Lighthouse desktop and mobile. The backdrop is `fetchpriority=high` for the first
  cover only, `loading=lazy` elsewhere.
- Code review (code-reviewer agent).
- Docs: `docs/` system architecture (backdrop key) and the changelog.
- Version: **minor** bump in `Cargo.toml`, `web/package.json` and
  `android/app/build.gradle.kts`, bumped by pattern (another session commits to main).
