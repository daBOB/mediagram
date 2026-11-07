// The ceremony's scene: felt backdrop, three podium blocks, soft lights, a
// contact shadow and a confetti shower. Everything is built once; per frame
// only the confetti's own arrays are written (no allocation).

import * as THREE from "three";

/** Places 1, 2, 3: how high the block is and its colour. */
export const PLACES = [
  // low blocks: the avatars are the show (the first cut's tall podium left them a third of the screen)
  { h: 1.0, color: "#f6c731" },
  { h: 0.65, color: "#d9dde3" },
  { h: 0.35, color: "#d69a5c" },
] as const;
/** Where the `i`-th of `n` contenders stands: the first in the middle, then left, right; two sit side by side. */
export const slotX = (i: number, n: number) => (n === 2 ? (i ? 1.3 : -1.3) : [0, -2.55, 2.55][i]);
const BLOCK_W = 2.3;
const CONFETTI = 260;
const PALETTE = ["#f6c731", "#e5487f", "#2c8fd6", "#13c48f", "#f7f3ea"];

const canvasTexture = (w: number, h: number, draw: (g: CanvasRenderingContext2D) => void) => {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  draw(c.getContext("2d")!);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
};

/** Block face: the block colour with its place number, shaded like the page's headings. */
function frontFace(place: number, h: number, color: string) {
  return canvasTexture(BLOCK_W * 100, h * 100, (g) => {
    g.fillStyle = color;
    g.fillRect(0, 0, BLOCK_W * 100, h * 100);
    g.fillStyle = "rgba(16, 42, 34, 0.7)";
    g.font = `800 ${Math.min(h * 70, 90)}px "Bricolage Grotesque", system-ui, sans-serif`;
    g.textAlign = "center";
    g.textBaseline = "middle";
    g.fillText(String(place), BLOCK_W * 50, h * 54);
  });
}

export interface CeremonySet {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  /** Moves the confetti on by `dt` seconds at time `t`. */
  update(dt: number, t: number): void;
  /** Frees geometry, materials and textures (not the renderer). */
  dispose(): void;
}

/** `places`: the place (1 to 3) of each block to build, in standing order; tied contenders share one. */
export function buildSet(places: number[]): CeremonySet {
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 16 / 5, 0.1, 100);
  // far enough back that the winner and its block fill about two thirds of the band's height (closer, they
  // filled it and crowded the screen)
  camera.position.set(0, 2.9, 11.5);
  camera.lookAt(0, 2.05, 0);

  // the page's felt, brighter in the middle, the page colour at the edges so the band blends in
  scene.background = canvasTexture(512, 256, (g) => {
    // an ellipse that reaches the page colour at all four edges
    g.translate(256, 128);
    g.scale(1, 0.5);
    const r = g.createRadialGradient(0, 0, 0, 0, 0, 256);
    r.addColorStop(0, "#1c7d62");
    r.addColorStop(1, "#0f5a43");
    g.fillStyle = r;
    g.fillRect(-256, -256, 512, 512);
  });

  // a warm key from the front left, a cool fill from the front right and a light from behind that
  // traces the silhouettes against the felt: one light alone left the matte avatars flat
  const key = new THREE.DirectionalLight(0xfff0d8, 2.4);
  key.position.set(-4, 9, 8);
  const fill = new THREE.DirectionalLight(0xdcecff, 0.9);
  fill.position.set(6, 4, 6);
  const back = new THREE.DirectionalLight(0xffffff, 1.6);
  back.position.set(1, 6, -7);
  scene.add(key, fill, back, new THREE.HemisphereLight(0xeafff4, 0x0a3d2e, 1.1));

  const shadow = new THREE.Mesh(
    new THREE.PlaneGeometry(10, 3.4).rotateX(-Math.PI / 2),
    new THREE.MeshBasicMaterial({
      transparent: true,
      depthWrite: false,
      map: canvasTexture(128, 64, (g) => {
        g.translate(64, 32);
        g.scale(1, 0.5);
        const r = g.createRadialGradient(0, 0, 0, 0, 0, 64);
        r.addColorStop(0, "rgba(5, 25, 18, 0.55)");
        r.addColorStop(1, "rgba(5, 25, 18, 0)");
        g.fillStyle = r;
        g.fillRect(-64, -64, 128, 128);
      }),
    }),
  );
  shadow.position.set(0, 0.01, 0.2);
  scene.add(shadow);

  places.forEach((place, i) => {
    const { h, color } = PLACES[place - 1];
    const x = slotX(i, places.length);
    const plain = new THREE.MeshLambertMaterial({ color });
    const front = new THREE.MeshLambertMaterial({ map: frontFace(place, h, color) });
    // BoxGeometry faces: +x -x +y -y +z -z
    const block = new THREE.Mesh(new THREE.BoxGeometry(BLOCK_W, h, 1.7), [plain, plain, plain, plain, front, plain]);
    block.position.set(x, h / 2, 0);
    scene.add(block);
  });

  const pos = new Float32Array(CONFETTI * 3), col = new Float32Array(CONFETTI * 3);
  const fall = new Float32Array(CONFETTI), phase = new Float32Array(CONFETTI);
  const tint = new THREE.Color();
  const respawn = (i: number, anywhere: boolean) => {
    pos[i * 3] = (Math.random() - 0.5) * 20;
    pos[i * 3 + 1] = anywhere ? Math.random() * 8 : 7.5 + Math.random();
    pos[i * 3 + 2] = -3 + Math.random() * 7;
    fall[i] = 0.7 + Math.random() * 1.1;
    phase[i] = Math.random() * 6.28;
  };
  for (let i = 0; i < CONFETTI; i++) {
    respawn(i, true);
    tint.set(PALETTE[i % PALETTE.length]);
    col.set([tint.r, tint.g, tint.b], i * 3);
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
  geo.setAttribute("color", new THREE.BufferAttribute(col, 3));
  const confetti = new THREE.Points(geo, new THREE.PointsMaterial({ size: 0.16, vertexColors: true, depthWrite: false }));
  confetti.frustumCulled = false; // positions move every frame; the first bounds would cull it
  scene.add(confetti);

  return {
    scene,
    camera,
    update(dt, t) {
      for (let i = 0; i < CONFETTI; i++) {
        pos[i * 3 + 1] -= fall[i] * dt;
        pos[i * 3] += Math.sin(t * 1.7 + phase[i]) * 0.5 * dt;
        if (pos[i * 3 + 1] < -0.6) respawn(i, false);
      }
      geo.attributes.position.needsUpdate = true;
    },
    dispose() {
      (scene.background as THREE.Texture).dispose();
      scene.traverse((o) => {
        const m = o as THREE.Mesh;
        if (!m.geometry) return;
        m.geometry.dispose();
        for (const mat of Array.isArray(m.material) ? m.material : [m.material]) {
          (mat as THREE.MeshLambertMaterial).map?.dispose();
          mat.dispose();
        }
      });
    },
  };
}
