# Code review: avatars in the lobby + TV settings (uncommitted, feat/spieltisch)

Plan: `plans/261106-1921-avatars-and-tv-settings/plan.md`. Scope: avatar/settings parts of the host, client, Android frame and render tool. Date 2026-11-06.

Checks run: `cargo test -p mediagram-game` 119 pass · `cargo clippy -p mediagram-game --tests -- -D warnings` clean (`--all-targets` is blocked by the hook's "target" filter; `--tests` covers lib + tests) · `bun run typecheck` clean · `bun test` 10 pass · headless Chrome measurement of the token CSS (below).

## Real bugs

### 1. HIGH: the avatar head is sized from the parent's width, not the token's (`client/src/styles.css:148-153`)
`.token:has(.token-head) { padding: 8% }`: percentage padding resolves against the **containing block's width** (the parent), not the token. Measured in headless Chrome with the real `.seat-list > .seat` and phone `.screen` rules:

| place | token | head now | head intended (84 %) |
|---|---|---|---|
| TV lobby seat, name "Al" | 40 px | 28 px | 33.6 px |
| TV lobby seat, "Alexandra" | 40 px | 20 px | 33.6 px |
| TV lobby seat, 16-char name + ★ | **44.5 px** (token grows) | 20 px | 33.6 px |
| phone lobby header (`token-lg`) | 104 px | **48 px** | 87 px |

So heads get smaller as names get longer, long names make the token bigger, and the phone header head is half the size. Also `max(2px, 6%)` in `box-shadow` is invalid (box-shadow takes no percentages): the computed `box-shadow` is `none`, so the colour ring and the bottom shading are both lost.
**Fix** (measured: 33.6 px / 87.4 px everywhere, shadow kept): delete the `.token:has(.token-head)` rule and size the image instead. The token is a grid with `place-items: center`, so the percentage resolves against the token: `.token-head { width: 84%; height: 84%; … }`. The token's own background stays as the ring.

### 2. MEDIUM: the TV settings panel comes back by itself on the next round (`client/src/tv/TvRace.tsx:24,73,96`)
If the panel is open during the 4 s cheer, the ranking replaces the section and unmounts the panel, but `settings` stays `true`. When the same table plays again (`!over && cheered` → `setCheered(false)`), the panel is drawn over the new board straight away and catches every arrow key.
**Fix:** call `setSettings(false)` next to `setCheered(true)` in the cheer timer.

### 3. MEDIUM: holding OK flips "Ton" on and off (`TvRace.tsx:62`, `TvSettingsPanel.tsx:44`)
Neither listener checks `e.repeat`. Holding OK on the board opens the panel, then each auto-repeat keydown runs `step(ROWS[0], 1)` and toggles mute, so it stops at random. In the lobby, holding OK on "Los geht's" starts the game, and the repeats then open the panel over the new board and toggle mute too. To the viewer it looks like "the game has no sound".
**Fix:** `if (e.repeat) return;` for Enter in both listeners. Keep repeat for ◀▶ on the level rows only if you want it.

### 4. LOW-MEDIUM: sound that finishes loading after `dispose()` leaks (`client/src/tv/race/race-sound.ts:47-55, 110-120`)
`load()` has no "disposed" check after its awaits. If the board is thrown away before sound finishes loading (Back while "Das Spielbrett wird aufgebaut", the ranking taking over during the build, or `buildWorld` failing, which calls `sound.dispose()` straight away), `load()` carries on. The music blob URLs it creates are never revoked, because `dispose` already ran over an empty `tracks`. Its `keydown`/`pointerdown` listeners are added *after* `dispose` removed them, so they live for the rest of the page. Each key press then calls `ctx.resume()` on a closed context, which is an unhandled rejection, and the listeners keep the `RaceSound` and its decoded buffers alive. This code mostly predates this plan (the sound phase), but it is the same object the new settings gain runs through.
**Fix:** add `private disposed = false`, set it in `dispose()`, and after the awaits in `load()` do `if (this.disposed) { tracks.forEach(URL.revokeObjectURL); throw new Error("disposed"); }` before adding the listeners.

### 5. LOW: Back now depends on the page's JS thread (`android/ui-tv/.../TvGamesFrame.kt:65-73`)
Before, Back acted at once. Now it waits for `evaluateJavascript`, which only answers once the page's main thread is free. While the board is being built (synchronous `buildWorld`, `renderer.compile`, HUD pre-draw: seconds on the box), Back does nothing visible. A second press then queues behind the first, and the second `model.back()` sees the lobby and calls `leave()`, which stops the host for every phone. Two quick presses already ended the same way before, but now there is no feedback in between to stop the second one.
**Fix (optional):** a short fallback. A `handled` flag, `web.postDelayed({ if (!handled) { handled = true; stepBack() } }, 400)`, and the callback only acts while `!handled`.

## Nice-to-haves / questions (not bugs)
- **Stored avatar is overwritten by a fallback** (`PhoneApp.tsx:48`). Every view writes `me.avatar`. A phone whose favourite was taken once (or when two phones join with the same stored id) stores the random one and asks for that from then on. The plan says "the phone remembers its avatar", which this does literally. **User call:** keep it, or store only after the player's own `pick_avatar` is confirmed.
- **Effects level 9–10 = master gain 1.125–1.25** (`tv-settings.ts:26`). With per-effect gains up to 0.9, overlapping die + scream + jingle can clip. Cap the master gain at 1, or put a `DynamicsCompressorNode` before the destination.
- **Losing a pick race shows the generic toast** "Das geht gerade nicht." (`room.rs:158-161`). A specific NotAllowed message ("Den hat schon jemand.") would explain the greyed head.
- **The avatar figures' base cylinder geometry is uploaded on the first frame after switching to "Avatare"**: `compile()` builds programs but uploads no buffers. It is tiny. One `renderer.render` with both styles visible before `showFigures` would make "nothing created mid-game" literally true.
- **`aria-label={a.id}`** in `AvatarPicker.tsx` reads "dark-knight" in English on a German UI.
- **Origins (existing limitation, not new code):** localStorage is per origin. The phones' origin is the box's DHCP-assigned LAN IP (it has moved several times), and the TV's origin includes the host port (a random one if 8080 is taken). So "remembered" avatars, names and TV settings can reset between game nights.

## Checked and fine
- **Uniqueness:** the room is synchronous. `pick` refuses an id held by anyone else, and `assign` only hands out free ids. Two phones tapping the same head: the first wins, the second gets NotAllowed and its view greys the head. Reconnect by token keeps the avatar whatever the hello asks. A player in the lobby grace period keeps holding it, and it is freed when the grace runs out. `StartGame`'s `retain(connected)` frees absent players' avatars. A phone joining mid-game gets a free one. 8 players max ≤ 22 avatars. Unknown ids are refused, the TV cannot pick, picking during a game is refused, and the client id is only compared, never echoed.
- **Protocol:** `Hello.avatar` is `#[serde(default)]` + `ts(optional)` and tested without the field. The `PlayerInfo.avatar` and `Lobby.avatars` fields are additive. The generated TS matches.
- **Panel keys:** the window capture listener + `stopImmediatePropagation` run before React's root listeners. `preventDefault` on Enter keydown stops a focused lobby button from activating, so the panel never starts the game. The panel's listener is attached after the opening keydown has finished, so one key never does both jobs. Closing in the lobby puts focus back on "Einstellungen". `tvBack` resets to `() => false` on unmount. All three settings subscriptions (panel, `RaceSound`, race-stage) unsubscribe.
- **Kotlin:** `page == null` (Starting/Failed) → native Back. A page that is not loaded, or `tvBack` missing or throwing → `"null"`/`'false'` → native Back. The JSON-quoted `"true"` is handled by `trim('"')`. `onRelease` clears `page` before `destroy()`. `page` is only read inside the lambda, so setting it from `factory` causes no recomposition.
- **Board:** `imageOrNull` never rejects: a missing figure keeps the pawn, a missing head draws the initial. Figure textures go through `initTexture`, and both styles are visible for `compile()`. `showFigures` only toggles `visible`. Dispose while loading: `gone`/`cheeredRef` disposes the late handle, and an unmount before the chunk import throws at `prepend(null)` before `RaceSound` exists and is caught. All images are on the TV's own origin, so textures are not tainted.
- **Sound settings:** the master gain is set before any effect can play. Changes ramp via `setTargetAtTime`, and a new `fade` replaces one in flight (including the 2 s fade-in). The `ending` flag stops a level change from bringing back the end-of-game music. Mute keeps the levels. `music.volume` stays ≤ 0.875.
- **Storage:** `load` and `updateSettings` both survive missing or full storage and bad JSON, and `sanitize` clamps and fills. Covered by tests.
- **Render tool:** binds 127.0.0.1, URL normalisation stops `..` traversal on `/three/`, output names are regex-checked.
- **Versions:** all three manifests are at 0.146.0.

**Status:** DONE_WITH_CONCERNS
**Summary:** The host side (uniqueness, reconnect, grace, protocol compatibility) is sound. One visible CSS bug (heads sized by the parent's width; lost ring shadow) needs fixing before the box walk, plus two small TV key/state bugs and a sound leak on an aborted board.
**Concerns/Blockers:** Finding 1 is visible on every surface. Findings 2–3 confuse the remote flow. Stored-avatar overwrite is a user decision.
