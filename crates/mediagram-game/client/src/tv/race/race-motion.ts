// What moves on the board each frame, worked out from the host's scene and
// the server clock alone, so a TV that reconnects mid-turn picks up where the
// game is: the die throw, a piece hopping field by field, a captured piece
// flying home, the move markers and the banner.

import * as THREE from "three";
import type { RaceScene } from "../../protocol/RaceScene";
import type { RaceStage } from "../../protocol/RaceStage";
import { BOARD_Y, CORNER_LEFT, TABLE_Y, TRACK, cellOf, hopCells, world, type Cell } from "./race-geometry";
import type { Hud } from "./race-hud";
import type { World } from "./race-world";

const DIE_X = 10; // the die rolls on the table beside the board, never through a piece
const DIE_REST_Y = TABLE_Y + 0.425;
const FACE_UP: Record<number, [number, number, number]> = {
  1: [0, 0, Math.PI / 2], 6: [0, 0, -Math.PI / 2], 2: [0, 0, 0], 5: [Math.PI, 0, 0], 3: [-Math.PI / 2, 0, 0], 4: [Math.PI / 2, 0, 0],
};
const HOP_LIFT = 0.55;
/** A captured piece's flight home; fits inside the host's capture time. */
export const CAPTURE_FLY_MS = 600;
const Y_AXIS = new THREE.Vector3(0, 1, 0);

/** Every banner text the game can show after a player's name, for drawing them up front. */
export function bannerTexts(names: string[]): string[] {
  const rolls = [1, 2, 3, 4, 5, 6].flatMap((v) => [` würfelt eine ${v}`, ` würfelt eine ${v} – kein Zug`, ` würfelt eine ${v} – welche Figur?`]);
  return [" ist dran", " würfelt nochmal", " würfelt …", ...rolls, " gewinnt!", ...names.map((n) => ` wirft ${n} raus!`)];
}

export class Motion {
  private rolledAt = -1;
  private from = new THREE.Vector3();
  private to = new THREE.Vector3(-DIE_X, DIE_REST_Y, 0);
  private face = new THREE.Quaternion().setFromEuler(new THREE.Euler(...FACE_UP[6]));
  private spin = new THREE.Vector3();
  private spun = new THREE.Quaternion();

  /** `names`: player id → name. */
  constructor(private w: World, private hud: Hud, private names: Map<number, string>) {}

  update(scene: RaceScene, now: number, t: number) {
    const seatOf = (player: number) => scene.seats.findIndex((s) => s.player === player);
    scene.seats.forEach((s, si) => s.pieces.forEach((p, pi) => this.put(si, pi, cellOf(s.corner, p, pi), 0)));
    for (const m of this.w.markers) m.ring.visible = m.target.visible = m.badge.visible = false;

    const st = scene.stage;
    const turn = st.stage === "final" ? seatOf(st.winner) : seatOf(st.player);
    scene.seats.forEach((s, si) => this.hud.setTag(si, si === turn, `${s.pieces.filter((p) => p !== null && p >= TRACK).length} im Ziel`));
    this.hud.setBanner(turn, this.banner(st, now));

    if (st.stage === "rolled") this.throwDie(st, scene.seats[turn].corner, now);
    else this.restDie(scene.die);
    if (st.stage === "rolled" && now >= st.lands_at && st.moves.length > 1) {
      st.moves.forEach((mv, i) => {
        const s = scene.seats[turn], m = this.w.markers[i];
        const [x, z] = world(cellOf(s.corner, s.pieces[mv.piece], mv.piece));
        const [tx, tz] = world(cellOf(s.corner, mv.to, mv.piece));
        m.ring.position.set(x, BOARD_Y + 0.012, z);
        m.target.position.set(tx, BOARD_Y + 0.014, tz);
        // low enough that a back-row piece's badge stays clear of the banner
        m.badge.position.set(x, BOARD_Y + 1.25 + Math.sin(t * 3 + i) * 0.05, z);
        m.ring.visible = m.target.visible = m.badge.visible = true;
      });
    }
    if (st.stage === "moving") {
      const s = scene.seats[turn], { mv } = st;
      const cells = hopCells(s.corner, mv.piece, mv.from, mv.to), hops = cells.length - 1;
      const e = (now - st.started_at) / st.hop_ms;
      if (e < hops) {
        const k = Math.max(0, Math.floor(e)), f = Math.max(0, e - k);
        this.put(turn, mv.piece, lerp(cells[k], cells[k + 1], f), Math.sin(f * Math.PI) * HOP_LIFT);
      }
      if (mv.captures) {
        const vi = seatOf(mv.captures.player), hit = cells[hops];
        const yard = cellOf(scene.seats[vi].corner, null, mv.captures.piece);
        const g = (now - st.started_at - hops * st.hop_ms) / CAPTURE_FLY_MS;
        if (g < 0) this.put(vi, mv.captures.piece, hit, 0);
        else if (g < 1) this.put(vi, mv.captures.piece, lerp(hit, yard, g), Math.sin(g * Math.PI) * 2.2);
      }
    }
  }

