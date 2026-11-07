// The card table as a three.js scene, built once per game: the lacquered cherry
// table (unlit, its lamp light baked into vertex colours), soft blobs for
// shadows, a pool of card meshes and the wished-suit badges. Cards are opaque
// rounded shapes carrying the atlas picture: no alpha test, no shadow maps.

import * as THREE from "three";
import type { Card } from "../../protocol/Card";
import type { Suit } from "../../protocol/Suit";
import { SUITS, SUIT_SYMBOL, isRed } from "../../ui/card-face";
import { CARD_H, CARD_W, TABLE_Y, type Placed } from "./cards-layout";
import { COLS, HUD_FONT, ROWS, cellOf, printCards } from "./cards-print";

/** Enough meshes for eight full fans, the piles and every card in flight. */
const POOL = 150;
/** Piles lying side by side that get a shadow (the deck has one more). */
const MAX_PILES = 3;

const image = (url: string) =>
  new Promise<HTMLImageElement>((ok, fail) => {
    const i = new Image();
    i.onload = () => ok(i);
    i.onerror = () => fail(new Error(`image ${url}`));
    i.src = url;
  });

export const canvasOf = (w: number, h: number, draw: (g: CanvasRenderingContext2D) => void) => {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  draw(c.getContext("2d")!);
  return c;
};

function canvasTexture(c: HTMLCanvasElement, mips = true) {
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  t.generateMipmaps = mips;
  t.minFilter = mips ? THREE.LinearMipmapLinearFilter : THREE.LinearFilter;
  return t;
}

/** A rounded card outline lying on the table, its top edge towards -z, with uv 0..1 over the card. */
function cardGeometry() {
  const w = CARD_W / 2, h = CARD_H / 2, r = 0.09, s = new THREE.Shape();
  s.moveTo(-w + r, -h);
  s.lineTo(w - r, -h);
  s.quadraticCurveTo(w, -h, w, -h + r);
  s.lineTo(w, h - r);
  s.quadraticCurveTo(w, h, w - r, h);
  s.lineTo(-w + r, h);
  s.quadraticCurveTo(-w, h, -w, h - r);
  s.lineTo(-w, -h + r);
  s.quadraticCurveTo(-w, -h, -w + r, -h);
  const g = new THREE.ShapeGeometry(s, 4).rotateX(-Math.PI / 2);
  const p = g.attributes.position, uv: number[] = [];
  for (let i = 0; i < p.count; i++) uv.push(p.getX(i) / CARD_W + 0.5, 0.5 - p.getZ(i) / CARD_H);
  g.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
  return g;
}

/** Cards put on the table this frame; whatever is not put stays hidden. */
export class CardPool {
  private meshes: { mesh: THREE.Mesh; cell: number }[] = [];
  private cursor = 0;
  private base: Float32Array;

  constructor(scene: THREE.Scene, atlas: THREE.Texture) {
    const geo = cardGeometry(), mat = new THREE.MeshBasicMaterial({ map: atlas });
    this.base = new Float32Array(geo.attributes.uv.array);
    for (let i = 0; i < POOL; i++) {
      const mesh = new THREE.Mesh(geo.clone(), mat);
      mesh.visible = false;
      scene.add(mesh);
      this.meshes.push({ mesh, cell: -1 });
      this.setCell(i, cellOf(null));
    }
  }

  private setCell(i: number, cell: number) {
    const e = this.meshes[i], uv = e.mesh.geometry.attributes.uv as THREE.BufferAttribute;
    const col = cell % COLS, row = Math.floor(cell / COLS);
    for (let k = 0; k < uv.count; k++) uv.setXY(k, (col + this.base[k * 2]) / COLS, 1 - (row + 1 - this.base[k * 2 + 1]) / ROWS);
    uv.needsUpdate = true;
    e.cell = cell;
  }

  begin() {
    this.cursor = 0;
  }

  /** `card` null: face down. */
  put(card: Card | null, p: Placed, lift = 0) {
    if (this.cursor >= POOL) return;
    const i = this.cursor++, cell = cellOf(card), e = this.meshes[i];
    if (e.cell !== cell) this.setCell(i, cell);
    e.mesh.position.set(p.x, p.y + lift, p.z);
    e.mesh.rotation.y = p.yaw;
    e.mesh.visible = true;
  }

  end() {
    for (let i = this.cursor; i < POOL; i++) this.meshes[i].mesh.visible = false;
  }

  /** Everything shown, for compiling the shaders. */
  showAll(on: boolean) {
    for (const e of this.meshes) e.mesh.visible = on;
  }
}

