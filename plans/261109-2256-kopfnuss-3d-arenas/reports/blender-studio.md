# Kopfnuss: Blender Director 3D stage assets

> **Superseded 2026-11-18** for the sets, materials, files and rebuild steps: see
> `plans/261118-0108-kopfnuss-stage-overhaul/plan.md`. The sets are now four Cycles-baked atlases, the editable
> `.blend` is no longer shipped in `public/`, and the build is `blender -b --python build_arena.py -- --bake`.
> What follows describes the first build and is kept as history.

## Built

The live Blender MCP addon created these scenes without changing the user's other scenes:

- Pick and Resolve: four tall ivory doors, gold crowns and pointed arches, fine geometric gold glitter, pale floor and backdrop. Characters stand between the doors, matching the supplied category reference; the shared dark studio and host are not linked into these two scenes.
- Question and Reveal: answer podiums, same readable HTML question and answer UI.
- Climb: ten numbered, coloured pyramid tiers and named climb markers.
- Fact: winner podium and spotlight.
- The shared 3D Final ceremony is unchanged.

## Files

- Scene builder: `crates/mediagram-game/tools/kopfnuss-arena/build_arena.py`
- Editable Blender source: `crates/mediagram-game/client/public/kopfnuss/kopfnuss-studio.blend`
- TV set: `crates/mediagram-game/client/public/kopfnuss/kopfnuss-arena.glb`
- Showmaster: `crates/mediagram-game/client/public/kopfnuss/showmaster.glb`
- TV renderer: `crates/mediagram-game/client/src/tv/kopfnuss/arena-engine.ts`
- Stage mapping: `crates/mediagram-game/client/src/tv/kopfnuss/arena-view.ts`
- React mount: `crates/mediagram-game/client/src/tv/kopfnuss/KopfnussArena.tsx`

The GLB contains only the shared master scene, not seven copies of it. Blender supplies six named cameras and marker objects. Three.js uses those markers for real 3D avatars, the ten climb steps and the winner. Pick and Resolve use the dedicated pale category set and camera; all other stages retain the shared dark studio and animated walnut host.

## Rebuild

With Blender 5.3 or the installed Blender MCP addon:

```sh
blender -b --python crates/mediagram-game/tools/kopfnuss-arena/build_arena.py
```

The script saves the editable `.blend` and exports both GLBs. `/tmp/kopfnuss-render-stages.py` renders the individual scene previews through the live Blender MCP addon. Actual TV gameplay is separately verified through `TvKopfnuss` in Chromium, including 1920×1080 and 960×540 layouts and reduced motion. The six live stages are in `blender-studio-live-grid.jpg`; the stage buttons in its top-right corners belong only to the removed test harness, not the game.

## Verification

- 186 Bun tests passed (5,717 assertions), including reveal/climb/winner stage mapping.
- TypeScript and the production Vite build passed.
- The live preview had no JavaScript errors, correct stage switching, no 960×540 viewport overflow, and working reduced motion.
- The corrected Pick and Resolve scenes were checked through the actual `TvKopfnuss` component with all six real avatar GLBs. Category labels stayed within the viewport at both 1920×1080 and 960×540; the browser reported `errors []` and reduced motion active.
- Blender re-rendered `blender-studio-pick.png` and `blender-studio-resolve.png` from their own pale sets, without the shared dark studio.
- A simulated WebGL2 failure hid the canvas and preserved the SVG climb.
- `git diff --check` and Blender builder syntax validation passed.
- Temporary preview entry files were removed. The game has no stage-preview navigation.

## Remaining boundaries

- This completes the new Kopfnuss stages, not the original plan's all-game rollout, eight-player studio, APK pipeline or Final ceremony speech improvements.
- The walnut moves and talks visually; synchronized pre-generated Puck audio is not wired in this change.
- Browser evidence used desktop Chromium/SwiftShader. The physical TV box still needs a frame-time and memory check before replacing the tested SVG fallback unconditionally. WebGL/asset failure keeps the previous HTML/SVG presentation.
