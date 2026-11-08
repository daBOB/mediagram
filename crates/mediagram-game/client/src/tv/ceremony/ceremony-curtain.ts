// The stage curtain: closed while the 3D avatars load, so the flat stand-ins are
// never seen, then drawn aside on the finished podium. It is drawn inside the
// canvas, as two unlit velvet panels hung just in front of the camera (HTML over
// the canvas costs the TV box frames), and hidden once open, so it costs nothing
// afterwards. Being nearest, the closed panels are drawn first and the depth test
// spares the box shading the stage behind them.

import * as THREE from "three";
import { canvasTexture } from "./ceremony-set";

/** Seconds the panels take to draw aside. */
export const OPEN_S = 1.6;
/** Distance in front of the camera: past its near plane, nearer than anything on stage. */
const DIST = 1;
/** Widest band the panels must cover (the ceremony band runs at about 3.8:1). */
const MAX_ASPECT = 5;
/** Each panel reaches this far past the middle, so no seam shows while closed. */
const OVERLAP = 0.02;
/** Velvet folds across one panel. */
const FOLDS = 16;

/** How far open at `elapsed` seconds after the opening began: 0 closed, 1 open, eased in and out. */
export function openProgress(elapsed: number): number {
  const x = Math.min(1, Math.max(0, elapsed / OPEN_S));
  return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2;
}

export interface Curtain {
  /** Starts drawing the panels aside at `t` (seconds); later calls are ignored. */
  open(t: number): void;
  update(t: number): void;
  /** True once fully open (and hidden). */
  readonly opened: boolean;
}

/** Hangs a closed curtain in front of `camera`, which must be in the scene for its children to be drawn. */
export function hangCurtain(camera: THREE.PerspectiveCamera, renderer: THREE.WebGLRenderer): Curtain {
  const h = 2 * DIST * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * 1.05;
  const w = (h * MAX_ASPECT) / 2;
  const velvet = canvasTexture(512, 256, (g) => {
    // eight folds per picture: dark in the creases, lit on the crowns
    for (let i = 0; i < 8; i++) {
      const fold = g.createLinearGradient(i * 64, 0, (i + 1) * 64, 0);
      fold.addColorStop(0, "#2a030b");
      fold.addColorStop(0.45, "#a3162b");
      fold.addColorStop(0.6, "#c8323f");
      fold.addColorStop(1, "#2a030b");
      g.fillStyle = fold;
      g.fillRect(i * 64, 0, 64, 256);
    }
    // darker towards the top and the floor, as under stage lights
    const shade = g.createLinearGradient(0, 0, 0, 256);
    shade.addColorStop(0, "rgba(0,0,0,0.55)");
    shade.addColorStop(0.35, "rgba(0,0,0,0)");
    shade.addColorStop(0.85, "rgba(0,0,0,0.1)");
    shade.addColorStop(1, "rgba(0,0,0,0.5)");
    g.fillStyle = shade;
    g.fillRect(0, 0, 512, 256);
  });
  velvet.wrapS = THREE.RepeatWrapping;
  velvet.repeat.set(FOLDS / 8, 1);
  // the colours as painted: no tone mapping on the unlit cloth
  const material = new THREE.MeshBasicMaterial({ map: velvet, toneMapped: false });
  const geometry = new THREE.PlaneGeometry(w + OVERLAP * h, h);
  const panels = [-1, 1].map((side) => {
    const panel = new THREE.Mesh(geometry, material);
    panel.position.set(side * (w / 2), 0, -DIST);
    camera.add(panel);
    return panel;
  });
  renderer.initTexture(velvet);

  let openedAt: number | null = null, opened = false;
  return {
    open(t) {
      openedAt ??= t;
    },
    update(t) {
      if (openedAt === null || opened) return;
      const p = openProgress(t - openedAt);
      panels.forEach((panel, i) => (panel.position.x = (i ? 1 : -1) * (w / 2 + p * w)));
      if (p >= 1) {
        opened = true;
        for (const panel of panels) panel.visible = false;
      }
    },
    get opened() {
      return opened;
    },
  };
}
