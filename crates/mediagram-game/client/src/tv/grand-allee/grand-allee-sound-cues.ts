// Which sounds a stage of a Grand Allee turn makes and when, in host time, from the scene alone (like the motion), with the race's
// sounds: the dice clatter as they land, a knock on every hop, a jingle when the Bank pays and when a guest's coins arrive, a thud
// when a property is sold and, with a scream, when a player goes broke, the winner's jingle at the end.

import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { clatterAt, type Cue } from "../race/race-sound-cues";
import { bankAt, hops } from "./grand-allee-clock";

/** The cues of the turn's stage, keyed so they are scheduled once however many frames show it; null: the stage is silent. */
export function grandAlleeCues(scene: GrandAlleeScene, now: number): { key: string; cues: Cue[] } | null {
  const t = scene.turn;
  switch (t.stage) {
    case "rolled":
    case "permit":
      return { key: `${t.stage} ${t.rolled_at}`, cues: [{ sound: "die", at: clatterAt(t.rolled_at, t.lands_at) }] };
    case "guest":
      return { key: `guest ${t.rolled_at}`, cues: [{ sound: "die", at: clatterAt(t.rolled_at, t.lands_at) }, { sound: "goalJingle", at: t.paid_at }] };
    case "walking": {
      const n = hops(t.from, t.to, scene.board.fields.length), bank = bankAt(scene);
      const cues: Cue[] = Array.from({ length: n }, (_, i) => ({ sound: "knock", at: t.started_at + (i + 1) * t.hop_ms }));
      if (bank !== null) cues.push({ sound: "goalJingle", at: bank });
      return { key: `walking ${t.started_at}`, cues };
    }
    case "sold":
      return { key: `sold ${t.started_at}`, cues: [{ sound: "thunk", at: t.started_at }] };
    case "note": {
      const broke = scene.seats.some((s) => s.player === t.player && s.out);
      return broke ? { key: `note ${t.started_at}`, cues: [{ sound: "thunk", at: t.started_at }, { sound: "scream", at: t.started_at }] } : null;
    }
    case "final":
      return { key: `final ${t.winner}`, cues: [{ sound: "winnerJingle", at: now }] };
    default:
      return null;
  }
}
