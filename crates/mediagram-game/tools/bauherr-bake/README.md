# Bauherr board bake

The TV's Bauherr board is three.js drawing a town that is painted offline. This tool paints it.

```
bun crates/mediagram-game/tools/bauherr-bake/bake.mjs      # writes client/public/bauherr/ (about 3 s)
bun crates/mediagram-game/tools/bauherr-bake/preview.mjs   # a stand-in host: the real board code on a scene made up in preview/entry.ts
```

Needs `bun install` in `client/` and `google-chrome-stable`. The bake paints in headless Chrome (canvas 2D), so any machine
gives the same pixels; run it again after changing anything under `page/` or `client/src/tv/bauherr/bauherr-layout.ts`
and commit the result. Textures are Poly Haven, CC0 (`textures/sources.json`); the baked images' provenance is in
`client/public/bauherr/SOURCES.md`.

Where things are:

- `client/src/tv/bauherr/bauherr-layout.ts` is the one place that turns the rules' 36 fields and 8 hotels into positions
  (the road, the lots, the plaza, the camera). The bake and the TV both read it, and a test checks it against `bauherr_board.rs`.
- `page/` is what runs in Chrome: `main.mjs` builds everything; `hotel-kit.mjs` and `hotels-a|b.mjs` are the eight hotels
  (stage by stage); `scenery-*.mjs` and `ground.mjs` the landscape and the ground print; `light.mjs` the baked evening light.
- Everything the TV needs is in `world.json` (an index) and `world.bin` (positions, atlas coordinates, light as bytes) plus images.
  A hotel's stages lie one after another in its mesh, so showing a stage is moving a draw range.
- Anything that carries a player's colour (awnings, flags, name signs, property lines, canopies) is baked white and flagged
  (`aTint`); the TV tints it with the owner's colour, so one baked town serves every player.
