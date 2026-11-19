// The stage every 3D board on the TV stands on, in the board's lazily loaded chunk (three.js stays out of the quiz and lobby
// pages): a canvas and WebGL context of its own, the sound loading beside the board, every avatar picture and HUD font loaded
// before the first frame, the shaders compiled up front, then a frame loop drawing whatever scene the host sent last. The game
// brings its world, HUD, motion and cues as a Board.

import * as THREE from "three";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { avatarFigureUrl, avatarHeadUrl } from "../../ui/Token";
import { HUD_FONT } from "./hud-font";
import { TvSound, type Effects } from "./tv-sound";

export interface StageHandle {
  /** Draw calls and triangles of the last frame, HUD included. */
  stats(): { calls: number; triangles: number };
  dispose(): void;
}
export type Live<S> = () => { scene: S; players: PlayerInfo[] };
/** A seated player as a board draws them; `figure` stays null unless the game loads figures. */
export interface Look {
  name: string;
  color: string;
  head: HTMLImageElement | null;
  figure: HTMLImageElement | null;
}

/** What a game puts on the stage: its world, HUD, motion and cues. */
export interface Board<N extends string> {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  /** Shows everything play may show later (true), or puts it away again (false), around the up-front compile. */
  reveal(on: boolean): void;
  /** Lays the HUD out again for the canvas's new size. */
  layout(): void;
  /** Moves pieces and camera to the frame: `ms` the animation frame's time, `host` the server clock. */
  move(ms: number, host: number): void;
  /** Schedules the frame's sounds; called once the sound has loaded. */
  cue(sound: TvSound<N>, host: number): void;
  /** Brings the HUD up to the frame, before the board is drawn. */
  paint?(ms: number, host: number): void;
  /** Draws the HUD over the board. */
  overlay(): void;
  /** Frees what the game holds beside the renderer. */
  dispose(): void;
}

export interface StageGame<S, N extends string, A> {
  /** Names the board's sound in the console when it fails to load. */
  name: string;
  canvasClass: string;
  sounds: Effects<N>;
  /** The HUD font's weights the game draws with: canvas text in a font not loaded yet falls back. */
  fontWeights: string[];
  /** Loads the avatars' standing figures too, not only their heads. */
  figures: boolean;
  /** Clipping planes on materials. */
  localClipping?: boolean;
  /** Uploads every texture and compiles without blocking before play; otherwise compiles at once. */
  uploadTextures?: boolean;
  /** Loads beside the avatars and fonts. */
  load?: (renderer: THREE.WebGLRenderer) => Promise<A>;
  /** Builds the board once everything has loaded; `scene` is the first one the host sent. */
  build(ctx: { renderer: THREE.WebGLRenderer; scene: S; looks: Look[]; assets: A }): Board<N> | Promise<Board<N>>;
}

/** Frees what a board holds outside the GL context too: geometries, materials, textures and their decoded images (an
 * ImageBitmap keeps its pixels until closed). Sprites share three's one sprite geometry, which stays. */
export function disposeScene(scene: THREE.Scene) {
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

/** An image, or null if it does not load: a missing avatar picture falls back to the pawn or the initial. */
const imageOrNull = (url: string) =>
  new Promise<HTMLImageElement | null>((ok) => {
    const i = new Image();
    i.onload = () => ok(i);
    i.onerror = () => ok(null);
    i.src = url;
  });

/**
 * Draws the game's board into a canvas of its own inside `container` (one WebGL context per board, so a second start never
 * shares the first one's); `live()` returns the latest scene and players, `now()` the server clock.
 */
export async function startStage<S extends { seats: { player: number }[] }, N extends string, A = undefined>(
  container: HTMLElement,
  live: Live<S>,
  now: () => number,
  game: StageGame<S, N, A>,
): Promise<StageHandle> {
  const canvas = document.createElement("canvas");
  canvas.className = game.canvasClass;
  container.prepend(canvas);
  // loads beside the board and joins when ready: slow or failed sound never holds the board back
  const sound = new TvSound(game.sounds);
  let soundReady = false, disposed = false;
  const soundLoad = sound.load().then(() => { soundReady = true; }, (e) => console.warn(`${game.name} sound`, e));
  const first = live(), byId = new Map(first.players.map((p) => [p.id, p]));
  let renderer: THREE.WebGLRenderer | null = null, board: Board<N> | null = null, observer: ResizeObserver | null = null, size = "";
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    renderer.autoClear = false;
    if (game.localClipping) renderer.localClippingEnabled = true;
    renderer.info.autoReset = false; // the board and the HUD are two renders of one frame
    const r = renderer;
    // every avatar picture is here before the first frame (one arriving mid-game would stall the box), and canvas text needs the fonts
    const [looks, , assets] = await Promise.all([
      Promise.all(first.scene.seats.map(async (s): Promise<Look> => {
        const p = byId.get(s.player), avatar = p?.avatar ?? null;
        const [head, figure] = avatar ? await Promise.all([imageOrNull(avatarHeadUrl(avatar)), game.figures ? imageOrNull(avatarFigureUrl(avatar)) : null]) : [null, null];
        return { name: p?.name ?? "?", color: p?.color ?? "#999999", head, figure };
      })),
      Promise.all(game.fontWeights.map((w) => document.fonts.load(`${w} 30px ${HUD_FONT}`))).catch(() => {}),
      game.load?.(r),
    ]);
    const b = (board = await game.build({ renderer: r, scene: first.scene, looks, assets: assets as A }));

    const resize = () => {
      const w = canvas.clientWidth || 960, h = canvas.clientHeight || 540;
      size = `${w}x${h}`;
      r.setSize(w, h, false);
      b.camera.aspect = w / h;
      b.camera.updateProjectionMatrix();
      b.layout();
    };
    observer = new ResizeObserver(() => {
      if (canvas.clientWidth && size !== `${canvas.clientWidth}x${canvas.clientHeight}`) resize();
    });
    observer.observe(canvas);
    resize();

    // compile every program now, with whatever play shows later showing: compiling it at its first appearance would stall the
    // box GPU in the middle of play
    b.reveal(true);
    if (game.uploadTextures) {
      // and upload every texture, which stalls the box the same way at first sight
      b.scene.traverse((o) => {
        for (const m of [(o as THREE.Mesh).material].flat() as (THREE.MeshStandardMaterial | undefined)[]) {
          for (const map of [m?.map, m?.normalMap]) if (map) r.initTexture(map);
        }
      });
      await r.compileAsync(b.scene, b.camera);
    } else r.compile(b.scene, b.camera);
    b.reveal(false);

    let frame = 0;
    const draw = (ms: number) => {
      frame = requestAnimationFrame(draw);
      const host = now();
      b.move(ms, host);
      if (soundReady) b.cue(sound, host);
      b.paint?.(ms, host);
      r.info.reset();
      r.clear();
      r.render(b.scene, b.camera);
      b.overlay();
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
        b.dispose();
        r.dispose();
        // the box keeps few WebGL contexts; free this one now rather than at garbage collection
        r.forceContextLoss();
        canvas.remove();
      },
    };
  } catch (e) {
    observer?.disconnect();
    board?.dispose();
    canvas.remove();
    sound.dispose();
    renderer?.dispose();
    renderer?.forceContextLoss();
    throw e;
  }
}
