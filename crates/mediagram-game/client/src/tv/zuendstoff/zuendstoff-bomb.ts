// The bomb (a black ball, a cap, a curled fuse and its spark, as in the approved render): held in a hand, flown on a
// parabola from the thrower's hand to the target's with a dotted trail, dropped and rolled to a missing player's feet, and
// gone off in a flash and a puff of smoke. Its lights stay in the scene at zero when unused: a light coming or going makes
// every material compile again.

import * as THREE from "three";
import { palm, type Seat } from "./zuendstoff-circle";
import { FALL_MS, type BombAt } from "./zuendstoff-motion";

const R = 0.3;
/** The ball's centre past the wrist, so it sits in the hand. */
const REACH = 0.34;
/** The flight's rise above the straight line, at its middle: a share of the distance, so a throw to a
 *  neighbour stays low (a fixed 1.7 left the frame over the far seats), capped for one across the table. */
const ARC_PER_M = 0.22;
const ARC_MAX = 1.7;
/** A loose bomb taken into a hand (picked up, or a catch the host confirmed late) slides there in this long, s. */
const TAKE_S = 0.25;
const TRAIL = 7;
/** A dropped bomb lands this far in front of the feet, then rolls to ROLL_TO, where the pick-up's hand reaches the felt. */
const LAND_AT = 0.8, ROLL_TO = 1.1, ROLL_MS = 650;
const PUFFS = 12, SMOKE_MS = 1800;

const ease = (x: number) => 1 - (1 - x) * (1 - x);
const clamp01 = (x: number) => Math.min(1, Math.max(0, x));

export class Bomb {
  private readonly body = new THREE.Group();
  private readonly spark: THREE.Mesh;
  private readonly glow = new THREE.PointLight("#ffb030", 0, 3);
  private readonly trail: THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial>[] = [];
  private readonly flash: THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial>;
  private readonly puffs: THREE.Mesh<THREE.SphereGeometry, THREE.MeshLambertMaterial>[] = [];
  private readonly blast = new THREE.PointLight("#ffa040", 0, 16);
  /** What reveal shows and hides. */
  private readonly parts: THREE.Object3D[];
  private kind: BombAt["kind"] = "none";
  /** Where the bomb was last frame, and where its flight or drop began. */
  private readonly at = new THREE.Vector3();
  private readonly from = new THREE.Vector3();
  private readonly end = new THREE.Vector3();
  private readonly dir = new THREE.Vector3();
  private taken = -Infinity;

