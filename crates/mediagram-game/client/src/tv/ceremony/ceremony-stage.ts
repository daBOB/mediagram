// Entry of the awards ceremony's lazily loaded chunk (three.js stays out of
// the pages that never end a game): podium, flat figures at once, 3D avatars
// swapped in as they arrive, jingle once. One canvas and one WebGL context per
// ceremony, freed in `dispose`.

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { MeshoptDecoder } from "three/examples/jsm/libs/meshopt_decoder.module.js";
import { Performer, flatFigure, loadAvatar, loadFigure } from "./ceremony-avatar";
import { CeremonySound } from "./ceremony-sound";
import { PLACES, buildSet, slotX } from "./ceremony-set";
import { type StudioLook, bakeStudioLook } from "./ceremony-studio-look";

/**
 * The canvas is drawn at this many device pixels per CSS pixel (at most the
 * screen's own: the TV page is 960x540 CSS px at DPR 2, so full resolution),
 * with MSAA. Quality first: the user's floor is 30 fps, not 60. Measured on the
 * box with three 51k-triangle normal-mapped avatars at the set's framing:
 * 2 → 36.4 fps, 1.75 → 38.6.
 */
const RENDER_SCALE = 2;
/** The scale is lowered by a step whenever a window's frames, the slowest tenth left out, average slower than
 *  this (seconds; ~32 fps, a margin above the 30 fps floor)… */
const SLOW_FRAME = 1 / 32;
/** …down to this: at 1 the box's avatars looked blurred. */
const MIN_SCALE = 1.25;
/** Frames per measuring window (~2.5 s at 36 fps; a multiple of 10, for the tenth left out). */
const WINDOW = 90;
/** Avatar height in world units: tall enough to read from the couch, short of the band's top for a jump. */
const AVATAR_HEIGHT = 3.0;
const FONT = '"Bricolage Grotesque"';

export interface Contender {
  name: string;
  color: string;
  avatar: string | null;
  /** 1-based place; tied contenders share one. */
  place: number;
}

export interface CeremonyHandle {
  dispose(): void;
}

/**
 * `top`: the winner first, at most three. `gone()` says the screen has left
 * while this was loading: nothing is built then. `jingle: false` keeps it silent.
 */
export async function startCeremony(container: HTMLElement, top: Contender[], gone: () => boolean, jingle = true): Promise<CeremonyHandle> {
  const contenders = top.slice(0, 3);
  // fonts for the block numbers, pictures for the stand-ins: all before anything is built
  const [figures] = await Promise.all([
    Promise.all(contenders.map((c) => (c.avatar ? loadFigure(`/avatars/${c.avatar}-figure.webp`) : null))),
    document.fonts.load(`800 40px ${FONT}`).catch(() => []),
  ]);
  if (gone()) return { dispose() {} };

  const set = buildSet(contenders.map((c) => c.place));
  const canvas = document.createElement("canvas");
  container.prepend(canvas);
  const sound = new CeremonySound();
  let disposed = false, frame = 0;
  const performers: Performer[] = [];
  let renderer: THREE.WebGLRenderer | null = null, look: StudioLook | null = null;
  const resize = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!renderer || !w || !h) return;
    renderer.setSize(w, h, false);
    set.camera.aspect = w / h;
    set.camera.updateProjectionMatrix();
  };
  const observer = new ResizeObserver(resize);
  const dispose = () => {
    disposed = true;
    cancelAnimationFrame(frame);
    observer.disconnect();
    sound.dispose();
    performers.forEach((p) => p.dispose());
    set.dispose();
    look?.dispose();
    renderer?.dispose();
    // the box keeps few WebGL contexts; free this one now rather than at garbage collection
    renderer?.forceContextLoss();
    canvas.remove();
  };

  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(devicePixelRatio, RENDER_SCALE));
    // the viewer's tone mapping: the studio light goes above 1 in places, and neutral tone mapping rolls it off
    // while keeping the colours
    renderer.toneMapping = THREE.NeutralToneMapping;
    look = bakeStudioLook(renderer, set.camera);
    const flats = contenders.map((c, i) => {
      const flat = flatFigure(figures[i], c.color, AVATAR_HEIGHT);
      flat.position.set(slotX(i, contenders.length), PLACES[c.place - 1].h, 0);
      set.scene.add(flat);
      return flat;
    });
    renderer.compile(set.scene, set.camera);
    for (const f of flats) if (f instanceof THREE.Sprite) renderer.initTexture(f.material.map!);
    renderer.initTexture(set.scene.background as THREE.Texture);

    observer.observe(canvas);
    resize();

    // `last` starts now: from 0 the first frame counted as a 0.1 s one and skewed the first average
    let last = performance.now() / 1000, scale = Math.min(devicePixelRatio, RENDER_SCALE), n = 0, seen = 0;
    const times = new Float32Array(WINDOW);
    const draw = (ms: number) => {
      frame = requestAnimationFrame(draw);
      const t = ms / 1000, dt = Math.min(0.1, t - last);
      last = t;
      // the device sets its own sharpness: slower than ~32 fps over a window drops the scale a step. An arriving
      // avatar starts a new window, and the slowest tenth of each is left out: one texture upload or garbage
      // collection is no measure, and it had dropped the box from full resolution without cause.
      if (performers.length !== seen) (seen = performers.length), (n = 0);
      else if (((times[n] = dt), ++n === WINDOW)) {
        const kept = times.slice().sort().subarray(0, WINDOW - WINDOW / 10);
        if (kept.reduce((a, b) => a + b, 0) / kept.length > SLOW_FRAME && scale > MIN_SCALE) {
          scale = Math.max(MIN_SCALE, scale - 0.25);
          renderer!.setPixelRatio(scale);
          resize();
        }
        n = 0;
      }
      for (const p of performers) p.update(dt, t);
      set.update(dt, t);
      renderer!.render(set.scene, set.camera);
    };
    frame = requestAnimationFrame(draw);
    if (jingle) sound.play().catch((e) => console.warn("ceremony sound", e));

    // 3D avatars: never awaited by the screen; a missing file just leaves the flat figure
    const r = renderer;
    // the shipped files are meshopt-compressed with WebP textures, both read without workers
    const loader = new GLTFLoader().setMeshoptDecoder(MeshoptDecoder);
    contenders.forEach((c, i) => {
      if (!c.avatar) return;
      const x = slotX(i, contenders.length);
      loadAvatar(loader, c.avatar, AVATAR_HEIGHT, look!.dress)
          .then(async ({ holder, root, clips }) => {
            if (disposed) return;
            holder.position.set(x, PLACES[c.place - 1].h, 0);
            holder.rotation.y = (-x / 2.55) * 0.3; // the sides turn a little towards the winner
            // link the shader (with the scene's lights) before the avatar joins the scene: drawn while still
            // compiling, a frame would block on it
            await r.compileAsync(holder, set.camera, set.scene);
            if (disposed) return;
            set.scene.add(holder);
            root.traverse((o) => {
              const mat = (o as THREE.Mesh).material as THREE.MeshMatcapMaterial | undefined;
              for (const map of [mat?.map, mat?.normalMap]) if (map) r.initTexture(map);
            });
            flats[i].visible = false;
            performers.push(new Performer(root, clips, c.place - 1, performance.now() / 1000));
          })
          .catch((e) => console.warn(`avatar ${c.avatar}`, e));
    });
  } catch (e) {
    dispose();
    throw e;
  }
  return { dispose };
}
