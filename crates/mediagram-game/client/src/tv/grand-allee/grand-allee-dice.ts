// The two dice beside the pond: the number die (thrown for the move and again for a guest's nights) and the permit die. 4b's
// die-number.glb and die-permit.glb mark each face with an empty face_<value> at its centre (face_1..face_6; face_approved,
// face_rejected, face_free, face_double), from which the TV works out which turn brings a face up. Each throw flies from the
// host's rolled_at to lands_at, then shows its value until the next.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { flight } from "./grand-allee-clock";
import { toSpot, type Board } from "./grand-allee-layout";
import { DIE } from "./grand-allee-look";

const UP = new THREE.Vector3(0, 1, 0);
/** The throw's distances were tuned for a 1.2 m die: they scale with ours. */
const K = DIE.size / 1.2;
/** The turn that brings box face k (+x, -x, +y, -y, +z, -z) to the top. */
export const FACE_UP: [number, number, number][] = [[0, 0, Math.PI / 2], [0, 0, -Math.PI / 2], [0, 0, 0], [Math.PI, 0, 0], [-Math.PI / 2, 0, 0], [Math.PI / 2, 0, 0]];
/** The turns that show a value on top, by value. */
type Faces = Map<string, THREE.Quaternion[]>;
interface Die {
  obj: THREE.Object3D;
  faces: Faces;
  rest: THREE.Vector3;
  from: THREE.Vector3;
  to: THREE.Vector3;
  q: THREE.Quaternion;
  axis: THREE.Vector3;
  thrown: number;
}

const add = (faces: Faces, value: string, q: THREE.Quaternion) => faces.set(value, [...(faces.get(value) ?? []), q]);

/** The value an empty named face_<value> marks (repeats are face_approved_1 or face_approved001); null for any other node. */
export function faceValue(name: string): string | null {
  if (!name.startsWith("face_")) return null;
  const raw = name.slice(5);
  return raw.replace(/(_\d+|\d{3})$/, "") || raw;
}

/**
 * The turn that brings a face pointing along `dir` to the top: FACE_UP's for the box face `dir` points through (+x, -x, +y, -y,
 * +z, -z). The shortest arc would turn a face pointing down about any axis and could land its drawing upside down.
 */
export function faceTurn(dir: THREE.Vector3): THREE.Quaternion {
  const c = [dir.x, dir.y, dir.z], a = c.map(Math.abs), axis = a.indexOf(Math.max(...a));
  return new THREE.Quaternion().setFromEuler(new THREE.Euler(...FACE_UP[axis * 2 + (c[axis] < 0 ? 1 : 0)]));
}

/** 4b's die: its faces from the face_<value> empties at the face centres. */
function modelDie(root: THREE.Object3D) {
  root.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(root), centre = box.getCenter(new THREE.Vector3()), size = box.getSize(new THREE.Vector3());
  const faces: Faces = new Map(), obj = new THREE.Group();
  root.traverse((o) => {
    const value = faceValue(o.name);
    if (value !== null) add(faces, value, faceTurn(o.getWorldPosition(new THREE.Vector3()).sub(centre)));
  });
  root.position.sub(centre);
  obj.add(root);
  obj.scale.setScalar(DIE.size / Math.max(size.x, size.y, size.z, 1e-3));
  return { obj, faces };
}

export class Dice {
  readonly number: Die;
  readonly permit: Die;
  private spun = new THREE.Quaternion();
  private yaw = new THREE.Quaternion();

  constructor(scene: THREE.Scene, board: Board, models: { number: THREE.Object3D; permit: THREE.Object3D }) {
    const [px, py] = board.json.pond;
    const make = (die: { obj: THREE.Object3D; faces: Faces }, at: [number, number]): Die => {
      const s = toSpot([px + at[0], py + at[1]]), rest = new THREE.Vector3(s.x, DIE.size / 2, s.z);
      const q = [...die.faces.values()][0]?.[0]?.clone() ?? new THREE.Quaternion();
      die.obj.position.copy(rest);
      die.obj.quaternion.copy(q);
      scene.add(die.obj);
      return { ...die, rest, from: new THREE.Vector3(), to: rest.clone(), q, axis: new THREE.Vector3(0, 1, 0), thrown: -1 };
    };
    this.number = make(modelDie(models.number), DIE.number);
    this.permit = make(modelDie(models.permit), DIE.permit);
  }

  private throwTo(d: Die, value: string, side: number, rolledAt: number) {
    const all = d.faces.get(value) ?? [d.q];
    d.thrown = rolledAt;
    d.from.set(d.rest.x + side * 3.4 * K, d.rest.y + 3.2 * K, d.rest.z + (Math.random() - 0.5) * 0.6 * K);
    d.to.set(d.rest.x + (Math.random() - 0.5) * 0.4 * K, d.rest.y, d.rest.z + (Math.random() - 0.5) * 0.3 * K);
    d.q.copy(all[Math.floor(Math.random() * all.length)]).premultiply(this.yaw.setFromAxisAngle(UP, (Math.random() - 0.5) * 0.4));
    d.axis.set(Math.random() - 0.5, 0.3, Math.random() - 0.5).normalize();
  }
  private fly(d: Die, rolledAt: number, landsAt: number, now: number) {
    const f = flight(rolledAt, landsAt, now), e = 1 - f;
    d.obj.position.lerpVectors(d.from, d.to, 1 - e * e);
    d.obj.position.y = d.to.y + Math.abs(Math.cos(f * Math.PI * 2.5)) * e * e * 1.4 * K + (d.from.y - d.to.y) * e * e * e;
    d.obj.quaternion.copy(d.q).multiply(this.spun.setFromAxisAngle(d.axis, e * e * 14));
  }
  private rest(d: Die, value: string | null) {
    // a TV that joined after the throw shows how it fell; a die thrown here stays as it landed
    const all = d.thrown === -1 && value !== null ? d.faces.get(value) : undefined;
    if (all) d.q.copy(all[0]);
    d.obj.position.copy(d.to);
    d.obj.quaternion.copy(d.q);
  }

  /** The die the turn is throwing flies, once per rolled_at; the other shows what it last showed. */
  update(scene: GrandAlleeScene, now: number) {
    const t = scene.turn;
    const act = (d: Die, value: string, side: number, rolledAt: number, landsAt: number) => {
      if (d.thrown !== rolledAt) this.throwTo(d, value, side, rolledAt);
      this.fly(d, rolledAt, landsAt, now);
    };
    if (t.stage === "rolled") act(this.number, String(t.value), -1, t.rolled_at, t.lands_at);
    else if (t.stage === "guest") act(this.number, String(t.nights), -1, t.rolled_at, t.lands_at);
    else this.rest(this.number, scene.die === null ? null : String(scene.die));
    if (t.stage === "permit") act(this.permit, t.permit, 1, t.rolled_at, t.lands_at);
    else this.rest(this.permit, scene.permit);
  }
}