  constructor(scene: THREE.Scene) {
    const ball = new THREE.Mesh(new THREE.SphereGeometry(R, 32, 20), new THREE.MeshStandardMaterial({ color: "#1d1d1f", roughness: 0.35, metalness: 0.2 }));
    ball.castShadow = true;
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.12, 0.13, 20), new THREE.MeshStandardMaterial({ color: "#3a3a3c", roughness: 0.5 }));
    cap.position.set(0.14, 0.26, 0);
    cap.rotation.z = -0.5;
    const curve = new THREE.CatmullRomCurve3([new THREE.Vector3(0.18, 0.32, 0), new THREE.Vector3(0.28, 0.48, 0.04), new THREE.Vector3(0.4, 0.53, 0)]);
    const fuse = new THREE.Mesh(new THREE.TubeGeometry(curve, 16, 0.022, 8), new THREE.MeshStandardMaterial({ color: "#c8a96b" }));
    this.spark = new THREE.Mesh(new THREE.SphereGeometry(0.065, 12, 8), new THREE.MeshBasicMaterial({ color: "#ffd84a" }));
    this.spark.position.set(0.4, 0.53, 0);
    this.body.add(ball, cap, fuse, this.spark);
    const dot = new THREE.SphereGeometry(0.05, 8, 6), puff = new THREE.SphereGeometry(1, 16, 12);
    for (let i = 0; i < TRAIL; i++) {
      this.trail.push(new THREE.Mesh(dot, new THREE.MeshBasicMaterial({ color: "#f6c731", transparent: true, depthWrite: false })));
    }
    this.flash = new THREE.Mesh(
      new THREE.SphereGeometry(1, 24, 16),
      new THREE.MeshBasicMaterial({ color: "#ffd56a", transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false }),
    );
    for (let i = 0; i < PUFFS; i++) {
      // lit, so each puff reads as a ball of smoke rather than a flat disc
      this.puffs.push(new THREE.Mesh(puff, new THREE.MeshLambertMaterial({ color: "#6f6a63", transparent: true, depthWrite: false })));
    }
    scene.add(this.body, this.glow, this.flash, this.blast, ...this.trail, ...this.puffs);
    this.parts = [this.body, this.flash, ...this.trail, ...this.puffs];
    this.reveal(false);
  }

  /** Shows every part at once (for the up-front compile), or hides them until play shows them. */
  reveal(on: boolean) {
    for (const o of this.parts) o.visible = on;
  }

  /** `t`: the frame clock, s (the spark's flicker). */
  update(b: BombAt, seats: Seat[], t: number) {
    const was = this.kind, p = this.at;
    this.kind = b.kind;
    this.reveal(false);
    if (b.kind === "hand") {
      if (was === "loose") {
        this.from.copy(p);
        this.taken = t;
      }
      // never into the felt: the pick-up's hand reaches below the ball's centre
      palm(seats[b.seat], p, REACH).setY(Math.max(R, p.y));
      if (t - this.taken < TAKE_S) p.lerpVectors(this.from, p, (t - this.taken) / TAKE_S);
    } else if (b.kind === "flight") {
      // from the hand it left; a TV joining mid-flight starts it from the thrower's hand
      if (was === "hand") this.from.copy(p);
      else if (was !== "flight") palm(seats[b.from], this.from, REACH);
      palm(seats[b.to], this.end, REACH);
      this.arc(b.k, p);
      this.trail.forEach((dot, i) => {
        const k = b.k - (i + 1) * 0.07;
        dot.visible = k > 0;
        this.arc(k, dot.position);
        dot.material.opacity = 0.75 - i * 0.09;
      });
    } else if (b.kind === "loose") {
      if (was === "hand" || was === "flight") this.from.copy(p);
      else if (was !== "loose") palm(seats[b.seat], this.from, REACH);
      this.drop(seats[b.seat], b.ms, p);
    } else if (b.kind === "blast") {
      // where it went off; a TV joining mid-blast puts it in the victim's hand
      if (was === "none") palm(seats[b.seat], p, REACH);
      this.boom(b.ms, p);
    }
    const shown = b.kind === "hand" || b.kind === "flight" || b.kind === "loose";
    this.body.visible = shown;
    this.body.position.copy(p);
    if (b.kind !== "loose") this.body.rotation.set(0, 0, b.kind === "flight" ? -b.k * 5 : 0.35);
    this.spark.scale.setScalar(0.8 + 0.6 * Math.abs(Math.sin(t * 37)));
    this.glow.position.copy(p).add(this.spark.position);
    this.glow.intensity = shown ? 4 + 2 * Math.abs(Math.sin(t * 23)) : 0;
  }

  /** On the parabola from `from` to `end`, `k` of the way. */
  private arc(k: number, out: THREE.Vector3) {
    out.lerpVectors(this.from, this.end, k);
    out.y += Math.min(ARC_MAX, ARC_PER_M * this.from.distanceTo(this.end)) * 4 * k * (1 - k);
  }

  /** Falls from the hands to the felt in front of the feet, hops once and rolls on. */
  private drop(seat: Seat, ms: number, p: THREE.Vector3) {
    const inward = this.dir.copy(seat.pos).setY(0).normalize().negate();
    const land = this.end.copy(seat.pos).addScaledVector(inward, LAND_AT).setY(R);
    if (ms < FALL_MS) {
      const u = clamp01(ms / FALL_MS);
      p.lerpVectors(this.from, land, u);
      p.y = this.from.y + (R - this.from.y) * u * u;
      return;
    }
    const v = clamp01((ms - FALL_MS) / ROLL_MS), d = (ROLL_TO - LAND_AT) * ease(v);
    p.copy(land).addScaledVector(inward, d);
    p.y = R + 0.28 * Math.sin(Math.PI * Math.min(1, v * 1.6)) * (1 - v);
    // rolling: turned about the axis across its path by the distance over its radius
    this.body.quaternion.setFromAxisAngle(inward.set(-inward.z, 0, inward.x), d / R);
  }

  /** A flash that swells and fades, its light on the felt and faces, and puffs of smoke rising round it. */
  private boom(ms: number, p: THREE.Vector3) {
    const f = clamp01(ms / 350);
    this.flash.visible = f < 1;
    this.flash.position.copy(p);
    this.flash.scale.setScalar(0.3 + 1.5 * ease(clamp01(ms / 250)));
    this.flash.material.opacity = 1 - f;
    this.blast.position.copy(p).setY(p.y + 0.6);
    this.blast.intensity = 120 * (1 - clamp01(ms / 600));
    this.puffs.forEach((puff, i) => {
      const v = clamp01((ms - 60 - i * 25) / SMOKE_MS), a = i * 2.4;
      puff.visible = v > 0 && v < 1;
      const out = 0.15 + (0.5 + (i % 4) * 0.15) * ease(v);
      puff.position.set(p.x + Math.cos(a) * out, p.y + 1.6 * v + (i % 3) * 0.25, p.z + Math.sin(a) * out);
      puff.scale.setScalar((0.22 + (i % 3) * 0.06) * (1 + 1.6 * ease(v)));
      puff.material.opacity = 0.8 * (1 - v);
    });
  }
}
