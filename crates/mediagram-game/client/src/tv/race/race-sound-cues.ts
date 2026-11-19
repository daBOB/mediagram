// Which sounds a stage of the race makes and when, in host time. Worked out from
// the scene alone, like the motion, so the die clatters as the die lands and a
// knock falls on every hop however late the TV joined.

import type { RaceScene } from "../../protocol/RaceScene";
import { clatterAt, type BoardSound } from "../table/board-sounds";
import type { Cue, Plan } from "../table/tv-sound";
import { TRACK, hopCells } from "./race-geometry";
import { CAPTURE_FLY_MS } from "./race-motion";

/**
 * The cues of the scene's stage, with a key naming that stage so its cues are
 * scheduled once however many frames show it. `null`: the stage makes no sound.
 * `now` times the winner's jingle, the one stage without a host timestamp.
 */
export function stageCues(scene: RaceScene, now: number): Plan<BoardSound> | null {
  const st = scene.stage;
  switch (st.stage) {
    case "roll":
      return null;
    case "rolled": {
      return { key: `rolled ${st.rolled_at}`, cues: [{ sound: "die", at: clatterAt(st.rolled_at, st.lands_at) }] };
    }
    case "moving": {
      const seat = scene.seats.find((s) => s.player === st.player);
      if (!seat) return null;
      const { mv } = st;
      const hops = hopCells(seat.corner, mv.piece, mv.from, mv.to).length - 1;
      const end = st.started_at + hops * st.hop_ms;
      const cues: Cue<BoardSound>[] = [];
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
