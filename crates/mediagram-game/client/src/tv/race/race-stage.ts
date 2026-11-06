// Entry of the race board's lazily loaded chunk (three.js stays out of the
// quiz and lobby pages): builds the world once, then draws every frame from
// whatever scene the host sent last.

import type { PlayerInfo } from "../../protocol/PlayerInfo";
import type { RaceScene } from "../../protocol/RaceScene";
import { HUD_FONT, Hud } from "./race-hud";
import { Motion, bannerTexts } from "./race-motion";
import { RaceSound } from "./race-sound";
import { avatarHeadUrl } from "../../ui/Token";
import { getSettings, subscribeSettings } from "../settings/tv-settings";
import { buildWorld, imageOrNull, showFigures } from "./race-world";

export interface RaceHandle {
  dispose(): void;
}

/**
 * Draws the board into a canvas of its own inside `container` (one WebGL
 * context per board, so a second start never shares the first one's).
 * `live()` returns the latest scene and players; `now()` the server clock.
 */
export async function startRace(
  container: HTMLElement,
  live: () => { scene: RaceScene; players: PlayerInfo[] },
  now: () => number,
): Promise<RaceHandle> {
  const canvas = document.createElement("canvas");
  canvas.className = "tv-race-canvas";
  container.prepend(canvas);
  // loads beside the world and joins when ready: slow or failed sound never holds the board back
  const sound = new RaceSound();
  let soundReady = false, disposed = false;
  const soundLoad = sound.load().then(
    () => { soundReady = true; },
    (e) => console.warn("race sound", e),
  );
  const { scene, players } = live();
  const byId = new Map(players.map((p) => [p.id, p]));
  // every avatar picture is loaded before the first frame: an image arriving mid-game would stall the box
  const seated = await Promise.all(
    scene.seats.map(async (s) => {
      const avatar = byId.get(s.player)?.avatar ?? null;
      const [head, figure] = avatar
        ? await Promise.all([imageOrNull(avatarHeadUrl(avatar)), imageOrNull(`/avatars/${avatar}-figure.webp`)])
        : [null, null];
      return { corner: s.corner, name: byId.get(s.player)?.name ?? "?", color: byId.get(s.player)?.color ?? "#999999", head, figure };
    }),
  );
  // canvas text needs the web fonts loaded, or the HUD is drawn in a fallback
  await Promise.all(["700", "800"].map((w) => document.fonts.load(`${w} 30px ${HUD_FONT}`))).catch(() => {});
  const world = await buildWorld(canvas, seated).catch((e) => {
    canvas.remove();
    sound.dispose();
    throw e;
  });
  const hud = new Hud(world.renderer, seated, bannerTexts(seated.map((s) => s.name)));
  const motion = new Motion(world, hud, new Map(scene.seats.map((s, i) => [s.player, seated[i].name])));

  let size = "";
  const resize = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    // the observer also fires once right after `observe`: the HUD pre-draw is not worth doing twice
    if (!w || !h || size === `${w}x${h}`) return;
    size = `${w}x${h}`;
    world.renderer.setSize(w, h, false);
    world.camera.aspect = w / h;
    world.camera.updateProjectionMatrix();
    hud.layout();
  };
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  // compile every shader now, the move markers' too: compiling them at the first
  // "welche Figur?" would stall the box GPU in the middle of play
  // (both figure styles too, so switching them in the settings never compiles mid-game)
  for (const m of world.markers) m.ring.visible = m.target.visible = m.badge.visible = true;
  for (const p of world.pawns.flat()) if (p.figure) p.figure.visible = true;
  world.renderer.compile(world.scene, world.camera);
  for (const m of world.markers) m.ring.visible = m.target.visible = m.badge.visible = false;
  showFigures(world, getSettings().figures);
  const unsubscribe = subscribeSettings((s) => showFigures(world, s.figures));

  let frame = 0;
  const draw = (ms: number) => {
    frame = requestAnimationFrame(draw);
    const t = ms / 1000, host = now(), { scene } = live();
    motion.update(scene, host, t);
    if (soundReady) sound.update(scene, host);
    world.camera.position.set(Math.sin(t * 0.15) * 1.4, 17.6, 13.2);
    world.camera.lookAt(0, 0, 0.55);
    world.renderer.clear();
    world.renderer.render(world.scene, world.camera);
    hud.render();
  };
  frame = requestAnimationFrame(draw);
  void soundLoad.then(() => {
    if (soundReady && !disposed) sound.startMusic();
  });

  return {
    dispose() {
      disposed = true;
      unsubscribe();
      sound.dispose();
      cancelAnimationFrame(frame);
      observer.disconnect();
      hud.dispose();
      world.renderer.dispose();
      // the box keeps few WebGL contexts; free this one now rather than at garbage collection
      world.renderer.forceContextLoss();
      canvas.remove();
    },
  };
}
