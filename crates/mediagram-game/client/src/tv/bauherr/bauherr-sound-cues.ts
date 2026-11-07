// Which sounds a stage of a Bauherr turn makes and when, in host time, worked out from the scene alone (like the motion):
// the die clatters as it lands, a knock falls on every hop, the bank pays with a jingle, the winner with another.

import type { BauherrTurn } from "../../protocol/BauherrTurn";
import { clatterAt, type Cue } from "../race/race-sound-cues";

/** The cues of the turn's stage, keyed so they are scheduled once however many frames show it; null: the stage is silent. */
export function bauherrCues(turn: BauherrTurn, now: number): { key: string; cues: Cue[] } | null {
  switch (turn.stage) {
    case "rolled":
    case "guest":
    case "permit":
      return { key: `${turn.stage} ${turn.rolled_at}`, cues: [{ sound: "die", at: clatterAt(turn.rolled_at, turn.lands_at) }] };
    case "walking": {
      const cues: Cue[] = Array.from({ length: turn.value }, (_, i) => ({ sound: "knock", at: turn.started_at + (i + 1) * turn.hop_ms }));
      if (turn.bonus) cues.push({ sound: "goalJingle", at: turn.started_at + turn.value * turn.hop_ms });
      return { key: `walking ${turn.started_at}`, cues };
    }
    case "final":
      return { key: `final ${turn.winner}`, cues: [{ sound: "winnerJingle", at: now }] };
    default:
      return null;
  }
}
