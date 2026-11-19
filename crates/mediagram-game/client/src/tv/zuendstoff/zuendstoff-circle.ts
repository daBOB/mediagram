// The players' avatars standing in a circle on the felt, facing the middle: each plays the clip its pose names, crossfading
// from the last, turns towards a throw's target while throwing, and turns see-through once out. A player whose avatar did not
// load stands there as their flat figure (or a pawn), still.

import * as THREE from "three";
import { flatFigure, type loadAvatar } from "../ceremony/ceremony-avatar";
import type { Look } from "../table/stage-host";
import { CLIP, FADE_S, THROW_DIR, type Pose } from "./zuendstoff-motion";

export type Avatar = Awaited<ReturnType<typeof loadAvatar>>;
export const CIRCLE_R = 4.5;
export const AVATAR_H = 2.5;
/** An out player's opacity. */
const GHOST = 0.35;
/** A clip further than this off the time its pose asks for is put back on it (a TV joining mid-clip). */
const SLACK_S = 0.15;

/**
 * Meshy's newer clips (throw, catch, miss, pick-up) carry bone offsets for a skeleton ~1.7 times the avatar's own: played as
 * they are, the avatar grows. Scales each clip's position tracks to the bones' own lengths.
 */
function fitClips(root: THREE.Object3D, clips: THREE.AnimationClip[]) {
  const rest = new Map<string, number>();
  root.traverse((o) => {
    if ((o as THREE.Bone).isBone) rest.set(o.name, o.position.length());
  });
  for (const clip of clips) {
    const tracks = clip.tracks.filter((t) => t.name.endsWith(".position"));
    const ratios = tracks.flatMap((t) => {
      const len = Math.hypot(t.values[0], t.values[1], t.values[2]), own = rest.get(t.name.slice(0, -".position".length)) ?? 0;
      return !t.name.startsWith("Hips.") && own > 1e-6 && len > 1e-6 ? [own / len] : [];
    }).sort((a, b) => a - b);
    const k = ratios[ratios.length >> 1];
    if (k && Math.abs(k - 1) > 0.05) for (const t of tracks) t.values.forEach((v, i, a) => (a[i] = v * k));
  }
}

/** Plays poses on one avatar: a new clip crossfades in, a running one is only corrected when it drifts. */
class Animator {
  private readonly mixer: THREE.AnimationMixer;
  private current: THREE.AnimationAction | null = null;
  private clip = "";

  constructor(root: THREE.Object3D, private readonly clips: THREE.AnimationClip[], private readonly phase: number) {
    this.mixer = new THREE.AnimationMixer(root);
  }

  play(pose: Pose, dt: number) {
    const clip = THREE.AnimationClip.findByName(this.clips, pose.clip) ?? THREE.AnimationClip.findByName(this.clips, CLIP.idle);
    if (!clip) return;
    const end = clip.duration - 1e-3, at = pose.at === null ? null : Math.min(Math.max(pose.at, 0), end);
    if (clip.name !== this.clip) {
      const next = this.mixer.clipAction(clip).reset();
      next.setLoop(at === null ? THREE.LoopRepeat : THREE.LoopOnce, Infinity);
      next.clampWhenFinished = true;
      next.time = at ?? (this.phase % clip.duration);
      next.play();
      if (this.current) next.crossFadeFrom(this.current, FADE_S, false);
      this.current = next;
      this.clip = clip.name;
    } else if (at !== null && Math.abs(this.current!.time - at) > SLACK_S) Object.assign(this.current!, { time: at, paused: false });
    this.current!.timeScale = pose.rate;
    this.mixer.update(dt);
  }

  dispose() {
    this.mixer.stopAllAction();
    this.mixer.uncacheRoot(this.mixer.getRoot());
  }
}

