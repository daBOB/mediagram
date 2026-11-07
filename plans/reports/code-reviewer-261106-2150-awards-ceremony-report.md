# Code review: awards ceremony (game night), 2026-11-06

Plan: `plans/261106-2135-awards-ceremony/plan.md` (phase B ceremony screen, phase A delivery).
Reviewed at commit fd7009ce (no later edits to these files). `bun run typecheck` clean, `bun test` 10/10 pass (none cover the ceremony).

## Scope
- `client/src/tv/TvFinal.tsx`, `client/src/tv/ceremony/{ceremony-stage,ceremony-set,ceremony-avatar,ceremony-sound}.ts`, `ceremony.css`
- `android/ui-tv/src/main/kotlin/ui/tv/games/TvGamesFrame.kt` (AvatarAssetClient, Back hook)
- `crates/mediagram-game/tools/make-ceremony-avatars.mjs`
- Callers: `TvApp.tsx` (Quiz), `TvPoster.tsx`, `TvRace.tsx`; host: `room.rs`, `room_seats.rs`, `room_views.rs`, `race-sound-cues.ts`, `net.ts`

## Real bugs / risks (most severe first)

### 1. High: the quiz and poster ceremonies re-render React on every animation frame
`TvApp.tsx:46-49` (Quiz) and `TvPoster.tsx:42-44` call `useServerNow(conn)` before the `final` branch. `useServerNow` (`net.ts:144-156`) calls `setNow` on every rAF, so the parent re-renders at 60 Hz and takes `TvFinal` with it, because `TvFinal` is not memoized. Each frame, on the main thread that also drives three.js, it sorts the players, maps `top`, runs `JSON.stringify`, and reconciles the strip (Tokens). That is per-frame allocation and GC churn during the WebGL animation. The box rules exist to prevent exactly this. Heimlauf is not affected, because TvRace re-renders only on server messages.
Fix (one line): `export const TvFinal = memo(function TvFinal(...) {...})`. `players` keeps its identity until the host sends a changed view (`room_views.rs:19`, sent only on change), and `conn` is stable.

### 2. High: an avatar's shader is linked during the next frame, not before it
`ceremony-stage.ts:114-121`: `r.compile()` only issues `linkProgram`. Three r170 waits for the link lazily, the first time the program is used (`WebGLProgram` `onFirstUse` / `getUniforms`; upstream comment: "It may cause a stall when it's first used"). So the first skinned Lambert program stalls the next `render()`, while confetti and the other figures are moving. The comment "no frame sees a hitch half-way" is true only in the narrow sense that the swap is atomic. The frame still hitches. Avatars 2 and 3 most likely reuse the program, so expect about one stall per ceremony, plus upload time.
Fix: `await r.compileAsync(holder, set.camera, set.scene)` (r170 polls `KHR_parallel_shader_compile` and resolves when the program is ready), then `if (disposed) return`, then add `holder`, `initTexture`, hide the flat figure and push the Performer. Without the extension it costs nothing more than today. Measure on the box in phase C.

