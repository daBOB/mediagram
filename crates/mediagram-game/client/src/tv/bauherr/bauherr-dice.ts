// The two dice on the plaza: an ivory number die and an oak permit die. Their faces are painted by the bake (3 × 2 cells
// of one image each); here they are mapped, thrown at the host's times and left showing the roll.

import * as THREE from "three";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";
import type { BauherrPermit } from "../../protocol/BauherrPermit";
import type { BauherrTurn } from "../../protocol/BauherrTurn";
import { DICE_AT } from "./bauherr-layout";

/** Box faces +x, -x, +y, -y, +z, -z. */
export const NUMBER_FACES = [1, 6, 2, 5, 3, 4];
export const PERMIT_FACES = ["ok", "no", "ok", "free", "ok", "x2"] as const;
const PERMIT_FACE: Record<BauherrPermit, (typeof PERMIT_FACES)[number]> = { approved: "ok", rejected: "no", free: "free", double: "x2" };
/** The turn that brings box face k to the top. */
export const FACE_UP: [number, number, number][] = [[0, 0, Math.PI / 2], [0, 0, -Math.PI / 2], [0, 0, 0], [Math.PI, 0, 0], [-Math.PI / 2, 0, 0], [Math.PI / 2, 0, 0]];
const SIZE = 0.8, REST_Y = SIZE / 2;
const Y_AXIS = new THREE.Vector3(0, 1, 0);

function dieGeometry() {
  const geo = new RoundedBoxGeometry(SIZE, SIZE, SIZE, 4, 0.11), uv = geo.attributes.uv;
  // the six groups share vertices only within a face, so each vertex moves once, into its face's cell of the 3 × 2 image
  geo.groups.forEach((grp, k) => {
    const col = k % 3, row = Math.floor(k / 3), seen = new Set<number>();
    for (let i = grp.start; i < grp.start + grp.count; i++) seen.add(geo.index ? geo.index.getX(i) : i);
    for (const j of seen) uv.setXY(j, (col + 0.03 + uv.getX(j) * 0.94) / 3, (row + 0.03 + uv.getY(j) * 0.94) / 2);
  });
  return geo;
}
/** The turn about the vertical that leaves each face's drawing upright for the viewer. */
export function upYaws(): number[] {
  const g = new RoundedBoxGeometry(SIZE, SIZE, SIZE, 4, 0.11), pos = g.attributes.position, uv = g.attributes.uv, idx = g.index;
  return g.groups.map((grp, k) => {
    const hi = new THREE.Vector3(), lo = new THREE.Vector3(), v = new THREE.Vector3();
    for (let i = grp.start; i < grp.start + grp.count; i++) {
      const j = idx ? idx.getX(i) : i;
      v.fromBufferAttribute(pos, j);
      if (uv.getY(j) > 0.9) hi.add(v);
      else if (uv.getY(j) < 0.1) lo.add(v);
    }
    const up = hi.normalize().sub(lo.normalize()).applyEuler(new THREE.Euler(...FACE_UP[k]));
    return Math.PI - Math.atan2(up.x, up.z);
  });
}

interface Die {
  mesh: THREE.Mesh;
  rest: THREE.Vector3;
  from: THREE.Vector3;
  to: THREE.Vector3;
  q: THREE.Quaternion;
  axis: THREE.Vector3;
  thrown: number;
}

export class Dice {
  readonly number: Die;
  readonly permit: Die;
  private yaws = upYaws();
  private spun = new THREE.Quaternion();

  constructor(scene: THREE.Scene, ivory: THREE.Texture, oak: THREE.Texture) {
    const geo = dieGeometry(), make = (map: THREE.Texture, at: readonly [number, number]): Die => {
      geo.clearGroups();
      const mesh = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map })), rest = new THREE.Vector3(at[0], REST_Y, at[1]);
      mesh.position.copy(rest);
      mesh.quaternion.setFromEuler(new THREE.Euler(...FACE_UP[2]));
      scene.add(mesh);
      return { mesh, rest, from: new THREE.Vector3(), to: rest.clone(), q: mesh.quaternion.clone(), axis: new THREE.Vector3(0, 1, 0), thrown: -1 };
    };
    this.number = make(ivory, DICE_AT.number);
    this.permit = make(oak, DICE_AT.permit);
  }

  private throwTo(d: Die, face: number, side: number, rolledAt: number) {
    d.thrown = rolledAt;
    const yaw = this.yaws[face] + (Math.random() - 0.5) * 0.4;
    d.from.set(d.rest.x + side * 3.6, d.rest.y + 2.6, d.rest.z + (Math.random() - 0.5) * 0.6);
    d.to.set(d.rest.x + (Math.random() - 0.5) * 0.4, d.rest.y, d.rest.z + (Math.random() - 0.5) * 0.3);
    d.q.setFromEuler(new THREE.Euler(...FACE_UP[face])).premultiply(new THREE.Quaternion().setFromAxisAngle(Y_AXIS, yaw));
    d.axis.set(Math.random() - 0.5, 0.3, Math.random() - 0.5).normalize();
  }
  private fly(d: Die, rolledAt: number, landsAt: number, now: number) {
    const f = Math.min(1, Math.max(0, (now - rolledAt) / (landsAt - rolledAt))), e = 1 - f;
    d.mesh.position.lerpVectors(d.from, d.to, 1 - e * e);
    d.mesh.position.y = d.to.y + Math.abs(Math.cos(f * Math.PI * 2.5)) * e * e * 1.4 + (d.from.y - d.to.y) * e * e * e;
    d.mesh.quaternion.copy(d.q).multiply(this.spun.setFromAxisAngle(d.axis, e * e * 14));
  }
  private rest(d: Die) {
    d.mesh.position.copy(d.to);
    d.mesh.quaternion.copy(d.q);
  }

  /** The die the turn is throwing, thrown once per `rolled_at`; the other stays where it landed. */
  update(turn: BauherrTurn, now: number) {
    const act = (d: Die, face: number, side: number, rolledAt: number, landsAt: number) => {
      if (d.thrown !== rolledAt) this.throwTo(d, face, side, rolledAt);
      this.fly(d, rolledAt, landsAt, now);
    };
    if (turn.stage === "rolled") act(this.number, NUMBER_FACES.indexOf(turn.value), -1, turn.rolled_at, turn.lands_at);
    else if (turn.stage === "guest") act(this.number, NUMBER_FACES.indexOf(turn.nights), -1, turn.rolled_at, turn.lands_at);
    else this.rest(this.number);
    if (turn.stage === "permit") {
      const want = PERMIT_FACE[turn.outcome], all = PERMIT_FACES.flatMap((f, i) => (f === want ? [i] : []));
      act(this.permit, all[Math.floor(Math.random() * all.length)], 1, turn.rolled_at, turn.lands_at);
    } else this.rest(this.permit);
  }
}