export interface Seat {
  /** On the circle; turned by a throw's aim. */
  holder: THREE.Group;
  pos: THREE.Vector3;
  /** Facing the middle. */
  base: number;
  hand: THREE.Object3D;
  /** The forearm, for which way the hand points; null for a stand-in. */
  arm: THREE.Object3D | null;
  anim: Animator | null;
  /** Each mesh with its own material and its see-through copy. */
  looks: [THREE.Mesh, THREE.Material, THREE.Material][];
  out: boolean;
}

const yawTo = (from: THREE.Vector3, to: THREE.Vector3) => Math.atan2(to.x - from.x, to.z - from.z);
const wrap = (a: number) => Math.atan2(Math.sin(a), Math.cos(a));

/** Stands everyone on the circle, seat 0 at the front (towards the camera). */
export function buildCircle(scene: THREE.Scene, looks: Look[], avatars: (Avatar | null)[]): Seat[] {
  return looks.map((look, i) => {
    const a = Math.PI / 2 + (i / looks.length) * Math.PI * 2;
    const pos = new THREE.Vector3(Math.cos(a) * CIRCLE_R, 0, Math.sin(a) * CIRCLE_R), base = yawTo(pos, new THREE.Vector3());
    const avatar = avatars[i];
    let holder: THREE.Group, hand: THREE.Object3D | undefined, arm: THREE.Object3D | null = null, anim: Animator | null = null;
    if (avatar) {
      holder = avatar.holder;
      fitClips(avatar.root, avatar.clips);
      hand = avatar.root.getObjectByName("RightHand");
      arm = avatar.root.getObjectByName("RightForeArm") ?? null;
      anim = new Animator(avatar.root, avatar.clips, i * 0.9);
    } else {
      const map = look.figure ? Object.assign(new THREE.Texture(look.figure), { colorSpace: THREE.SRGBColorSpace, needsUpdate: true }) : null;
      holder = new THREE.Group().add(flatFigure(map, look.color, AVATAR_H));
    }
    if (!hand) {
      hand = new THREE.Object3D();
      hand.position.set(-0.55, AVATAR_H * 0.55, 0.35);
      holder.add(hand);
    }
    holder.position.copy(pos);
    holder.rotation.y = base;
    const meshes: [THREE.Mesh, THREE.Material, THREE.Material][] = [];
    holder.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (!mesh.isMesh && !(o as THREE.Sprite).isSprite) return;
      mesh.castShadow = true;
      const own = mesh.material as THREE.Material, ghost = own.clone();
      Object.assign(ghost, { transparent: true, opacity: GHOST });
      meshes.push([mesh, own, ghost]);
    });
    scene.add(holder);
    return { holder, pos, base, hand, arm, anim, looks: meshes, out: false };
  });
}

export function setOut(seat: Seat, out: boolean) {
  if (seat.out === out) return;
  seat.out = out;
  for (const [mesh, own, ghost] of seat.looks) mesh.material = out ? ghost : own;
}

/** Plays the frame's poses and turns each thrower towards their target. */
export function poseCircle(seats: Seat[], poses: Pose[], dt: number) {
  seats.forEach((seat, i) => {
    const pose = poses[i];
    if (!pose) return;
    const aim = pose.aim && seats[pose.aim.to];
    // the clip lets go THROW_DIR round from where the avatar faces; turned so that is the target
    seat.holder.rotation.y = seat.base + (aim ? pose.aim!.k * wrap(yawTo(seat.pos, aim.pos) - seat.base - THROW_DIR) : 0);
    seat.anim?.play(pose, dt);
  });
}

const armAt = new THREE.Vector3();
/** Where a held bomb sits: just past the hand, along the forearm (the avatars have no finger bones). */
export function palm(seat: Seat, out: THREE.Vector3, reach: number): THREE.Vector3 {
  seat.hand.getWorldPosition(out);
  if (!seat.arm) return out;
  seat.arm.getWorldPosition(armAt);
  return out.add(armAt.sub(out).normalize().multiplyScalar(-reach));
}

export function disposeCircle(seats: Seat[]) {
  for (const s of seats) {
    s.anim?.dispose();
    for (const [, , ghost] of s.looks) ghost.dispose();
  }
}
