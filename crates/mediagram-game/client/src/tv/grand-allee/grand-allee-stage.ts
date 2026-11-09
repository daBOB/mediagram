// Entry of the Grand Allee board's lazily loaded chunk (three.js stays out of the quiz and lobby pages): loads the layout, the
// hotels and the avatars, builds the scene once, then draws every frame from whatever scene the host sent last.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { avatarHeadUrl } from "../../ui/Token";
import { FrameLog, mark, reportStart } from "./grand-allee-timing";
import { HUD_FONT } from "../race/race-hud";
import { RaceSound } from "../race/race-sound";
import { imageOrNull } from "../race/race-world";
import { loadAssets } from "./grand-allee-assets";
import { buildBoard } from "./grand-allee-board";
import { CameraRig, type CameraPose } from "./grand-allee-camera";
import { Dice } from "./grand-allee-dice";
import { bankAt } from "./grand-allee-clock";
import { SceneWatch } from "./grand-allee-events";
import { buildHotel } from "./grand-allee-hotels";
import { GrandAlleeHud } from "./grand-allee-hud";
import { CAMERA, SKY } from "./grand-allee-look";
import { GrandAlleeMotion } from "./grand-allee-motion";
import { Pieces } from "./grand-allee-pieces";
import { Grounds } from "./grand-allee-plots";
import { grandAlleeCues } from "./grand-allee-sound-cues";

export interface GrandAlleeHandle {
  /** Draw calls and triangles of the last frame, HUD included. */
  stats(): { calls: number; triangles: number };
  dispose(): void;
}
/** What the stand-in host may ask of the board: a fixed camera (null leaves the camera to the turn). */
export interface BoardOptions {
  camera?: () => CameraPose | null;
}
type Live = () => { scene: GrandAlleeScene; players: PlayerInfo[] };

// frees what the board holds outside the GL context too: geometries, materials, textures and their decoded images (an
// ImageBitmap keeps its pixels until closed). Sprites share three's one sprite geometry, which stays.
function disposeScene(scene: THREE.Scene) {
  scene.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!(o as THREE.Sprite).isSprite) mesh.geometry?.dispose();
    if ((o as THREE.InstancedMesh).isInstancedMesh) (o as THREE.InstancedMesh).dispose();
    for (const material of [mesh.material ?? []].flat()) {
      for (const value of Object.values(material)) {
        if (!(value instanceof THREE.Texture)) continue;
        value.dispose();
        (value.image as { close?: () => void } | null)?.close?.();
      }
      material.dispose();
    }
  });
}

