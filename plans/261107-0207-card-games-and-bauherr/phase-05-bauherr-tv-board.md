# Phase 05 — Bauherr TV board

**Status:** in progress (2026-11-07 13:05) — look and structure decided by the user.

Turn the chosen look into `client/src/tv/bauherr/` + `TvBauherr.tsx`, driven by
`TvScene::Bauherr`, replacing the plain 2D board. Reuse Heimlauf's die motion,
sound scheduler, music and TV settings panel; stages rise when built, pawns hop
along the path (`hop_ms`), permit die and guest payment are shown, candidate
entrance fields marked in the answer colours. Pre-draw every HUD state,
`renderer.compile` at load. Verify with scripted phones on desktop, then on the box
(lead).

## User decisions (2026-11-07)

- 12:55, on the TV: light **D Abendsonne** (golden hour, low warm sun, long baked
  shadows, first windows lit); realistic CC0 textures, matte. Dice roll on a paved
  plaza in the foreground, no table.
- 13:05, after sharing photos of the original Hotel game (`~/Downloads/hotel`):
  - **Hotels fill the board.** Drop most of D's town houses; the 8 hotels are big
    complexes and the dominant thing on screen.
  - **Own landscape per hotel**, matched to its name and leisure stage (lake shore,
    forest, mountain with snow, sea coast, spa park, city block, hilltop, garden).
  - **Winding road**: the 36 fields wind through the districts, with a bridge,
    instead of D's rectangular loop.
  - **Different hotel types; hotel and plot upgrade visibly**: each stage adds
    a wing or tower, and the plot itself develops (bought → fenced and signed, the
    leisure stage lays out its pool, terrace or garden on the plot).
- Own architecture only: none of the original's buildings (domed palace, gold
  hexagonal towers, red crescent, blue twin towers, chalets row), board, names or art.

## Notes

- Round-2 load on the box was 15–18 s because the page paints its surfaces there;
  ship the painted surfaces as images instead (target first frame ≤ ~5 s).
- Real bloom failed the floor (37–42 fps); glow discs on lit windows cost nothing.
- Rules geometry stays: field indices, each hotel's entrance fields
  (`bauherr_board.rs`); only the TV places them in 3D.
