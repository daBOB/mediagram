// What moves or is placed by the game on top of the baked town: each player's pawn (their avatar as a sprite on a
// disc in their colour), soft shadow blobs under pawns and dice, candidate-field markers, and entrance canopies in the owners' colours.

import * as THREE from "three";
import type { AnswerColor } from "../../protocol/AnswerColor";
import { ANSWER_MARKS } from "../../ui/answers";
import { geometryOf } from "./bauherr-data";
import type { Assets } from "./bauherr-assets";
import { ownerMaterial, shadowMaterial } from "./bauherr-materials";

export const MARK_COLORS: Record<AnswerColor, string> = { blue: "#2c6bed", orange: "#f2862e", yellow: "#f6c731", pink: "#e5487f" };
const PAWN_H = 1.25, CANOPY_CAP = 16;
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

export interface SeatLook {
  name: string;
  color: string;
  figure: HTMLImageElement | null;
}
export interface Pawn {
  sprite: THREE.Sprite;
}

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
    this.mesh = new THREE.Mesh(g, shadowMaterial(tex, false));
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

export class Pieces {
  readonly pawns: Pawn[];
  /** Pawn discs, one instanced mesh; lit by the scene's sun like the dice. */
  readonly bases: THREE.InstancedMesh;
  readonly rings: THREE.InstancedMesh;
  readonly badges: THREE.Sprite[];
  /** Entrance canopies by seat, an instanced mesh each in that player's colour. */
  readonly canopies: THREE.InstancedMesh[];

  constructor(scene: THREE.Scene, renderer: THREE.WebGLRenderer, a: Assets, seats: SeatLook[]) {
    this.pawns = seats.map((s) => {
      // the avatar image, or the player's initial in their colour when there is none
      const map = s.figure ? new THREE.Texture(s.figure) : srgb(draw(128, 192, (c) => {
        c.fillStyle = s.color;
        c.beginPath();
        c.arc(64, 70, 54, 0, 7);
        c.fill();
        c.fillStyle = "#1c120b";
        c.font = '800 70px "Bricolage Grotesque", sans-serif';
        c.textAlign = "center";
        c.textBaseline = "middle";
        c.fillText([...s.name.trim()][0]?.toUpperCase() ?? "?", 64, 74);
      }));
      map.colorSpace = THREE.SRGBColorSpace;
      map.needsUpdate = true;
      renderer.initTexture(map);
      const aspect = s.figure ? s.figure.width / s.figure.height : 128 / 192, sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map, alphaTest: 0.04, transparent: true, color: "#fff3e2" }));
      sprite.center.set(0.5, 0);
      sprite.scale.set(PAWN_H * aspect, PAWN_H, 1);
      scene.add(sprite);
      return { sprite };
    });
    this.bases = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.27, 0.3, 0.07, 28).translate(0, 0.035, 0), new THREE.MeshLambertMaterial(), seats.length);
    seats.forEach((s, i) => this.bases.setColorAt(i, new THREE.Color(s.color)));
    scene.add(this.bases);

    // candidate fields: a ring on the field, a badge with the answer button's mark above it
    this.rings = new THREE.InstancedMesh(new THREE.RingGeometry(0.46, 0.64, 36).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ fog: false }), 4);
    this.rings.setColorAt(0, new THREE.Color()); // the colour buffer exists from the start, so the program is built with it
    this.rings.count = 0;
    this.rings.frustumCulled = false;
    scene.add(this.rings);
    this.badges = (Object.keys(MARK_COLORS) as AnswerColor[]).map((k) => {
      const tex = srgb(draw(128, 128, (g) => {
        g.beginPath(); g.arc(64, 64, 58, 0, 7); g.fillStyle = "#fff"; g.fill();
        g.beginPath(); g.arc(64, 64, 50, 0, 7); g.fillStyle = MARK_COLORS[k]; g.fill();
        g.fillStyle = "#fff"; g.font = "900 56px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(ANSWER_MARKS[k], 64, 68);
      }));
      renderer.initTexture(tex);
      const badge = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false, fog: false }));
      badge.scale.set(0.7, 0.7, 1);
      badge.visible = false;
      scene.add(badge);
      return badge;
    });

    this.canopies = seats.map((s) => {
      const { material, owner } = ownerMaterial(a.canopy, { side: THREE.DoubleSide });
      owner.value.set(s.color);
      const m = new THREE.InstancedMesh(geometryOf(a.buf, a.rec.canopy), material, CANOPY_CAP);
      m.count = 0;
      m.frustumCulled = false;
      scene.add(m);
      return m;
    });
  }
}