/** Draws the board into a canvas of its own inside `container`; `live()` returns the latest scene and players, `now()` the server clock. */
export async function startGrandAllee(container: HTMLElement, live: Live, now: () => number, opts: BoardOptions = {}): Promise<GrandAlleeHandle> {
  mark("start");
  const canvas = document.createElement("canvas");
  canvas.className = "tv-race-canvas";
  container.prepend(canvas);
  // loads beside the board and joins when ready: slow or failed sound never holds the board back
  const sound = new RaceSound();
  let soundReady = false, disposed = false;
  const soundLoad = sound.load().then(() => { soundReady = true; }, (e) => console.warn("grand allee sound", e));
  const first = live(), byId = new Map(first.players.map((p) => [p.id, p]));
  let renderer: THREE.WebGLRenderer | null = null, observer: ResizeObserver | null = null, size = "";
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    renderer.autoClear = false;
    renderer.localClippingEnabled = true;
    // every avatar picture is here before the first frame (one arriving mid-game would stall the box), and canvas text needs the fonts
    const [looks, , assets] = await Promise.all([
      Promise.all(first.scene.seats.map(async (s) => {
        const p = byId.get(s.player), avatar = p?.avatar ?? null;
        const [head, figure] = avatar ? await Promise.all([imageOrNull(avatarHeadUrl(avatar)), imageOrNull(`/avatars/${avatar}-figure.webp`)]) : [null, null];
        return { name: p?.name ?? "?", color: p?.color ?? "#999999", head, figure };
      })),
      Promise.all(["600", "700", "800"].map((w) => document.fonts.load(`${w} 30px ${HUD_FONT}`))).catch(() => {}),
      loadAssets(renderer),
    ]);
    mark("assets");
    const r = renderer, board = assets.board, scene = new THREE.Scene();
    scene.background = new THREE.Color(SKY);
    const camera = new THREE.PerspectiveCamera(CAMERA.fov, 16 / 9, 5, 800);
    buildBoard(scene, board, first.scene.board.fields, assets.boardModel);
    const hotels = assets.hotels.map((root, i) => buildHotel(scene, root, board.plots[i], first.scene.board.hotels[i]?.stages.length ?? 0));
    const grounds = new Grounds(scene, board), pieces = new Pieces(scene, r, looks);
    const dice = new Dice(scene, board, assets.dice), hud = new GrandAlleeHud(r, looks);
    const motion = new GrandAlleeMotion(board, hotels, grounds, pieces, dice);
    r.info.autoReset = false; // the board and the HUD are two renders of one frame

    const resize = () => {
      const w = canvas.clientWidth || 960, h = canvas.clientHeight || 540, { scene: s, players } = live();
      size = `${w}x${h}`;
      r.setSize(w, h, false);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      hud.layout(s, players, now(), performance.now() / 1000);
    };
    observer = new ResizeObserver(() => {
      if (canvas.clientWidth && size !== `${canvas.clientWidth}x${canvas.clientHeight}`) resize();
    });
    observer.observe(canvas);
    resize();

    // upload every texture and compile every program now, with the rising stages, markers and coins showing: doing either at
    // the first purchase would stall the box GPU in the middle of play
    const meshes = hotels.flatMap((h) => [h.body, h.tint]).flatMap((p) => (p ? [p.mesh, p.rise] : []));
    const showing = [...meshes, ...pieces.badges];
    showing.forEach((m) => (m.visible = true));
    pieces.rings.count = pieces.coins.count = 1;
    scene.traverse((o) => {
      for (const m of [(o as THREE.Mesh).material].flat()) {
        const map = (m as THREE.MeshBasicMaterial | undefined)?.map;
        if (map) r.initTexture(map);
      }
    });
    await r.compileAsync(scene, camera);
    showing.forEach((m) => (m.visible = false));
    pieces.rings.count = pieces.coins.count = 0;
    hud.compile();
    mark("compiled");

    const rig = new CameraRig(), log = new FrameLog(), watch = new SceneWatch();
    let frame = 0, frames = 0, last = 0;
    const draw = (ms: number) => {
      frame = requestAnimationFrame(draw);
      log.begin(ms);
      const t = ms / 1000, host = now(), { scene: sc, players } = live(), seen = watch.next(sc, t);
      if (seen?.fresh) motion.restart();
      if (seen) {
        motion.changed(seen.changes, t);
        // the host pays the lap money as the walk starts; it shows when the pawn reaches the Bank, with its jingle
        const bank = bankAt(sc), walker = sc.turn.stage === "walking" ? sc.turn.player : -1;
        hud.flash(seen.changes.cash, (p) => t + (p === walker && bank !== null ? Math.max(0, bank - host) / 1000 : 0));
      }
      const pose = opts.camera?.() ?? rig.update(sc, board, host, last ? t - last : 0);
      last = t;
      camera.position.set(...pose.pos);
      camera.lookAt(...pose.at);
      const colors = new Map(players.map((p) => [p.id, p.color]));
      motion.update(sc, (id) => colors.get(id) ?? "#999999", host, t);
      log.phase("motion");
      if (soundReady) sound.tick(grandAlleeCues(sc, host), sc.turn.stage === "final", host);
      const painted = hud.repaints;
      hud.update(sc, players, host, t);
      log.phase("hud");
      r.info.reset();
      r.clear();
      r.render(scene, camera);
      log.phase("board");
      hud.render();
      log.phase("hudgl");
      log.end(`${sc.turn.stage}${hud.repaints !== painted ? " hud-redrawn" : ""}${hotels.some((h) => h.rising) ? " rising" : ""}`);
      if (frames++ < 2) {
        mark(`frame${frames}`);
        if (frames === 2) reportStart();
      }
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
        observer?.disconnect();
        hud.dispose();
        disposeScene(scene);
        r.dispose();
        // the box keeps few WebGL contexts; free this one now rather than at garbage collection
        r.forceContextLoss();
        canvas.remove();
      },
    };
  } catch (e) {
    observer?.disconnect();
    canvas.remove();
    sound.dispose();
    renderer?.dispose();
    renderer?.forceContextLoss();
    throw e;
  }
}
