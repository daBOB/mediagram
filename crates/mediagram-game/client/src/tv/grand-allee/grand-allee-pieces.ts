// What the game moves on the board: each player's pawn (their avatar as an alpha-blended sprite on a disc in their colour; no alpha
// cut-outs, they cost the box under MSAA), soft shadow blobs under pawns and dice, the rings and button badges on the fields a
// question offers, and the coins of a guest's payment. Everything but the sprites is instanced: one draw call a kind.

import * as THREE from "three";
import type { AnswerColor } from "../../protocol/AnswerColor";
import { ANSWER_MARKS } from "../../ui/answers";
import { Blobs, MARK_COLORS } from "../bauherr/bauherr-pieces";
import { shade } from "./grand-allee-board";
import { COINS, PAWN, SUN } from "./grand-allee-look";

export interface SeatLook {
  name: string;
  color: string;
  figure: HTMLImageElement | null;
  head: HTMLImageElement | null;
}
/** A hotel's whole frontage can be offered at once: up to eight fields. */
const MARK_CAP = 8;

const draw = (w: number, h: number, f: (g: CanvasRenderingContext2D) => void) => {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  f(c.getContext("2d")!);
  return c;
};
const upload = (image: HTMLCanvasElement | HTMLImageElement, renderer: THREE.WebGLRenderer) => {
  const t = new THREE.Texture(image);
  t.colorSpace = THREE.SRGBColorSpace;
  t.needsUpdate = true;
  renderer.initTexture(t);
  return t;
};

export class Pieces {
  readonly pawns: THREE.Sprite[];
  /** Pawn discs, by seat. */
  readonly bases: THREE.InstancedMesh;
  readonly rings: THREE.InstancedMesh;
  readonly badges: THREE.Sprite[];
  readonly badgeMaps: Record<AnswerColor, THREE.Texture>;
  readonly coins: THREE.InstancedMesh;
  /** Under seat i's pawn: blob i; under the two dice the last two. */
  readonly blobs: Blobs;

  constructor(scene: THREE.Scene, renderer: THREE.WebGLRenderer, seats: SeatLook[]) {
    this.pawns = seats.map((s) => {
      // the avatar's figure, or the player's initial on a disc in their colour when there is none
      const image = s.figure ?? draw(128, 192, (g) => {
        g.fillStyle = s.color;
        g.beginPath();
        g.arc(64, 70, 54, 0, 7);
        g.fill();
        g.fillStyle = "#1c120b";
        g.font = '800 70px "Bricolage Grotesque", sans-serif';
        g.textAlign = "center";
        g.textBaseline = "middle";
        g.fillText([...s.name.trim()][0]?.toUpperCase() ?? "?", 64, 74);
      });
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: upload(image, renderer), transparent: true, color: "#fff6ea" }));
      sprite.center.set(0.5, 0);
      sprite.scale.set((PAWN.height * image.width) / image.height, PAWN.height, 1);
      return sprite;
    });
    this.bases = new THREE.InstancedMesh(shade(new THREE.CylinderGeometry(PAWN.base, PAWN.base * 1.08, 0.5, 28).translate(0, 0.25, 0)), new THREE.MeshBasicMaterial({ vertexColors: true }), Math.max(1, seats.length));
    seats.forEach((s, i) => this.bases.setColorAt(i, new THREE.Color(s.color)));
    this.bases.frustumCulled = false;

    this.rings = new THREE.InstancedMesh(new THREE.RingGeometry(2.3, 3.1, 40).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial(), MARK_CAP);
    this.rings.setColorAt(0, new THREE.Color()); // the colour buffer exists from the start, so the program is built with it
    this.rings.count = 0;
    this.rings.frustumCulled = false;
    const keys = Object.keys(MARK_COLORS) as AnswerColor[];
    this.badgeMaps = Object.fromEntries(keys.map((k) => [k, upload(draw(128, 128, (g) => {
      g.beginPath(); g.arc(64, 64, 58, 0, 7); g.fillStyle = "#fff"; g.fill();
      g.beginPath(); g.arc(64, 64, 50, 0, 7); g.fillStyle = MARK_COLORS[k]; g.fill();
      g.fillStyle = "#fff"; g.font = "900 56px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(ANSWER_MARKS[k], 64, 68);
    }), renderer)])) as Record<AnswerColor, THREE.Texture>;
    this.badges = Array.from({ length: MARK_CAP }, () => {
      const badge = new THREE.Sprite(new THREE.SpriteMaterial({ map: this.badgeMaps.blue, transparent: true, depthTest: false }));
      badge.scale.set(3.4, 3.4, 1);
      badge.visible = false;
      return badge;
    });

    this.coins = new THREE.InstancedMesh(shade(new THREE.CylinderGeometry(0.9, 0.9, 0.24, 20).rotateX(Math.PI / 2)), new THREE.MeshBasicMaterial({ vertexColors: true, color: "#f4c542" }), COINS.max);
    this.coins.count = 0;
    this.coins.frustumCulled = false;
    this.blobs = new Blobs(scene, seats.length + 2, { x: -SUN[0] / SUN[1], z: -SUN[2] / SUN[1] });
    this.blobs.mesh.position.y = 0.2; // above the board's layers
    scene.add(...this.pawns, this.bases, this.rings, this.coins, ...this.badges);
  }
}
