# Phase 06 — Merge, verify, version

**Status:** cards + Bauherr rules done as 0.152.0 (results in plan.md); the Bauherr board follows phase 05. Lead's job, per finished game.

- Merge the phase branch into feat/spieltisch; resolve the additive conflicts
  (GameKind, factory, room_settings, lobby, TvApp/PhoneApp); rerun `cargo test` so
  the generated protocol types match, commit them.
- `scripts/check.sh` green.
- Bump all three manifests (`Cargo.toml`, `web/package.json`,
  `android/app/build.gradle.kts` versionName) by pattern, ≥ 0.152.0; changelog
  entry in `docs/project-changelog.md`; `docs/system-architecture.md` gets "Card
  games" / "Bauherr".
- Box: real `/tv` page via the spike WebView runner with scripted phones (fps,
  1 % low, first frame), then the release app sideloaded to the box only
  (`ANDROID_SERIAL=192.168.0.37:5555`, native core rebuilt first). Leave the box on
  the game lobby.
- Independent code review per game; fix, then record results in `plan.md`.
