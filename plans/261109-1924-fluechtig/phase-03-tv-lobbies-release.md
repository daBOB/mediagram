# Phase 3: TV page, lobbies, release

Files:
- create `client/src/tv/TvFluechtig.tsx`, `client/src/tv/fluechtig/fluechtig.css`
  (+ a board component if the page passes ~200 lines)
- modify `client/src/tv/TvApp.tsx`, `client/src/phone/PhoneLobby.tsx`,
  `client/src/ui/lobby.ts`, `android/ui-tv/.../TvGamesShelf.kt` (parity: the TV
  app's games shelf lists every game), generated `client/src/protocol/*.ts`
- release: version 0.167.0 in `Cargo.toml`, `web/package.json`,
  `android/app/build.gradle.kts`; `docs/project-changelog.md`

- [x] TV: board, hunters in player colours, hideouts, last seen, log strip, turn, news
- [x] end: route drawn, result, then the awards ceremony
- [x] lobby lists and the Android shelf name Flüchtig, 2–6
- [x] checks: cargo test, clippy, client typecheck + bun test, a rendered TV screenshot
