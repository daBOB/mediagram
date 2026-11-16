// Which clip every avatar plays, from which time, and where the bomb is, worked out from the scene and the host clock alone
// (plus the last throw seen, whose follow-through outlasts its stage): a TV joining mid-throw shows the same as one that saw
// it all. No three.js here.

import type { ZuendstoffScene } from "../../protocol/ZuendstoffScene";
import type { ZuendstoffStage } from "../../protocol/ZuendstoffStage";
import type { BoardSound } from "../table/board-sounds";
import type { Plan } from "../table/tv-sound";

export type Flight = Extract<ZuendstoffStage, { stage: "flight" }>;
type Hold = Extract<ZuendstoffStage, { stage: "hold" }>;

/** The host's throw lead-in (tap to release) and pick-up after a drop (ZuendstoffConfig); the wire carries neither. */
export const THROW_LEAD_MS = 600;
export const PICKUP_MS = 2000;
/** Crossfade between two clips, s. */
export const FADE_S = 0.25;

export const CLIP = {
  idle: "Idle_15",
  throw: "Over_Shoulder_Throw",
  catch: "Leap_Right_And_Catch",
  miss: "Jump_To_Catch_And_Fall",
  pickup: "Male_Bend_Over_Pick_Up",
  fall: "Falling_Down",
  win: "victory",
} as const;
export type Clip = (typeof CLIP)[keyof typeof CLIP];

// Clip times, found by sampling the RightHand bone over each clip on the avatar copies (the clip is the same on all 22).
/** Over_Shoulder_Throw: the hand at its highest over the shoulder, about to whip forward; the clip ends bent over at 4.3 s. */
const THROW_RELEASE_S = 3.3;
const THROW_END_S = 4.3;
/** The throw turns the body ~140° and lets go this far round (radians, from facing ahead towards the avatar's left). */
export const THROW_DIR = (155 * Math.PI) / 180;
/** Leap_Right_And_Catch: the top of the leap, hands up; done at 2.67 s. */
const CATCH_S = 0.85;
const CATCH_END_S = 2.67;
/** Jump_To_Catch_And_Fall: the top of the jump, grabbing at nothing; flat on the floor at 2.3 s. */
const GRAB_S = 1.55;
/** After the landing the miss plays this long (into the fall), then the pick-up takes over. */
const MISS_FOR_S = 0.8;
/** Male_Bend_Over_Pick_Up from mid-bend, the hand on the floor at 1.5 s, upright again at 2.8 s, sped up to be done at ready_at. */
const PICK_FROM_S = 1.0;
const PICK_S = 1.5;
const PICK_TO_S = 2.8;
const PICK_RATE = (PICK_TO_S - PICK_FROM_S) / (PICKUP_MS / 1000 - MISS_FOR_S);
/** From the landing of a missed bomb until the hand closes on it. */
export const PICK_DELAY_MS = (MISS_FOR_S + (PICK_S - PICK_FROM_S) / PICK_RATE) * 1000;
/** A dropped bomb hits the felt this long after the landing (the drop's thunk). */
export const FALL_MS = 450;

export interface Pose {
  clip: Clip;
  /** Seconds into the clip; null: a loop running on its own. */
  at: number | null;
  rate: number;
  /** Turned towards seat `to` by `k` (0..1) of the throw's turn. */
  aim: { to: number; k: number } | null;
}
/** Where the bomb is; seats by index. `ms`: since it fell (loose) or went off (blast). */
export type BombAt =
  | { kind: "hand"; seat: number }
  | { kind: "flight"; from: number; to: number; k: number }
  | { kind: "loose"; seat: number; ms: number }
  | { kind: "blast"; seat: number; ms: number }
  | { kind: "none" };
export interface Frame {
  poses: Pose[];
  bomb: BombAt;
}

const idle = (aim: Pose["aim"] = null): Pose => ({ clip: CLIP.idle, at: null, rate: 1, aim });
const once = (clip: Clip, at: number, rate = 1, aim: Pose["aim"] = null): Pose => ({ clip, at, rate, aim });
const smooth = (x: number) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));

/** The thrower of `f` (`to` its target's seat): winds up, lets go at the lead-in's end, follows through, turns back. */
function thrower(f: Flight, to: number, now: number): Pose | null {
  const release = f.thrown_at + THROW_LEAD_MS, end = release + (THROW_END_S - THROW_RELEASE_S) * 1000;
  if (now < f.thrown_at) return null;
  if (now < end) return once(CLIP.throw, THROW_RELEASE_S + (now - release) / 1000, 1, { to, k: smooth((now - f.thrown_at) / THROW_LEAD_MS) });
  // turns back as the clip fades into the idle, the two turns cancelling
  const back = 1 - (now - end) / (FADE_S * 1000);
  return back > 0 ? idle({ to, k: back }) : null;
}

