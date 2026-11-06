// The ceremony's scene: felt backdrop, three podium blocks, soft lights, a
// contact shadow and a confetti shower. Everything is built once; per frame
// only the confetti's own arrays are written (no allocation).

import * as THREE from "three";

/** Places 1, 2, 3: where each stands and how high its block is. */
export const SLOTS = [
  { x: 0, h: 1.7, color: "#f6c731" },
  { x: -2.55, h: 1.15, color: "#d9dde3" },
  { x: 2.55, h: 0.75, color: "#d69a5c" },
] as const;
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

/** `places`: how many blocks to build (one to three). */
export function buildSet(places: number): CeremonySet {
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 16 / 5, 0.1, 100);
  camera.position.set(0, 3.7, 10);
  camera.lookAt(0, 2.15, 0);

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

  const key = new THREE.DirectionalLight(0xfff0d8, 2.4);
  key.position.set(-4, 9, 8);
  scene.add(key, new THREE.HemisphereLight(0xeafff4, 0x0a3d2e, 1.5));

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

  for (let i = 0; i < places; i++) {
    const { x, h, color } = SLOTS[i];
    const plain = new THREE.MeshLambertMaterial({ color });
    const front = new THREE.MeshLambertMaterial({ map: frontFace(i + 1, h, color) });
    // BoxGeometry faces: +x -x +y -y +z -z
    const block = new THREE.Mesh(new THREE.BoxGeometry(BLOCK_W, h, 1.7), [plain, plain, plain, plain, front, plain]);
    block.position.set(x, h / 2, 0);
    scene.add(block);
  }

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
