# Code review: Kopfnuss stage overhaul (2026-11-18)

Reviewer: `code-reviewer` agent, on the builder (`tools/kopfnuss-arena`), `arena-fx.ts`, `arena-engine.ts`,
`arena-view.ts`, the asset test and two CSS rules. It traced code and three.js source; it ran `tsc` and the two
arena tests, nothing in a browser or Blender. Findings as reported, with what was done.

| # | Severity | Finding | Done |
|---|---|---|---|
| 1 | high | Confetti never visible: opaque, no depth write, drawn before the set | fixed: depth write on (`arena-fx.ts`) |
| 2 | high | Unmount leaves the GL context and GPU memory alive (atlases ≈ 90 MB, avatars, KTX2 workers, module-level shadow texture) | partly: atlases, avatar textures, cast, confetti, shadow texture (now per engine) and KTX2 workers are disposed. **Open:** the context itself is not force-lost; that needs an engine-owned canvas as `ceremony-stage.ts` has (StrictMode reuses the React canvas) |
| 3 | medium | Reuse cache could pair an atlas with the wrong layout (layout written before the atlas; size/samples not in the key) | fixed: old layout removed first, new one written after the atlas, key = geometry fingerprint + size + samples |
| 4 | medium | An avatar is dropped if the cast changes while it loads | fixed: the load-version check is gone, `cast.get(id) !== member` covers it |
| 5 | medium | After a failed load or lost context the engine keeps drawing behind the 2D show | fixed: `fail` stops the loop |
| 6 | medium | Set names from the command line unchecked; `--only X --bake` wrote a partial arena over the shipped file | fixed: `--only` removed, a bake is always the whole arena, `--rebake` names are checked |
| 7 | medium | `build(["Doors"])` emptied the other sets' material slots; a second `bake()` piled up collections | fixed: `build()` always rebuilds all sets, `bake()` builds afresh first |
| 8 | medium | Asset test would pass with a broken export | fixed: cameras must be camera nodes, every mesh must hang under a set or stage group, names come from `ARENA_SET` |
| 9 | low | Shader colours written without sRGB conversion | confetti fixed. The additive effects stay as tuned by eye: the user approved the look on 2026-11-18 ("yes looks right") |
| 10 | low | glTF flips V: column bands ran down, beams were brightest at the floor | fixed: V flipped back in the vertex shader, dust drifts down |
| 11 | low | `_clear` removes anything linked into a `KN_` scene | not changed: `KN_` scenes are the tool's own |
| 12 | low | Far plane 100 trims the cloud sea | fixed: 150, as the stage cameras |
| 13 | low | A truncated download is trusted forever | fixed: size checked |
| 14 | low | A failed bake leaves `hide_render` and the float image behind | fixed: `try/finally` |
| 15 | low | Effect clock loses precision on a page open for days; `pow` of a negative base; reversed `smoothstep` edges | clock and `pow` fixed. **Open:** reversed `smoothstep` kept (undefined by the GLSL ES text, works on every driver the mockups ran on) |
| 16 | low | Eleven scene searches per frame | not changed: about a thousand string compares a frame |
| 17 | low | Blob shadow wider than a pyramid tread | reduced from 1.7 to 1.4 |

House rules: stale comments fixed, dead `BOW` tuple removed, every builder module is under 200 lines.
`arena-engine.ts` (300 lines) was split after the user's verdict: engine 184, `arena-cast.ts` 129.

Checked and found sound by the reviewer: winding and UVs of `tube`, `prism`, `moulding`, `fx_ring`, `beam`,
`add_sphere`; the bake's slot remap, hidden-face split and position restore; no double-dispose; no lookup throws
on a missing node; reduced motion stops clock, sway, bob, jaw and mixers.

## Unresolved questions

1. Does the TV page stay open across games and days? Decides how much finding 2's open part matters.
