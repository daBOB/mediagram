// Entry of the Bauherr board's lazily loaded chunk (three.js stays out of the quiz and lobby pages): loads the baked
// town, builds the scene once, then draws every frame from whatever scene the host sent last.

import * as THREE from "three";
import type { BauherrScene } from "../../protocol/BauherrScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { avatarHeadUrl } from "../../ui/Token";
import { HUD_FONT } from "../race/race-hud";
import { imageOrNull } from "../race/race-world";
import { RaceSound } from "../race/race-sound";
import { loadAssets } from "./bauherr-assets";
import { Dice } from "./bauherr-dice";
import { BauherrHud } from "./bauherr-hud";
import { BauherrMotion } from "./bauherr-motion";
import { Blobs, Pieces } from "./bauherr-pieces";
import { bauherrCues } from "./bauherr-sound-cues";
import { buildWorld } from "./bauherr-world";

export interface BauherrHandle {
  /** Draw calls and triangles of the last frame, hud included. */
  stats(): { calls: number; triangles: number };
  dispose(): void;
}
type Live = () => { scene: BauherrScene; players: PlayerInfo[] };

/** The evening sun, as in the bake: what lights the dice and the pawn discs at run time. */
const SUN = new THREE.Vector3(-0.92, 0.32, 0.27).normalize().multiplyScalar(20);

/**
 * Draws the board into a canvas of its own inside `container` (one WebGL context per board). `live()` returns the
 * latest scene and players; `now()` the server clock.
 */
export async function startBauherr(container: HTMLElement, live: Live, now: () => number): Promise<BauherrHandle> {
  const canvas = document.createElement("canvas");
  canvas.className = "tv-race-canvas";
  container.prepend(canvas);
  // loads beside the world and joins when ready: slow or failed sound never holds the board back
  const sound = new RaceSound();
  let soundReady = false, disposed = false;
  const soundLoad = sound.load().then(() => { soundReady = true; }, (e) => console.warn("bauherr sound", e));
  const first = live(), byId = new Map(first.players.map((p) => [p.id, p]));
  // every avatar picture is loaded before the first frame: an image arriving mid-game would stall the box
  const looks = await Promise.all(first.scene.seats.map(async (s) => {
    const p = byId.get(s.player), avatar = p?.avatar ?? null;
    const [head, figure] = avatar ? await Promise.all([imageOrNull(avatarHeadUrl(avatar)), imageOrNull(`/avatars/${avatar}-figure.webp`)]) : [null, null];
    return { name: p?.name ?? "?", color: p?.color ?? "#999999", head, figure };
  }));
  // canvas text needs the web fonts loaded, or the HUD is drawn in a fallback
  await Promise.all(["700", "800"].map((w) => document.fonts.load(`${w} 30px ${HUD_FONT}`))).catch(() => {});
  let renderer: THREE.WebGLRenderer | null = null;
  try {
    const assets = await loadAssets(first.scene.board.plots.map((p) => p.levels.length));
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    const world = buildWorld(renderer, assets), scene = world.scene;
    scene.add(new THREE.DirectionalLight(0xffbb7a, 2.1 * 1.1).translateX(SUN.x).translateY(SUN.y).translateZ(SUN.z), new THREE.HemisphereLight(0x9aa6bc, 0xb08a64, 0.46 * 1.6));
    const blobs = new Blobs(scene, looks.length + 2, { x: 2.875, z: -0.84 });
    const pieces = new Pieces(scene, renderer, assets, looks);
    const dice = new Dice(scene, assets.ivory, assets.oak), hud = new BauherrHud(renderer, looks);
    const motion = new BauherrMotion(world, pieces, dice, blobs);
    renderer.info.autoReset = false; // the board and the hud are two renders of one frame

    let size = "";
    const observer = new ResizeObserver(() => {
      const w = canvas.clientWidth, h = canvas.clientHeight;
      if (!w || !h || size === `${w}x${h}`) return;
      size = `${w}x${h}`;
      renderer!.setSize(w, h, false);
      world.camera.aspect = w / h;
      world.camera.updateProjectionMatrix();
      const { scene: s, players } = live();
      hud.layout(s, players, now());
    });
    observer.observe(canvas);
    const r = renderer, { scene: s0, players: p0 } = live();
    r.setSize(canvas.clientWidth || 960, canvas.clientHeight || 540, false);
    world.camera.aspect = (canvas.clientWidth || 960) / (canvas.clientHeight || 540);
    world.camera.updateProjectionMatrix();
    hud.layout(s0, p0, now());
    size = `${canvas.clientWidth}x${canvas.clientHeight}`;

    // compile every program now, with the rising stages, the markers and the canopies showing, and upload every texture:
    // doing either at the first purchase would stall the box GPU in the middle of play
    for (const h of world.hotels) h.rise.visible = h.scaf.visible = true;
    pieces.rings.count = 1;
    pieces.badges.forEach((b) => (b.visible = true));
    pieces.canopies.forEach((c) => (c.count = 1));
    r.compile(scene, world.camera);
    for (const t of [assets.ground, assets.outer, assets.town, assets.canopy, assets.ivory, assets.oak, ...assets.hotels, ...assets.shadows]) r.initTexture(t);
    hud.compile();
    for (const h of world.hotels) h.rise.visible = h.scaf.visible = false;
    pieces.badges.forEach((b) => (b.visible = false));

    let frame = 0;
    const draw = (ms: number) => {
      frame = requestAnimationFrame(draw);
      const t = ms / 1000, host = now(), { scene: sc, players } = live();
      motion.update(sc, players, host, t);
      if (soundReady) sound.tick(bauherrCues(sc.turn, host), sc.turn.stage === "final", host);
      hud.update(sc, players, host);
      r.info.reset();
      r.clear();
      r.render(scene, world.camera);
      hud.render();
    };
    frame = requestAnimationFrame(draw);
    void soundLoad.then(() => {
      if (soundReady && !disposed) sound.startMusic();
    });
    return {
      stats: () => ({ calls: r.info.render.calls, triangles: r.info.render.triangles }),
      dispose() {
        disposed = true;
        sound.dispose();
        cancelAnimationFrame(frame);
        observer.disconnect();
        hud.dispose();
        r.dispose();
        // the box keeps few WebGL contexts; free this one now rather than at garbage collection
        r.forceContextLoss();
        canvas.remove();
      },
    };
  } catch (e) {
    canvas.remove();
    sound.dispose();
    renderer?.dispose();
    renderer?.forceContextLoss();
    throw e;
  }
}