  private banner(st: RaceStage, now: number): string {
    switch (st.stage) {
      case "roll":
        return st.again ? " würfelt nochmal" : " ist dran";
      case "rolled":
        if (now < st.lands_at) return " würfelt …";
        if (st.moves.length === 0) return ` würfelt eine ${st.value} – kein Zug`;
        return st.moves.length > 1 ? ` würfelt eine ${st.value} – welche Figur?` : ` würfelt eine ${st.value}`;
      case "moving": {
        const victim = st.mv.captures?.player;
        return victim === undefined ? ` würfelt eine ${st.value}` : ` wirft ${this.names.get(victim) ?? "jemanden"} raus!`;
      }
      case "final":
        return " gewinnt!";
    }
  }

  private put(seat: number, piece: number, cell: Cell, lift: number) {
    const p = this.w.pawns[seat][piece], [x, z] = world(cell);
    p.body.position.set(x, BOARD_Y + lift, z);
    p.shadow.position.set(x, BOARD_Y + 0.004, z);
    const k = 1.05 * (1 - Math.min(0.5, lift * 0.4));
    p.shadow.scale.set(k, 1, k);
  }

  /** The die flies in from beside the active player's corner and lands showing the roll. */
  private throwDie(st: Extract<RaceStage, { stage: "rolled" }>, corner: number, now: number) {
    if (st.rolled_at !== this.rolledAt) {
      this.rolledAt = st.rolled_at;
      const side = CORNER_LEFT[corner] ? -1 : 1, z = (Math.random() - 0.5) * 3;
      this.from.set(side * (DIE_X + 3.2), DIE_REST_Y + 2.2, z + (Math.random() - 0.5) * 2);
      this.to.set(side * DIE_X, DIE_REST_Y, z);
      this.face.setFromEuler(new THREE.Euler(...FACE_UP[st.value])).premultiply(new THREE.Quaternion().setFromAxisAngle(Y_AXIS, Math.random() * Math.PI * 2));
      this.spin.set(Math.random() - 0.5, 0.3, Math.random() - 0.5).normalize();
    }
    const f = Math.min(1, Math.max(0, (now - st.rolled_at) / (st.lands_at - st.rolled_at))), e = 1 - f;
    const die = this.w.die;
    die.position.lerpVectors(this.from, this.to, 1 - e * e);
    die.position.y = this.to.y + Math.abs(Math.cos(f * Math.PI * 2.5)) * e * e * 1.4 + (this.from.y - this.to.y) * e * e * e;
    die.quaternion.copy(this.face).multiply(this.spun.setFromAxisAngle(this.spin, e * e * 14));
    this.shadeDie();
  }

  /** `last`: the host's last roll, shown when this TV has not seen a throw yet (it joined mid-game). */
  private restDie(last: number | null) {
    if (this.rolledAt < 0 && last) this.face.setFromEuler(new THREE.Euler(...FACE_UP[last]));
    this.w.die.position.copy(this.to);
    this.w.die.quaternion.copy(this.face);
    this.shadeDie();
  }

  private shadeDie() {
    const { die, dieShadow } = this.w;
    dieShadow.position.set(die.position.x, TABLE_Y + 0.01, die.position.z);
    const k = 1.5 / (1 + (die.position.y - DIE_REST_Y) * 0.5);
    dieShadow.scale.set(k, 1, k);
  }
}

function lerp(a: Cell, b: Cell, f: number): Cell {
  return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f];
}