### 3. Medium: KTX2 transcoder workers leak when a ceremony is disposed while avatars are still loading
`ceremony-stage.ts:60` calls `ktx2.dispose()` immediately. `KTX2Loader.init()` runs lazily, on the first texture, after the GLB has arrived. If dispose comes before init, or while init is still fetching the transcoder, `workerPool.dispose()` has nothing to terminate. Init then finishes, sets the worker creator and posts the transcode job, and that new Worker, holding a compiled basis wasm plus its heap, is never terminated. One to three workers can leak per aborted ceremony. Triggers:
- every mount in dev, under StrictMode;
- a `topKey` restart (#7);
- a quick "Zurück zur Lobby" (the button has `autoFocus`; OK presses carry over from the race's cheer);
- the unmount-while-loading path (#4).

On a TV box with little memory this adds up.
Fix: keep the loader alive until the loads settle: collect `loads.push(loadAvatar(...).then(...))`, and in dispose call `void Promise.allSettled(loads).then(() => ktx2?.dispose())`. Pending transcodes then finish instead of hanging, and every worker gets terminated. Alternative: one module-level `KTX2Loader` with `setWorkerLimit(1)` that is never disposed (one idle worker kept).

### 4. Medium: unmounting while the ceremony loads still builds a ceremony
`ceremony-stage.ts:73`: `if (disposed)` is dead code. `dispose` can only be reached through the handle returned at the end, or through the catch. So if TvFinal unmounts while figures or fonts are loading, `startCeremony` still:
- creates a WebGL context;
- compiles the shaders and starts the rAF loop;
- starts the jingle fetch and three GLB fetches (feeding #3);

and only then does `TvFinal` dispose it. If the unmount happens before the dynamic import resolves, `band.current!` is null and `container.prepend` throws (logged as an "awards ceremony" error).
Fix: `startCeremony(container, top, isGone)`, called from TvFinal as `() => gone`. Check `isGone()` after the `await` (throw or return a no-op handle) and before `prepend`. In TvFinal, guard `band.current`.

### 5. Medium: Heimlauf plays the winner jingle twice
`race-sound-cues.ts:50-51` already plays `winnerJingle` at the win. About 4 s later (`TvRace.tsx` `CHEER_MS`) the ceremony plays it again (`ceremony-sound.ts`). The plan says "the winner jingle … once". Fix: a `jingle={false}` prop from TvRace (or skip the jingle when `ranked`). Check with the user which moment should keep it.

### 6. Medium: ties in quiz and poster contradict the podium
`TvFinal.tsx:15-18`: `rankOf` gives tied players the same place ("1." twice in the strip), but `top` goes onto the slots by index. The second tied player stands on the silver block with "2" on its face, does `Cheer` instead of `victory`, and the title names only one winner. When all scores are 0, the strip says everyone is "1." while the podium shows 1-2-3. Fix: pass each contender's place (`rankOf`) into `startCeremony` and use it for the block number and the intro clip. How to show a shared first place, and the title wording, is a user decision.

### 7. Medium: a late joiner or a rename restarts the whole ceremony
The host seats new phones during a running game (`room_seats.rs:78-103`, no `in_game` check), and the TV view lists all players (`room_views.rs:34`). In a quiz or poster game with fewer than three players, a phone joining during the final changes `topKey`. The ceremony is then torn down and rebuilt: new WebGL context, all loads again, jingle again, and the newcomer with 0 points stands on bronze. A name change on reconnect (`room_seats.rs:74-76`) restarts it too. Heimlauf is safe because it filters by `final.ranking`.
Fix: freeze the contenders when the ceremony mounts (`useState(() => top)`, effect deps `[]`). Optionally rank only players with `score > 0` or who were seated at the start. The quiz has no participant list in the `final` stage.

### 8. Medium (release process): a release can ship without 3D avatars and nobody would notice
`android/app/src/main/assets/game-avatars/` is gitignored and is generated only from `assets/game-night/avatars/`, which is not in git. `scripts/` contains no check (grep found no `game-avatars` or `make-ceremony` reference). A release built on the other machine, or in a fresh worktree, would ship flat figures with no error. Fix: `scripts/release-android.sh` should fail unless all 22 `.glb` files are present, or call the tool.

## Low / nice-to-have
9. `ceremony-stage.ts:44` vs `:71`: `buildSet` draws the block numbers onto canvas textures before `document.fonts.load` resolves. On a page that has not used Bricolage 800 yet (for example, the TV page reloaded straight into a final), the digits stay in the fallback font. Build the set after the `await`. `set` becomes `let`, with null checks in dispose.
10. The KTX2 pool defaults to 4 workers. With three avatars, three workers each compile the 527 KB basis wasm in parallel during the animation. `ktx2.setWorkerLimit(1)` is gentler on the box's CPU. Measure.
11. Jingle level: the ceremony uses `effectsGain` alone (up to 1.25 at effects 10), while the race mixes the same jingle at `0.8 × effectsGain`. The ceremony is therefore 25 % louder and can clip. Multiply by 0.8.
12. The APK stores `.glb` deflated (no `noCompress`), so every read inflates about 1.2 MB of data that is already compressed (meshopt and KTX2). `androidResources { noCompress += "glb" }` lets the asset be read directly.
13. `onRenderProcessGone` is not overridden. A WebGL- or OOM-killed renderer kills the whole app (same as before; the default client behaves identically). Since there is now a client, returning `true` and calling `leave()` would be cheap insurance.
14. A two-player podium is lopsided: gold in the centre, silver on the left, the right side empty. Cosmetic.
15. `make-ceremony-avatars.mjs:10`: `new URL(...).pathname` is percent-encoded, so paths with spaces break. Use `fileURLToPath`.
16. `ktx2` is created (and disposed, which decrements `_activeLoaders` below 0) even when no contender has an avatar. Harmless.

## Checked and fine
- Kotlin `AvatarAssetClient`:
  - runs on WebView's IO thread, and `AssetManager.open` is thread-safe;
  - the WebView closes the stream;
  - `Uri.path` is decoded, so `%2F` and `%2E%2E` are caught by the `/` and `..` checks; anything else odd fails with an IOException and a 404;
  - query strings are ignored;
  - only GET `/game-avatars/*.glb` is answered, everything else returns `null` and is untouched;
  - the 404 constructor is valid (non-empty reason, not 3xx), and three's FileLoader rejects it, so the flat figure stays;
  - MIME is fine for `fetch`, the response is same-origin (no CORS), and `blob:` URLs are not intercepted.
- Setting a `WebViewClient`: the only behaviour change is that user-gesture navigations to other URLs now load in the WebView instead of being handed to an external app by intent. The TV page has no links or `location` writes (grep), so in practice nothing changes. `onUnhandledKeyEvent` and `onRenderProcessGone` defaults are identical, because a null client is internally a default `WebViewClient`. D-pad and Back are unaffected.
- Back hook: both callbacks run on the main thread, `answered` is consistent, `page` is cleared before `destroy()`, and the 400 ms fallback works.
- Dispose order: rAF cancelled, observer disconnected, mixers stopped and uncached. `set.dispose` frees the avatar geometry, materials and maps, because the avatars are in the scene. Then `renderer.dispose()` and `forceContextLoss()`, and the canvas is removed. The race board's context is force-lost before the ceremony mounts, so two contexts are never alive at once in production.
- AnimationMixer: a clamped LoopOnce intro cross-fades to Idle from the `finished` event (re-entrant `play` during `mixer.update` is safe: new actions land past `nActions`). `reset()` un-pauses for the next jump. Jumps only start from Idle. A missing Idle leaves the intro clamped, and missing clips are skipped. No timers. The tool's self-check guarantees all 5 clips (fox and ghost inspected: 5 clips, one KTX2 base-colour texture, no normal map, 24 joints, double-sided).
- Per-frame work in the ceremony itself: confetti writes preallocated arrays, `frustumCulled = false` on the moving parts, `dt` clamped to 0.1. No allocations apart from the `for…of` iterators.
- Sound: muted or effects 0 means no fetch and no `AudioContext`. The disposed flag is checked after each await, and the context is closed on dispose.
- Rankings with 0, 1, 2, 3 and 8 players (`buildSet(n)` builds n blocks; the strip wraps; at 960x540, rem ≈ 11 px, the band keeps about 300–400 px). Players without an avatar get a pawn in their colour. A missing figure or GLB leaves the stand-in. Heimlauf `ranked` uses distinct places, and players who left stay ranked.
- WebView 158: WebGL2, ResizeObserver, `inset`, workers, wasm, dynamic import: all fine. The Rust host serves `/basis/*` from the embedded dist.

## Unresolved questions
- Heimlauf jingle: keep it at the win, in the ceremony, or both? (#5)
- Ties: a shared top block or a tie-break, and the title wording? (#6)
- Should late joiners appear in the quiz and poster final at all? (#7, behaviour predates this change)