export interface World {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  cards: CardPool;
  /** One soft shadow patch per seat, then the deck's and one per pile. */
  blobs: THREE.Mesh[];
  /** Glow under the active seat. */
  glow: THREE.Mesh;
  /** One badge per suit, in `SUITS` order. */
  badges: Record<Suit, THREE.Mesh>;
}

const radial = (size: number, stops: [number, string][]) =>
  canvasOf(size, size, (g) => {
    const r = g.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    for (const [at, color] of stops) r.addColorStop(at, color);
    g.fillStyle = r;
    g.fillRect(0, 0, size, size);
  });

function badgeCanvas(suit: Suit) {
  return canvasOf(160, 160, (g) => {
    g.beginPath();
    g.arc(80, 80, 74, 0, 7);
    g.fillStyle = "#fbf6ea";
    g.fill();
    g.lineWidth = 8;
    g.strokeStyle = "#f6c731";
    g.stroke();
    g.fillStyle = isRed(suit) ? "#c4213f" : "#1b1b22";
    g.font = `700 100px ${HUD_FONT}, "DejaVu Sans"`;
    g.textAlign = "center";
    g.textBaseline = "middle";
    g.fillText(SUIT_SYMBOL[suit], 80, 86);
  });
}

export async function buildWorld(canvas: HTMLCanvasElement, seats: number): Promise<World> {
  const woodTable = await image("/race/table-lacquered-cherry.jpg");
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.autoClear = false;
  const camera = new THREE.PerspectiveCamera(36, 16 / 9, 0.1, 300);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color("#1a110b");

  // the table: wood tiled, darker towards the edge like a lamp over the middle
  const wood = new THREE.Texture(woodTable);
  wood.colorSpace = THREE.SRGBColorSpace;
  wood.anisotropy = 8;
  wood.wrapS = wood.wrapT = THREE.RepeatWrapping;
  wood.repeat.set(12, 12);
  wood.needsUpdate = true;
  const geo = new THREE.PlaneGeometry(120, 120, 60, 60).rotateX(-Math.PI / 2);
  const pos = geo.attributes.position, shade: number[] = [];
  for (let i = 0; i < pos.count; i++) {
    const k = 1 - 0.8 * THREE.MathUtils.smoothstep(Math.hypot(pos.getX(i), pos.getZ(i) * 1.25), 9, 24);
    shade.push(k, k * 0.96, k * 0.92);
  }
  geo.setAttribute("color", new THREE.Float32BufferAttribute(shade, 3));
  // what a lit upward face would be under the race table's lamp and sky, baked into the colour
  const lit = new THREE.Color(0xffe2bc).multiplyScalar((2.25 * (12 / Math.hypot(5, 12, 7))) / Math.PI).add(new THREE.Color(0xfff1de).multiplyScalar(1.2 / Math.PI));
  const table = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: wood, vertexColors: true, color: lit }));
  table.position.y = TABLE_Y - 0.01;
  scene.add(table);

  const blobTex = canvasTexture(radial(128, [[0, "rgba(15,6,0,0.55)"], [0.6, "rgba(15,6,0,0.28)"], [1, "rgba(15,6,0,0)"]]), false);
  const blobMat = new THREE.MeshBasicMaterial({ map: blobTex, transparent: true, depthWrite: false });
  const plane = new THREE.PlaneGeometry(1, 1).rotateX(-Math.PI / 2);
  const flatMesh = (mat: THREE.Material, y: number, w: number, d: number) => {
    const m = new THREE.Mesh(plane, mat);
    m.position.y = TABLE_Y + y;
    m.scale.set(w, 1, d);
    scene.add(m);
    return m;
  };
  const blobs = Array.from({ length: seats + 1 + MAX_PILES }, () => flatMesh(blobMat, 0.004, 1, 1));
  const glowTex = canvasTexture(radial(128, [[0, "rgba(255,214,120,0.55)"], [0.6, "rgba(255,200,90,0.25)"], [1, "rgba(255,200,90,0)"]]), false);
  const glow = flatMesh(new THREE.MeshBasicMaterial({ map: glowTex, transparent: true, depthWrite: false }), 0.008, 7, 3.2);

  const atlas = canvasTexture(printCards());
  renderer.initTexture(atlas);
  const cards = new CardPool(scene, atlas);

  const badges = {} as Record<Suit, THREE.Mesh>;
  for (const suit of SUITS) {
    const tex = canvasTexture(badgeCanvas(suit), false);
    renderer.initTexture(tex);
    badges[suit] = flatMesh(new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }), 0.03, 1.5, 1.5);
    badges[suit].visible = false;
  }
  return { renderer, scene, camera, cards, blobs, glow, badges };
}
