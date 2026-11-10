// Soft shadow blobs under the pawns and the dice: the long evening shadow along the sun and a contact core, all in one mesh,
// drawn as darkness over what is below (multiplied, so no light is added).

import * as THREE from "three";

const shadowMaterial = (map: THREE.Texture) =>
  new THREE.MeshBasicMaterial({
    map, transparent: true, depthWrite: false, fog: false,
    blending: THREE.CustomBlending, blendSrc: THREE.ZeroFactor, blendDst: THREE.OneMinusSrcColorFactor, blendSrcAlpha: THREE.ZeroFactor, blendDstAlpha: THREE.OneFactor,
  });
const draw = (w: number, h: number, f: (g: CanvasRenderingContext2D) => void) => {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  f(c.getContext("2d")!);
  return c;
};
const srgb = (c: HTMLCanvasElement) => {
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
};

/** Soft shadow blobs, all in one mesh: quad i lies at (x, z) with the given scale; unused ones collapse. */
export class Blobs {
  readonly mesh: THREE.Mesh;
  private pos: Float32Array;
  constructor(scene: THREE.Scene, readonly capacity: number, sun: { x: number; z: number }) {
    const uv: number[] = [], idx: number[] = [];
    for (let i = 0; i < capacity; i++) {
      uv.push(0, 1, 1, 1, 1, 0, 0, 0);
      idx.push(i * 4, i * 4 + 2, i * 4 + 1, i * 4, i * 4 + 3, i * 4 + 2);
    }
    this.pos = new Float32Array(capacity * 12);
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(this.pos, 3).setUsage(THREE.DynamicDrawUsage));
    g.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    // the long evening shadow: a tail along the sun, then the contact core on top; stored as darkness
    const len = Math.hypot(sun.x, sun.z), tex = srgb(draw(128, 128, (c) => {
      c.fillStyle = "#000";
      c.fillRect(0, 0, 128, 128);
      c.translate(64, 64);
      const disc = (r: number, a: number) => {
        const gr = c.createRadialGradient(0, 0, 0, 0, 0, r);
        gr.addColorStop(0, `rgba(255,255,255,${a})`);
        gr.addColorStop(0.6, `rgba(255,255,255,${a * 0.55})`);
        gr.addColorStop(1, "rgba(255,255,255,0)");
        c.fillStyle = gr;
        c.beginPath();
        c.arc(0, 0, r, 0, 7);
        c.fill();
      };
      c.save();
      c.rotate(Math.atan2(sun.z, sun.x));
      c.translate(20, 0);
      c.scale(1.9, 0.75);
      disc(24, 0.56);
      c.restore();
      disc(26, len > 0 ? 0.8 : 0);
    }));
    this.mesh = new THREE.Mesh(g, shadowMaterial(tex));
    this.mesh.renderOrder = 1;
    this.mesh.frustumCulled = false;
    scene.add(this.mesh);
    for (let i = 0; i < capacity; i++) this.set(i, 0, 0, 0);
  }
  set(i: number, x: number, z: number, scale: number) {
    const r = scale / 2, p = this.pos;
    p.set([x - r, 0.015, z + r, x + r, 0.015, z + r, x + r, 0.015, z - r, x - r, 0.015, z - r], i * 12);
    (this.mesh.geometry.attributes.position as THREE.BufferAttribute).needsUpdate = true;
  }
}