/**
 * The target of a bomb landing at `lands`: leaps for it while it is caught or not known yet; on a miss jumps, grabs at
 * nothing as it lands, falls, and picks it up again by the time they may throw.
 */
function target(lands: number, caught: boolean | null, now: number): Pose | null {
  const t = (now - lands) / 1000;
  if (caught !== false) {
    const at = CATCH_S + t;
    return at >= 0 && at < CATCH_END_S ? once(CLIP.catch, at) : null;
  }
  if (t < MISS_FOR_S) return GRAB_S + t >= 0 ? once(CLIP.miss, GRAB_S + t) : null;
  const at = PICK_FROM_S + (t - MISS_FOR_S) * PICK_RATE;
  return at < PICK_TO_S ? once(CLIP.pickup, at, PICK_RATE) : null;
}

/** How the bomb came to `h`'s holder, if the hold follows a throw: from the drop flag, or from the last throw seen. */
function landed(h: Hold, last: Flight | null): { lands: number; caught: boolean } | null {
  if (h.dropped) return { lands: h.ready_at - PICKUP_MS, caught: false };
  if (!last || last.to !== h.holder) return null;
  if (Math.abs(h.ready_at - last.lands_at) < 1) return { lands: last.lands_at, caught: true };
  if (Math.abs(h.ready_at - PICKUP_MS - last.lands_at) < 1) return { lands: last.lands_at, caught: false };
  return null;
}

/** `last`: the latest throw seen (its stage may be over); `boomAt`: when the TV first saw the blast, host ms. */
export function frameAt(scene: ZuendstoffScene, last: Flight | null, boomAt: number, now: number): Frame {
  const seat = (id: number) => scene.seats.findIndex((s) => s.player === id);
  const poses = scene.seats.map(() => idle());
  const set = (id: number, p: Pose | null) => {
    const i = seat(id);
    if (i >= 0 && p) poses[i] = p;
  };
  const st = scene.stage;
  switch (st.stage) {
    case "final":
      set(st.order[0], { ...idle(), clip: CLIP.win });
      return { poses, bomb: { kind: "none" } };
    case "boom":
      set(st.victim, once(CLIP.fall, Math.max(0, (now - boomAt) / 1000)));
      return { poses, bomb: { kind: "blast", seat: seat(st.victim), ms: now - boomAt } };
    case "flight": {
      const from = seat(st.from), to = seat(st.to), release = st.thrown_at + THROW_LEAD_MS;
      set(st.from, thrower(st, to, now));
      set(st.to, target(st.lands_at, st.caught, now));
      const bomb: BombAt =
        now < release ? { kind: "hand", seat: from }
        : now < st.lands_at ? { kind: "flight", from, to, k: (now - release) / (st.lands_at - release) }
        : st.caught ? { kind: "hand", seat: to }
        : { kind: "loose", seat: to, ms: now - st.lands_at };
      return { poses, bomb };
    }
    case "hold": {
      const how = landed(st, last), holder = seat(st.holder);
      if (last) set(last.from, thrower(last, seat(last.to), now));
      if (how) set(st.holder, target(how.lands, how.caught, now));
      const loose = how && !how.caught && now < how.lands + PICK_DELAY_MS;
      return { poses, bomb: loose ? { kind: "loose", seat: holder, ms: now - how.lands } : { kind: "hand", seat: holder } };
    }
  }
}

/** The stage's sounds: a knock for a catch, a thunk as a dropped bomb hits the felt, the blast, the winner's jingle. */
export function zuendstoffCues(scene: ZuendstoffScene, boomAt: number, now: number): Plan<BoardSound> | null {
  const st = scene.stage;
  // one key per landing, whether the flight or the drop that follows it says so first
  if (st.stage === "flight" && st.caught !== null) {
    return { key: `land ${st.lands_at}`, cues: [st.caught ? { sound: "knock", at: st.lands_at } : { sound: "thunk", at: st.lands_at + FALL_MS }] };
  }
  if (st.stage === "hold" && st.dropped) {
    const lands = st.ready_at - PICKUP_MS;
    return { key: `land ${lands}`, cues: [{ sound: "thunk", at: lands + FALL_MS }] };
  }
  if (st.stage === "boom") return { key: `boom ${scene.round}`, cues: [{ sound: "thunk", at: boomAt }, { sound: "scream", at: boomAt + 120 }] };
  if (st.stage === "final") return { key: "final", cues: [{ sound: "winnerJingle", at: now }] };
  return null;
}
