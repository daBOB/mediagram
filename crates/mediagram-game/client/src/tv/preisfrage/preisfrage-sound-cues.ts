// Which sound Preisfrage makes when, worked out from the scene alone, in
// Kopfnuss's game-show voices and with its once-per-key bookkeeping: a chime as
// the article appears, a beep as the keypads open, a click for each guess, the
// last three seconds ticking, a bell with the price. The final ranking has the
// ceremony's jingle.

import type { PreisfrageScene } from "../../protocol/PreisfrageScene";
import { cuesDue, type Cue, type SoundState } from "../kopfnuss/kopfnuss-sound-cues";

function cues(scene: PreisfrageScene, since: number): Cue[] {
  const st = scene.stage;
  const r = scene.round;
  switch (st.stage) {
    case "guessing":
      return [
        { key: `article ${r}`, sound: "round", at: since },
        { key: `go ${r}`, sound: "go", at: st.opens_at },
        ...st.guessed.map((p): Cue => ({ key: `guess ${r} ${p}`, sound: "pickDone", onlyChanges: true })),
        ...[3, 2, 1].map((s): Cue => ({ key: `tick ${r} ${s}`, sound: "tick", at: st.closes_at - s * 1000 })),
      ];
    case "reveal":
      return [{ key: `reveal ${r}`, sound: "reveal", at: since }];
    case "final":
      return [];
  }
}

/** The cues to start now (with their host times), each once; `state` remembers them. */
export const preisfrageSoundsDue = (state: SoundState, scene: PreisfrageScene, now: number, lookaheadMs?: number) =>
  cuesDue(state, `${scene.stage.stage} ${scene.round}`, (since) => cues(scene, since), now, lookaheadMs);
