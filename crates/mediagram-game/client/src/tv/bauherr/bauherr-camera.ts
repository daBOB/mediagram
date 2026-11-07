// The board's camera: an overview of the whole town that, while a turn is rolled and walked, leans slightly towards
// the pawn on the move (a gentle zoom in, following its hops) and eases back out once the move is over. Every change
// is a slow critically damped glide, so the picture never jumps; the candidate fields, the guest payment and the permit
// die are asked or shown from the overview.

import type { BauherrScene } from "../../protocol/BauherrScene";
import { CAMERA, roadPoint } from "./bauherr-layout";
import { pawnPlace } from "./bauherr-pawn-place";

/** How far the view moves towards the pawn (of the way from the overview's centre) and how much closer it comes, at full lean. */
export const LEAN = { pan: 0.15, dolly: 0.05 };
/** The view never leans further forward than this: a pawn on the front road is already low in the frame, and the dice lie below it. */
const FOCUS_FRONT = 1.0;
/** Glide times in seconds (a critically damped follow settles in about 4 times this). */
const GLIDE = { in: 1.1, out: 1.5, focus: 0.7 };

export interface CameraPose {
  pos: [number, number, number];
  at: [number, number, number];
}

/** The pose at lean `k` (0 overview .. 1 full) with the view drawn towards the ground point (x, z). */
export function poseAt(k: number, x: number, z: number): CameraPose {
  const [px, py, pz] = CAMERA.pos, [tx, ty, tz] = CAMERA.at;
  const ax = tx + (x - tx) * LEAN.pan * k, az = tz + (Math.min(z, FOCUS_FRONT) - tz) * LEAN.pan * k, f = 1 - LEAN.dolly * k;
  return { at: [ax, ty, az], pos: [ax + (px - tx) * f, ty + (py - ty) * f, az + (pz - tz) * f] };
}

/** What the turn asks of the camera: lean in on the pawn while its owner rolls and walks, the overview otherwise. */
export function cameraWish(scene: BauherrScene, now: number): { lean: number; x: number; z: number } | null {
  const t = scene.turn;
  if (t.stage !== "roll" && t.stage !== "rolled" && t.stage !== "walking") return null;
  const seat = scene.seats.findIndex((s) => s.player === t.player);
  if (seat < 0) return null;
  const { x, z } = roadPoint(pawnPlace(scene, seat, now).u);
  return { lean: 1, x, z };
}

/** Critically damped approach of `cur` to `target` (no overshoot, no jerk); `v` is carried between calls. */
export function glide(cur: number, target: number, v: { v: number }, time: number, dt: number): number {
  const w = 2 / time, x = w * dt, e = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x), d = cur - target, tmp = (v.v + w * d) * dt;
  v.v = (v.v - w * tmp) * e;
  return target + (d + tmp) * e;
}

export class CameraRig {
  private k = 0;
  private x: number = CAMERA.at[0];
  private z: number = CAMERA.at[2];
  private vk = { v: 0 };
  private vx = { v: 0 };
  private vz = { v: 0 };

  /** Moves the view on by `dt` seconds towards what the scene asks for. */
  update(scene: BauherrScene, now: number, dt: number): CameraPose {
    const wish = cameraWish(scene, now), step = Math.min(dt, 0.1);
    if (wish) {
      this.x = glide(this.x, wish.x, this.vx, GLIDE.focus, step);
      this.z = glide(this.z, wish.z, this.vz, GLIDE.focus, step);
    }
    this.k = glide(this.k, wish ? wish.lean : 0, this.vk, wish ? GLIDE.in : GLIDE.out, step);
    return poseAt(Math.min(1, Math.max(0, this.k)), this.x, this.z);
  }
}
