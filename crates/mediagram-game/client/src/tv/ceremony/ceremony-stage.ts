// Entry of the awards ceremony's lazily loaded chunk (three.js stays out of
// the pages that never end a game): podium, flat figures at once, 3D avatars
// swapped in as they arrive, jingle once. One canvas and one WebGL context per
// ceremony, freed in `dispose`.

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { MeshoptDecoder } from "three/examples/jsm/libs/meshopt_decoder.module.js";
import { KTX2Loader } from "three/examples/jsm/loaders/KTX2Loader.js";
import { Performer, flatFigure, loadAvatar, loadFigure } from "./ceremony-avatar";
import { CeremonySound } from "./ceremony-sound";
import { SLOTS, buildSet } from "./ceremony-set";

/**
 * The one knob for the box's GPU: the canvas is drawn at this many device
 * pixels per CSS pixel (at most the screen's own). The TV page is 960x540 CSS
 * px at DPR 2; 1 draws a quarter of the pixels, which is what to try first if
 * three skinned avatars cannot hold ~60 fps.
 */
const RENDER_SCALE = 2;
/** Avatar height in world units: tall enough to read from the couch, short of the band's top for a jump. */
const AVATAR_HEIGHT = 2.3;
const FONT = '"Bricolage Grotesque"';

export interface Contender {
  name: string;
  color: string;
  avatar: string | null;
}

export interface CeremonyHandle {
  dispose(): void;
}

/** `top`: the winner first, at most three. */
export async function startCeremony(container: HTMLElement, top: Contender[]): Promise<CeremonyHandle> {
  const contenders = top.slice(0, 3);
  const canvas = document.createElement("canvas");
  container.prepend(canvas);
  const sound = new CeremonySound();
  let disposed = false, frame = 0;
  const performers: Performer[] = [];
  let renderer: THREE.WebGLRenderer | null = null, ktx2: KTX2Loader | null = null;
  const set = buildSet(contenders.length);
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
    ktx2?.dispose();
    renderer?.dispose();
    // the box keeps few WebGL contexts; free this one now rather than at garbage collection
    renderer?.forceContextLoss();
    canvas.remove();
  };

  try {
    // fonts for the block numbers, pictures for the stand-ins: all before the first frame
    const [figures] = await Promise.all([
      Promise.all(contenders.map((c) => (c.avatar ? loadFigure(`/avatars/${c.avatar}-figure.webp`) : null))),
      document.fonts.load(`800 40px ${FONT}`).catch(() => []),
    ]);
    if (disposed) throw new Error("ceremony closed while loading");
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(devicePixelRatio, RENDER_SCALE));
    const flats = contenders.map((c, i) => {
      const flat = flatFigure(figures[i], c.color, AVATAR_HEIGHT);
      flat.position.set(SLOTS[i].x, SLOTS[i].h, 0);
      set.scene.add(flat);
      return flat;
    });
    renderer.compile(set.scene, set.camera);
    for (const f of flats) if (f instanceof THREE.Sprite) renderer.initTexture(f.material.map!);
    renderer.initTexture(set.scene.background as THREE.Texture);

    observer.observe(canvas);
    resize();

    let last = 0;
    const draw = (ms: number) => {
      frame = requestAnimationFrame(draw);
      const t = ms / 1000, dt = Math.min(0.1, t - last);
      last = t;
      for (const p of performers) p.update(dt, t);
      set.update(dt, t);
      renderer!.render(set.scene, set.camera);
    };
    frame = requestAnimationFrame(draw);
    sound.play().catch((e) => console.warn("ceremony sound", e));

    // 3D avatars: never awaited by the screen; a missing file just leaves the flat figure
    const r = renderer;
    ktx2 = new KTX2Loader().setTranscoderPath("/basis/").detectSupport(r);
    // the shipped files are meshopt-compressed; the decoder only runs for files that need it
    const loader = new GLTFLoader().setKTX2Loader(ktx2).setMeshoptDecoder(MeshoptDecoder);
    contenders.forEach((c, i) => {
      if (!c.avatar) return;
      loadAvatar(loader, c.avatar, AVATAR_HEIGHT).then(
        ({ holder, root, clips }) => {
          if (disposed) return;
          holder.position.set(SLOTS[i].x, SLOTS[i].h, 0);
          holder.rotation.y = (-SLOTS[i].x / 2.55) * 0.3; // the sides turn a little towards the winner
          set.scene.add(holder);
          // compile and upload now, in the same task that swaps the flat figure out: no frame sees a hitch half-way
          r.compile(set.scene, set.camera);
          root.traverse((o) => {
            const map = ((o as THREE.Mesh).material as THREE.MeshLambertMaterial | undefined)?.map;
            if (map) r.initTexture(map);
          });
          flats[i].visible = false;
          performers.push(new Performer(root, clips, i, performance.now() / 1000));
        },
        (e) => console.warn(`avatar ${c.avatar}`, e),
      );
    });
  } catch (e) {
    dispose();
    throw e;
  }
  return { dispose };
}
