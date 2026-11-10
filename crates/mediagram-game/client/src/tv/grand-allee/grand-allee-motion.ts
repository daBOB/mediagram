// What moves each frame, worked out from the host's scene and the server clock alone (so a TV that joins mid-turn picks up where
// the game is), plus the animations of what changed since the last scene: a stage rising, a plot changing hands, an entrance
// dropping in, a pawn going broke, a guest's coins flying to the owner's hotel. Nothing that stands still is uploaded again.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { coinCount, coinProgress, pawnPlace, sharing } from "./grand-allee-clock";
import type { Dice } from "./grand-allee-dice";
import type { Changes } from "./grand-allee-events";
import { stepHotel, type HotelView } from "./grand-allee-hotels";
import { fieldAt, fieldPoint, type Board } from "./grand-allee-layout";
import { COINS, DIE, MARK_COLORS, PAWN, TIMES } from "./grand-allee-look";
import type { Pieces } from "./grand-allee-pieces";
import type { Grounds } from "./grand-allee-plots";

/** Where pawns sharing a field stand, metres: along the road and across it. */
const SLOT = { along: 1.8, across: 1.5 };
const Y = new THREE.Vector3(0, 1, 0);

export class GrandAlleeMotion {
  /** When (frame clock, s) a plot changed hands, an entrance was placed (by field), a player went out. */
  private pulses = new Map<number, number>();
  private drops = new Map<number, number>();
  private topples = new Map<number, number>();
  /** Per pawn, then per die: what was last drawn (x, y, z and one more), so what stands still is not uploaded again. */
  private drawn: Float64Array;
  private m = new THREE.Matrix4();
  private q = new THREE.Quaternion();
  private p = new THREE.Vector3();
  private s = new THREE.Vector3(1, 1, 1);
  private c = new THREE.Color();

  constructor(private board: Board, private hotels: HotelView[], private grounds: Grounds, private pieces: Pieces, private dice: Dice) {
    this.drawn = new Float64Array((pieces.pawns.length + 2) * 4).fill(NaN);
  }

  /** Starts the animations of what changed since the last scene (t: frame clock, s). */
  changed(c: Changes, t: number) {
    for (const h of c.owners) this.pulses.set(h, t);
    for (const f of c.entrances) this.drops.set(f, t);
    for (const p of c.out) this.topples.set(p, t);
  }

  /** After a gap (frames stopped, the socket was down): the board as it stands, nothing replayed. */
  restart() {
    this.pulses.clear();
    this.drops.clear();
    this.topples.clear();
    for (const h of this.hotels) h.seen = false;
  }

  update(scene: GrandAlleeScene, colorOf: (player: number) => string, now: number, t: number) {
    const estates = new Map(scene.estates.map((e) => [e.hotel, e]));
    this.hotels.forEach((h, i) => {
      const e = estates.get(i);
      stepHotel(h, e?.levels ?? 0, e ? colorOf(e.owner) : null, t);
    });
    this.grounds.update(scene, colorOf, this.pulses, this.drops, t);
    this.pawns(scene, now, t);
    this.dice.update(scene, now);
    [this.dice.number, this.dice.permit].forEach((d, k) => {
      const { x, y, z } = d.obj.position, i = this.pieces.pawns.length + k;
      if (this.moved(i, x, y, z, 0)) this.pieces.blobs.set(i, x, z, (DIE.size * 1.9) / (1 + Math.max(0, y - DIE.size / 2) * 0.15));
    });
    this.coins(scene, now);
    this.markers(scene, t);
  }

  private moved(i: number, x: number, y: number, z: number, w: number) {
    const d = this.drawn, k = i * 4;
    if (d[k] === x && d[k + 1] === y && d[k + 2] === z && d[k + 3] === w) return false;
    d.set([x, y, z, w], k);
    return true;
  }

