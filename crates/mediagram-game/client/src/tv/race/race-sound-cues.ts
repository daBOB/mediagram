// Which sounds a stage of the race makes and when, in host time. Worked out from
// the scene alone, like the motion, so the die clatters as the die lands and a
// knock falls on every hop however late the TV joined.

import type { RaceScene } from "../../protocol/RaceScene";
import { TRACK, hopCells } from "./race-geometry";
import { CAPTURE_FLY_MS } from "./race-motion";

export type SoundName = "die" | "knock" | "thunk" | "scream" | "goalJingle" | "winnerJingle";

export interface Cue {
  sound: SoundName;
  /** Host time, ms. */
  at: number;
}

/** The die first touches the table 60 % into its flight (the bounce in race-motion). */
const DIE_TOUCH = 0.6;
/** The clatter's first hit sits this far into its file. */
const CLATTER_ATTACK_MS = 70;

/**
 * The cues of the scene's stage, with a key naming that stage so its cues are
 * scheduled once however many frames show it. `null`: the stage makes no sound.
 * `now` times the winner's jingle, the one stage without a host timestamp.
 */
export function stageCues(scene: RaceScene, now: number): { key: string; cues: Cue[] } | null {
  const st = scene.stage;
  switch (st.stage) {
    case "roll":
      return null;
    case "rolled": {
      const touch = st.rolled_at + (st.lands_at - st.rolled_at) * DIE_TOUCH;
      return { key: `rolled ${st.rolled_at}`, cues: [{ sound: "die", at: touch - CLATTER_ATTACK_MS }] };
    }
    case "moving": {
      const seat = scene.seats.find((s) => s.player === st.player);
      if (!seat) return null;
      const { mv } = st;
      const hops = hopCells(seat.corner, mv.piece, mv.from, mv.to).length - 1;
      const end = st.started_at + hops * st.hop_ms;
      const cues: Cue[] = [];
      for (let i = 1; i <= hops; i++) cues.push({ sound: "knock", at: st.started_at + i * st.hop_ms });
      if (mv.captures) {
        cues.push({ sound: "thunk", at: end }, { sound: "scream", at: end }, { sound: "knock", at: end + CAPTURE_FLY_MS });
      }
      if ((mv.from ?? -1) < TRACK && mv.to >= TRACK) cues.push({ sound: "goalJingle", at: end });
      return { key: `moving ${st.started_at}`, cues };
    }
    case "final":
      return { key: `final ${st.winner}`, cues: [{ sound: "winnerJingle", at: now }] };
  }
}
