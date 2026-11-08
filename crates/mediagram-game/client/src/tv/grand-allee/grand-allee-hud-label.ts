// A HUD label: a small canvas drawn as a textured quad in the 3D canvas's own frame (HTML over the WebGL canvas cost the TV box
// frames). Each owns one texture, sized and uploaded at layout and redrawn in place only when its text changes.

import * as THREE from "three";

export const INK = "#fff4e2", PILL = "rgba(28,18,11,0.84)";
export interface Label {
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  canvas: HTMLCanvasElement;
  tex: THREE.CanvasTexture;
  key: string | number;
  /** Size at 1920 pixels wide. */
  w: number;
  h: number;
}
type Draw = (g: CanvasRenderingContext2D, w: number, h: number) => void;

/** A label, its quad placed by its top-left corner. */
export function label(scene: THREE.Scene, w: number, h: number): Label {
  const canvas = document.createElement("canvas"), tex = new THREE.CanvasTexture(canvas);
  Object.assign(tex, { colorSpace: THREE.SRGBColorSpace, generateMipmaps: false, minFilter: THREE.LinearFilter });
  const material = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthTest: false, depthWrite: false });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1).translate(0.5, -0.5, 0), material);
  scene.add(mesh);
  return { mesh, canvas, tex, key: "", w, h };
}

/** Sizes the canvas for the screen scale `u`, before its texture goes up: a texture cannot grow after that. */
export function fit(l: Label, u: number) {
  l.key = "";
  l.tex.dispose();
  Object.assign(l.canvas, { width: Math.ceil(l.w * u), height: Math.ceil(l.h * u) });
  l.mesh.scale.set(l.canvas.width, l.canvas.height, 1);
}

/** Draws the label again if `key` changed; true when it did (a redraw is a texture upload). */
export function paint(l: Label, key: string | number, draw: Draw): boolean {
  if (l.key === key) return false;
  l.key = key;
  const g = l.canvas.getContext("2d")!;
  g.clearRect(0, 0, l.canvas.width, l.canvas.height);
  draw(g, l.canvas.width, l.canvas.height);
  l.tex.needsUpdate = true;
  return true;
}

export function pill(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, border = "", bw = 0, r = h / 2) {
  g.beginPath();
  g.roundRect(x + bw / 2, y + bw / 2, w - bw, h - bw, r);
  g.fillStyle = PILL;
  g.fill();
  if (!bw) return;
  g.lineWidth = bw;
  g.strokeStyle = border;
  g.stroke();
}

/** A player's round avatar: a disc in their colour with their head inside. */
export function avatar(g: CanvasRenderingContext2D, look: { color: string; head: HTMLImageElement | null }, cx: number, cy: number, d: number, u: number) {
  g.beginPath();
  g.arc(cx, cy, d / 2, 0, 7);
  g.fillStyle = look.color;
  g.fill();
  if (!look.head) return;
  g.save();
  g.beginPath();
  g.arc(cx, cy, d / 2 - 3 * u, 0, 7);
  g.clip();
  g.drawImage(look.head, cx - d / 2, cy - d / 2, d, d);
  g.restore();
}

export const place = (l: Label, x: number, yTop: number) => l.mesh.position.set(Math.round(x), Math.round(yTop), 0);

export function drop(l: Label) {
  l.tex.dispose();
  l.mesh.geometry.dispose();
  l.mesh.material.dispose();
}
