// The board's camera: the 55-degree overview of the whole board, leaning in on what the turn is about (the pawn walking, the
// dice with the pawn or hotel they decide on, the hotel at auction, the fields a question marks) and easing back out after.
// On the north road it leans in further and steeper, over the tall park hotels that would hide the pawn. Every change is
// Bauherr's critically damped glide, so the picture never jumps.

import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { glide } from "../bauherr/bauherr-camera";
import { pawnPlace } from "./grand-allee-clock";
import { fieldPoint, toSpot, type Board, type Spot } from "./grand-allee-layout";
import { CAMERA, DEEP, DIE, GLIDE, LEAN } from "./grand-allee-look";

export interface CameraPose {
  pos: [number, number, number];
  at: [number, number, number];
}
/** Where to look (x, z), how far to lean in (0 overview .. 1 full) and how deep (0 .. 1, the north road). */
export interface Wish extends Spot {
  lean: number;
  deep: number;
}

const mix = (a: number, b: number, k: number) => a + (b - a) * k;
const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

/** The pose at lean `k` and depth `d`, drawn towards the ground point (x, z). */
export function poseAt(k: number, d: number, x: number, z: number): CameraPose {
  const pan = mix(LEAN.pan, DEEP.pan, d) * k, dist = CAMERA.dist * (1 - mix(LEAN.dolly, DEEP.dolly, d) * k);
  const pitch = (mix(CAMERA.pitch, DEEP.pitch, d * k) * Math.PI) / 180;
  const ax = mix(CAMERA.at[0], x, pan), az = mix(CAMERA.at[1], z, pan);
  return { at: [ax, 0, az], pos: [ax, Math.sin(pitch) * dist, az + Math.cos(pitch) * dist] };
}

/** Between the two dice, on the ground. */
export function diceSpot(board: Board): Spot {
  const [px, py] = board.json.pond;
  return toSpot([px + (DIE.number[0] + DIE.permit[0]) / 2, py + (DIE.number[1] + DIE.permit[1]) / 2]);
}

/** Whether field `u` is on the frontage of a tall park hotel in front of the north road. */
export function deepField(scene: GrandAlleeScene, u: number): boolean {
  const n = scene.board.fields.length, f = ((Math.round(u) % n) + n) % n;
  return DEEP.hotels.some((h) => {
    const hotel = scene.board.hotels[h];
    return hotel !== undefined && f >= hotel.first && f <= hotel.last;
  });
}

const centre = (spots: Spot[]): Spot => ({ x: spots.reduce((a, s) => a + s.x, 0) / spots.length, z: spots.reduce((a, s) => a + s.z, 0) / spots.length });

/** What the turn asks of the camera; null: the overview. */
export function cameraWish(scene: GrandAlleeScene, board: Board, now: number): Wish | null {
  const t = scene.turn, dice = diceSpot(board);
  const plot = (h: number): Spot => board.plots[h] ?? dice;
  const on = (spots: Spot[], lean: number, deep = 0): Wish => ({ ...centre(spots), lean, deep });
  switch (t.stage) {
    case "walking":
    case "ask":
    case "rolled":
    case "guest": {
      const seat = scene.seats.findIndex((s) => s.player === t.player);
      if (seat < 0) return null;
      const u = pawnPlace(scene, seat, now).u, pawn = fieldPoint(board, u), deep = deepField(scene, u) ? 1 : 0;
      if (t.stage === "walking") return on([pawn], 1, deep);
      if (t.stage === "rolled") return on([pawn, dice], 0.5);
      if (t.stage === "guest") return on([pawn, plot(t.hotel), dice], 0.45);
      if (t.marks.length) return on(t.marks.map((m) => fieldPoint(board, m.field)), 0.6, t.marks.every((m) => deepField(scene, m.field)) ? 1 : 0);
      if (t.hotel !== null) return on([plot(t.hotel)], 0.6);
      return on([pawn], 0.5, deep);
    }
    case "permit":
      return on([plot(t.hotel), dice], 0.5);
    case "auction":
      return on([plot(t.auction.hotel)], 0.55);
    case "sold":
      return on([plot(t.hotel)], 0.55);
    default:
      return null;
  }
}

export class CameraRig {
  private k = 0;
  private d = 0;
  private x = CAMERA.at[0];
  private z = CAMERA.at[1];
  private v = { k: { v: 0 }, d: { v: 0 }, x: { v: 0 }, z: { v: 0 } };

  /** Moves the view on by `dt` seconds towards what the scene asks for. */
  update(scene: GrandAlleeScene, board: Board, now: number, dt: number): CameraPose {
    const wish = cameraWish(scene, board, now), step = Math.min(dt, 0.1);
    if (wish) {
      this.x = glide(this.x, wish.x, this.v.x, GLIDE.focus, step);
      this.z = glide(this.z, wish.z, this.v.z, GLIDE.focus, step);
    }
    this.k = glide(this.k, wish?.lean ?? 0, this.v.k, wish ? GLIDE.in : GLIDE.out, step);
    this.d = glide(this.d, wish?.deep ?? 0, this.v.d, GLIDE.in, step);
    return poseAt(clamp01(this.k), clamp01(this.d), this.x, this.z);
  }
}
