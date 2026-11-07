# Bauherr board bake

The TV's Bauherr board is three.js drawing a town that is painted offline. This tool paints it.

```
bun crates/mediagram-game/tools/bauherr-bake/bake.mjs      # writes client/public/bauherr/
bun crates/mediagram-game/tools/bauherr-bake/preview.mjs   # a stand-in host: the real board code on a scene made up in preview/entry.ts
```

The stand-in takes its first scene (and a fixed camera, `cam: { pos, at }`, for looking at a corner of the town) from the address:
`http://127.0.0.1:PORT/#{"estates":[...],"turn":{...},"cam":{...}}`, and `?report` posts the start's timing marks to its `/log`, for runs on the TV box.

Needs `bun install` in `client/`, `google-chrome-stable` and `toktx` (KTX-Software 4.x, on PATH or `TOKTX=/path/to/toktx`; `KTX_CLEVEL=1`
encodes faster and a little larger, for trying things out; the bake takes a few minutes at the default). The bake paints in headless Chrome (canvas 2D), so any machine
gives the same pixels; run it again after changing anything under `page/` or `client/src/tv/bauherr/bauherr-layout.ts`
and commit the result. Textures are Poly Haven, CC0 (`textures/sources.json`); the baked images' provenance is in
`client/public/bauherr/SOURCES.md`.

Where things are:

- `client/src/tv/bauherr/bauherr-layout.ts` is the one place that turns the rules' 36 fields and 8 hotels into positions
  (the road, the lots, the plaza, the camera). The bake and the TV both read it, and a test checks it against `bauherr_board.rs`.
- `page/` is what runs in Chrome: `main.mjs` builds everything; `hotel-kit.mjs` and `hotels-a|b.mjs` are the eight hotels
  (stage by stage); `scenery-*.mjs` and `ground.mjs` the landscape and the ground print; `light.mjs` the baked evening light.
- `page/terrain-field.mjs` and `page/terrain-bake.mjs` make the mountains, the hill and the coast rocks: height fields, lit texel by texel
  (sun marched along the heights, sky occlusion, normals) and coloured from real rock, scree, meadow and snow photos by slope and height. One
  image and one mesh; the shadows they throw on the flat ground go into the ground print.
- `page/lot-states.mjs` is a lot before its hotel: the meadow of a lot for sale, the building site of a bought one. The owner's fence and
  sign are in `page/owner-props.mjs` (they take the owner's colour).
- Everything the TV needs is in `world.json` (an index) and `world.bin` (positions, atlas coordinates, light as bytes) plus images.
  A hotel's stages lie one after another in its mesh, so showing a stage is moving a draw range.
- Anything that carries a player's colour (awnings, flags, name signs, property lines, canopies) is baked white and flagged
  (`aTint`); the TV tints it with the owner's colour, so one baked town serves every player.
