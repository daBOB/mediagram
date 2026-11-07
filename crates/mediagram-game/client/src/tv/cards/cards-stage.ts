// Entry of the card table's lazily loaded chunk (three.js stays out of the quiz
// and lobby pages): builds the world once, then draws every frame from whatever
// scene the host sent last.

import type { CardsScene } from "../../protocol/CardsScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { avatarHeadUrl } from "../../ui/Token";
import { CardsHud } from "./cards-hud";
import { seatSpot } from "./cards-layout";
import { CardsMotion } from "./cards-motion";
import { HUD_FONT } from "./cards-print";
import { CardsSound } from "./cards-sound";
import { textsFor } from "./cards-texts";
import { buildWorld } from "./cards-world";

export interface CardsHandle {
  dispose(): void;
}

const imageOrNull = (url: string) =>
  new Promise<HTMLImageElement | null>((ok) => {
    const i = new Image();
    i.onload = () => ok(i);
    i.onerror = () => ok(null);
    i.src = url;
  });

/**
 * Draws the table into a canvas of its own inside `container` (one WebGL
 * context per table). `live()` returns the latest scene and players; `now()`
 * the server clock.
 */
export async function startCards(
  container: HTMLElement,
  live: () => { scene: CardsScene; players: PlayerInfo[] },
  now: () => number,
): Promise<CardsHandle> {
  const canvas = document.createElement("canvas");
  canvas.className = "tv-cards-canvas";
  container.prepend(canvas);
  // loads beside the world and joins when ready: slow or failed sound never holds the table back
  const sound = new CardsSound();
  let soundReady = false, disposed = false;
  const soundLoad = sound.load().then(
    () => { soundReady = true; },
    (e) => console.warn("card sound", e),
  );
  const { scene, players } = live();
  const byId = new Map(players.map((p) => [p.id, p]));
  // every head is loaded before the first frame: a picture arriving mid-game would stall the box
  const seated = await Promise.all(
    scene.seats.map(async (s) => {
      const p = byId.get(s.player);
      return { name: p?.name ?? "?", color: p?.color ?? "#999999", head: p?.avatar ? await imageOrNull(avatarHeadUrl(p.avatar)) : null };
    }),
  );
  // canvas text needs the web fonts loaded, or the HUD is drawn in a fallback
  await Promise.all(["700", "800"].map((w) => document.fonts.load(`${w} 30px ${HUD_FONT}`))).catch(() => {});
  const world = await buildWorld(canvas, seated.length).catch((e) => {
    canvas.remove();
    sound.dispose();
    throw e;
  });
  const spots = seated.map((_, i) => seatSpot(i, seated.length));
  world.camera.position.set(0, 15.5, 10.5);
  world.camera.lookAt(0, 0, 0.4);
  const hud = new CardsHud(world.renderer, world.camera, seated, spots, textsFor(scene.game));
  const motion = new CardsMotion(world, hud, spots, scene.seats.map((s) => s.player));

  let size = "";
  const resize = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight;
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
  // compile every shader now, the badges' too: compiling at the first wish would stall the box GPU
  world.cards.showAll(true);
  for (const b of Object.values(world.badges)) b.visible = true;
  world.renderer.compile(world.scene, world.camera);
  world.cards.showAll(false);
  for (const b of Object.values(world.badges)) b.visible = false;

  let frame = 0;
  const draw = () => {
    frame = requestAnimationFrame(draw);
    const host = now(), { scene } = live();
    motion.update(scene, host);
    if (soundReady) sound.update(scene, host);
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
