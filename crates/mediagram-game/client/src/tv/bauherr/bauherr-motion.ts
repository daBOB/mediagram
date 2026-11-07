// What moves each frame, worked out from the host's scene and the server clock alone, so a TV that reconnects mid-turn
// picks up where the game is: hotels rising, pawns hopping field by field along the road, the dice, the candidate
// markers and the entrance canopies.

import * as THREE from "three";
import type { BauherrScene } from "../../protocol/BauherrScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import type { Dice } from "./bauherr-dice";
import { FIELD_SIZE, LOTS, roadPoint } from "./bauherr-layout";
import { MARK_COLORS, type Blobs, type Pieces } from "./bauherr-pieces";
import { stepHotel } from "./bauherr-stages";
import type { World } from "./bauherr-world";

const HOP_LIFT = 0.55;
/** A pawn's place on its field by seat (along the walk, across it), so up to four share a field. */
const SLOTS = [[-0.4, -0.22], [0.4, -0.22], [-0.4, 0.3], [0.4, 0.3]];

/** Where a pawn is: its field, or mid-hop while its owner walks (one field per `hop_ms`); `lift` is the hop's height. */
export function pawnPlace(scene: BauherrScene, seat: number, now: number): { u: number; lift: number } {
  const s = scene.seats[seat], t = scene.turn;
  if (t.stage !== "walking" || t.player !== s.player) return { u: s.field, lift: 0 };
  const progress = Math.min(Math.max((now - t.started_at) / t.hop_ms, 0), t.value);
  const whole = Math.floor(progress), f = progress - whole;
  return { u: t.from + progress, lift: whole >= t.value ? 0 : Math.sin(f * Math.PI) * HOP_LIFT };
}

export class BauherrMotion {
  private m = new THREE.Matrix4();
  private q = new THREE.Quaternion();
  private p = new THREE.Vector3();
  private s = new THREE.Vector3(1, 1, 1);
  private c = new THREE.Color();
  private up = new THREE.Vector3(0, 1, 0);

  constructor(private w: World, private pieces: Pieces, private dice: Dice, private blobs: Blobs) {}

  update(scene: BauherrScene, players: PlayerInfo[], now: number, t: number) {
    const byId = new Map(players.map((p) => [p.id, p]));
    this.w.hotels.forEach((h, i) => {
      const estate = scene.estates.find((e) => e.plot === i);
      stepHotel(h, estate, estate ? (byId.get(estate.owner)?.color ?? null) : null, t);
    });
    this.canopies(scene);
    this.pawns(scene, now);
    this.dice.update(scene.turn, now);
    const dice = [this.dice.number.mesh.position, this.dice.permit.mesh.position];
    dice.forEach((d, k) => this.blobs.set(scene.seats.length + k, d.x, d.z, 1.5 / (1 + (d.y - 0.4) * 0.5)));
    this.markers(scene, t);
  }

  private canopies(scene: BauherrScene) {
    const counts = scene.seats.map(() => 0);
    for (const e of scene.entrances) {
      const owner = scene.estates.find((s) => s.plot === e.plot)?.owner, seat = scene.seats.findIndex((s) => s.player === owner);
      if (seat < 0 || counts[seat] >= 16) continue;
      const f = roadPoint(e.field), lot = LOTS[e.plot];
      // beside the field, on the lot's side, its front towards the road
      let dx = lot.x - f.x, dz = lot.z - f.z;
      const l = Math.hypot(dx, dz) || 1;
      dx /= l;
      dz /= l;
      this.p.set(f.x + dx * 0.5, 0, f.z + dz * 0.5);
      this.q.setFromAxisAngle(this.up, Math.atan2(-dx, -dz));
      this.pieces.canopies[seat].setMatrixAt(counts[seat]++, this.m.compose(this.p, this.q, this.s));
    }
    this.pieces.canopies.forEach((c, i) => {
      c.count = counts[i];
      c.instanceMatrix.needsUpdate = true;
    });
  }

  private pawns(scene: BauherrScene, now: number) {
    scene.seats.forEach((seat, i) => {
      const { u, lift } = pawnPlace(scene, i, now), f = roadPoint(u), [a, b] = SLOTS[i % 4];
      const x = f.x + (Math.cos(f.ang) * a - Math.sin(f.ang) * b) * FIELD_SIZE, z = f.z + (Math.sin(f.ang) * a + Math.cos(f.ang) * b) * FIELD_SIZE;
      const sprite = this.pieces.pawns[i].sprite;
      sprite.position.set(x, lift + 0.06, z);
      sprite.material.opacity = seat.out ? 0.4 : 1;
      this.pieces.bases.setMatrixAt(i, this.m.compose(this.p.set(x, lift, z), this.q.identity(), this.s));
      this.blobs.set(i, x, z, 1.1 * (1 - Math.min(0.5, lift * 0.4)));
    });
    this.pieces.bases.instanceMatrix.needsUpdate = true;
  }

  private markers(scene: BauherrScene, t: number) {
    const turn = scene.turn, marks = turn.stage === "ask" ? turn.marks : [], { rings, badges } = this.pieces;
    rings.count = marks.length;
    badges.forEach((b) => (b.visible = false));
    marks.forEach((mk, i) => {
      const f = roadPoint(mk.field), k = 1 + Math.sin(t * 4 + i) * 0.06;
      rings.setMatrixAt(i, this.m.compose(this.p.set(f.x, 0.04, f.z), this.q.identity(), this.s.set(k, 1, k)));
      rings.setColorAt(i, this.c.set(MARK_COLORS[mk.color]));
      const badge = badges[Object.keys(MARK_COLORS).indexOf(mk.color)];
      badge.position.set(f.x, 1.2 + Math.sin(t * 3 + i) * 0.05, f.z);
      badge.visible = true;
    });
    this.s.set(1, 1, 1);
    rings.instanceMatrix.needsUpdate = true;
    if (rings.instanceColor) rings.instanceColor.needsUpdate = true;
  }
}
