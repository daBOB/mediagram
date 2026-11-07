# Phase 05 — Bauherr TV board

**Status:** pending — needs phase 03 merged and the user's pick from phase 04.

Turn the chosen mockup into `client/src/tv/bauherr/` + `TvBauherr.tsx`, driven by
`TvScene::Bauherr`, replacing the plain 2D board. Reuse Heimlauf's die motion,
sound scheduler, music and TV settings panel; stages rise when built, pawns hop
along the path (`hop_ms`), permit die and guest payment are shown on the table,
candidate entrance fields marked in the answer colours. Pre-draw every HUD state,
`renderer.compile` at load. Verify with scripted phones on desktop, then on the box
(phase 06).