  private pawns(scene: GrandAlleeScene, now: number, t: number) {
    const share = sharing(scene), { pawns, bases, blobs } = this.pieces;
    let dirty = false;
    scene.seats.forEach((seat, i) => {
      const sprite = pawns[i];
      if (!sprite) return;
      const { u, lift } = pawnPlace(scene, i, now), at = fieldPoint(this.board, u), rot = fieldAt(this.board, u).rot, { k, n } = share[i];
      const along = n > 1 ? ((k % 4) - (Math.min(n, 4) - 1) / 2) * SLOT.along : 0, across = n > 4 ? (k < 4 ? -1 : 1) * SLOT.across : 0;
      const x = at.x + Math.cos(rot) * along + Math.sin(rot) * across, z = at.z - Math.sin(rot) * along + Math.cos(rot) * across;
      // going broke: the pawn topples about its feet, fades and leaves the board (one out before this TV joined is simply gone)
      const since = this.topples.get(seat.player), v = !seat.out ? 0 : since === undefined ? 1 : Math.min(1, (t - since) / TIMES.topple);
      if (!this.moved(i, x, lift, z, v)) return;
      sprite.visible = v < 1;
      sprite.material.rotation = (-v * Math.PI) / 2;
      sprite.material.opacity = 1 - v * v;
      sprite.position.set(x, lift + 0.5, z);
      bases.setMatrixAt(i, this.m.compose(this.p.set(x, lift, z), this.q.identity(), this.s.setScalar(v < 1 ? 1 - v * 0.7 : 0)));
      blobs.set(i, x, z, v < 1 ? PAWN.base * 3.7 * (1 - Math.min(0.5, lift * 0.12)) : 0);
      dirty = true;
    });
    this.s.set(1, 1, 1);
    if (dirty) bases.instanceMatrix.needsUpdate = true;
  }

  // a guest's payment: the coins leave the payer's pawn once the nights die lands and reach the owner's hotel at paid_at
  private coins(scene: GrandAlleeScene, now: number) {
    const t = scene.turn, coins = this.pieces.coins, was = coins.count;
    let m = 0;
    if (t.stage === "guest" && now >= t.lands_at && now <= t.paid_at) {
      const seat = scene.seats.findIndex((s) => s.player === t.player), plot = this.board.plots[t.hotel];
      if (seat >= 0 && plot) {
        const from = fieldPoint(this.board, pawnPlace(scene, seat, now).u), n = coinCount(t.amount);
        for (let k = 0; k < n; k++) {
          const p = coinProgress(k, n, t.lands_at, t.paid_at, now);
          if (p === null) continue;
          this.p.set(from.x + (plot.x - from.x) * p, 3 + 3 * p + Math.sin(p * Math.PI) * COINS.arc, from.z + (plot.z - from.z) * p);
          coins.setMatrixAt(m++, this.m.compose(this.p, this.q.setFromAxisAngle(Y, p * 12 + k), this.s));
        }
      }
    }
    coins.count = m;
    if (m || was) coins.instanceMatrix.needsUpdate = true;
  }

  // the fields a question offers: a ring on each and the phone button's badge above it
  private markers(scene: GrandAlleeScene, t: number) {
    const turn = scene.turn, marks = turn.stage === "ask" ? turn.marks : [], { rings, badges, badgeMaps } = this.pieces;
    const n = Math.min(marks.length, badges.length), was = rings.count;
    rings.count = n;
    if (!n && !was) return;
    badges.forEach((b, i) => (b.visible = i < n));
    for (let i = 0; i < n; i++) {
      const mk = marks[i], f = fieldPoint(this.board, mk.field), k = 1 + Math.sin(t * 4 + i) * 0.06;
      rings.setMatrixAt(i, this.m.compose(this.p.set(f.x, 0.2, f.z), this.q.identity(), this.s.set(k, 1, k)));
      rings.setColorAt(i, this.c.set(MARK_COLORS[mk.color]));
      badges[i].material.map = badgeMaps[mk.color];
      badges[i].position.set(f.x, 7 + Math.sin(t * 3 + i) * 0.25, f.z);
    }
    this.s.set(1, 1, 1);
    rings.instanceMatrix.needsUpdate = true;
    if (rings.instanceColor) rings.instanceColor.needsUpdate = true;
  }
}
