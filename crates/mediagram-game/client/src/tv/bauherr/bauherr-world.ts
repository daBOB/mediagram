// The Bauherr board as a three.js scene, built once per game from the baked town: ground, landscape, and per hotel a
// base mesh (the stages standing), a rise mesh and scaffold (the stage being built, in a clipping plane), owner pieces
// in the owner's colour, and shadow decals. A stage is a range of indices, so showing or hiding one moves a draw range.

import * as THREE from "three";
import type { Assets } from "./bauherr-assets";
import { LIGHT_MAX, geometryOf, shareGeometry, type HotelRec } from "./bauherr-data";
import { CAMERA, GROUND } from "./bauherr-layout";
import { haloMaterial, ownerMaterial, shadowMaterial, townMaterial } from "./bauherr-materials";

/** The warm haze that stands where the sky would be: the far land fades into it. */
export const SKY = "#efb27c";
type Mesh = THREE.Mesh<THREE.BufferGeometry, THREE.MeshBasicMaterial>;

export interface HotelView {
  rec: HotelRec;
  base: Mesh;
  /** The stage being built, drawn alone inside `riseClip`, with its scaffold inside `scafClip`. */
  rise: Mesh;
  scaf: Mesh;
  owner: Mesh;
  /** The plot sign: the owner pieces' last range, drawn only before the first stage. */
  sign: Mesh;
  /** Shadow decals; `shade` holds each decal vertex's strength (a stage's shadow fades in as it rises). */
  shadow: Mesh;
  shade: Float32Array;
  riseClip: THREE.Plane;
  scafClip: THREE.Plane;
  tint: { value: THREE.Color };
  /** The colour last given to `tint`. */
  tintKey: string;
  /** What is shown now: stages standing, and the stage rising (null: none). */
  built: number;
  rising: { stage: number; t0: number } | null;
  owned: boolean | null;
}
export interface World {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  hotels: HotelView[];
  assets: Assets;
}

function haloTexture() {
  const c = document.createElement("canvas");
  c.width = c.height = 128;
  const g = c.getContext("2d")!, r = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  r.addColorStop(0, "rgba(255,255,255,1)");
  r.addColorStop(0.18, "rgba(255,255,255,0.45)");
  r.addColorStop(0.5, "rgba(255,255,255,0.1)");
  r.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = r;
  g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
// camera-facing quads (tilted like the camera) merged into one mesh: the lamp halos
function haloMesh(halos: Assets["rec"]["halos"]) {
  const pos: number[] = [], uv: number[] = [], col: number[] = [], idx: number[] = [];
  const tilt = new THREE.Quaternion().setFromEuler(new THREE.Euler(-0.66, 0, 0)), v = new THREE.Vector3();
  for (const [x, y, z, r, a, css] of halos) {
    const c = new THREE.Color(css).multiplyScalar(a), b = pos.length / 3;
    for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      v.set(sx * r, sy * r, 0).applyQuaternion(tilt);
      pos.push(x + v.x, y + v.y, z + v.z);
      uv.push((sx + 1) / 2, (sy + 1) / 2);
      col.push(c.r, c.g, c.b);
    }
    idx.push(b, b + 1, b + 2, b, b + 2, b + 3);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
  g.setAttribute("color", new THREE.Float32BufferAttribute(col, 3));
  g.setIndex(idx);
  const m = new THREE.Mesh(g, haloMaterial(haloTexture()));
  m.renderOrder = 2;
  return m;
}

function hotelView(scene: THREE.Scene, a: Assets, i: number): HotelView {
  const rec = a.rec.hotels[i], tex = a.hotels[i];
  const baseGeo = geometryOf(a.buf, rec.base), ownerGeo = geometryOf(a.buf, rec.owner);
  const base = new THREE.Mesh(baseGeo, townMaterial(tex)), riseClip = new THREE.Plane(new THREE.Vector3(0, -1, 0), 0), scafClip = new THREE.Plane(new THREE.Vector3(0, -1, 0), 0);
  const rise = new THREE.Mesh(shareGeometry(baseGeo), townMaterial(tex, { clippingPlanes: [riseClip] }));
  const scaf = new THREE.Mesh(shareGeometry(baseGeo), townMaterial(tex, { clippingPlanes: [scafClip] }));
  const { material, owner: tint } = ownerMaterial(tex), owner = new THREE.Mesh(ownerGeo, material);
  const shadowGeo = geometryOf(a.buf, rec.shadow), n = rec.shadow.n, shade = new Float32Array(n * 3).fill(1);
  shadowGeo.setAttribute("color", new THREE.BufferAttribute(shade, 3).setUsage(THREE.DynamicDrawUsage));
  const shadow = new THREE.Mesh(shadowGeo, shadowMaterial(a.shadows[i], true));
  shadow.renderOrder = 1;
  const sign = new THREE.Mesh(shareGeometry(ownerGeo), material);
  for (const m of [base, rise, scaf, owner, sign, shadow]) {
    m.frustumCulled = false; // draw ranges change what the bounds hold, and the town is always in view
    scene.add(m);
  }
  rise.visible = scaf.visible = false;
  return { rec, base, rise, scaf, owner, sign, shadow, shade, riseClip, scafClip, tint, tintKey: "", built: 0, rising: null, owned: null };
}

export function buildWorld(renderer: THREE.WebGLRenderer, a: Assets): World {
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.autoClear = false;
  renderer.localClippingEnabled = true;
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(SKY);
  scene.fog = new THREE.Fog(SKY, 18, 85);
  const camera = new THREE.PerspectiveCamera(CAMERA.fov, 16 / 9, 0.5, 300);
  camera.position.set(...CAMERA.pos);
  camera.lookAt(...CAMERA.at);

  // the ground print holds the albedo and the shadows of everything fixed; the baked light of an upward surface multiplies it
  const g = GROUND, ground = new THREE.PlaneGeometry(g.x1 - g.x0, g.z1 - g.z0).rotateX(-Math.PI / 2);
  ground.setAttribute("color", new THREE.Float32BufferAttribute(Array(4).fill(a.rec.up.map((v) => v / LIGHT_MAX)).flat(), 3));
  const floor = new THREE.Mesh(ground, townMaterial(a.ground));
  floor.position.set((g.x0 + g.x1) / 2, 0, (g.z0 + g.z1) / 2);
  const o = a.rec.outer, beyond = new THREE.Mesh(new THREE.PlaneGeometry(o.x1 - o.x0, o.z1 - o.z0).rotateX(-Math.PI / 2), townMaterial(a.outer));
  beyond.geometry.setAttribute("color", ground.getAttribute("color"));
  beyond.position.set((o.x0 + o.x1) / 2, -0.03, (o.z0 + o.z1) / 2);
  // three draws opaque things by material, not by depth, so the two big ground planes would be shaded first and then covered by
  // every building and hill; drawn last, the GPU skips every pixel something stands on (the nearer ground before the far)
  floor.renderOrder = 10;
  beyond.renderOrder = 11;
  const town = new THREE.Mesh(geometryOf(a.buf, a.rec.town), townMaterial(a.town));
  town.frustumCulled = false;
  const land = new THREE.Mesh(geometryOf(a.buf, a.rec.terrain), townMaterial(a.terrain));
  land.frustumCulled = false;
  scene.add(beyond, floor, land, town, haloMesh(a.rec.halos));
  return { scene, camera, hotels: a.rec.hotels.map((_, i) => hotelView(scene, a, i)), assets: a };
}
