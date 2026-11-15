// The wooden board games' sounds, the race's and Grand Allee's: a die's clatter, a piece's knock on every hop, a thunk and a
// scream when a piece is taken or a player falls, a jingle for a goal or a payment, and the winner's.

import type { Effects } from "./tv-sound";

export type BoardSound = "die" | "knock" | "thunk" | "scream" | "goalJingle" | "winnerJingle";

export const BOARD_SOUNDS: Effects<BoardSound> = {
  die: { files: ["die-clatter"], gain: 0.9 },
  knock: { files: [1, 2, 3, 4, 5].map((i) => `pawn-knock-${i}`), gain: 0.55, vary: true },
  thunk: { files: ["capture-thunk"], gain: 0.8 },
  scream: { files: ["capture-scream"], gain: 0.7 },
  goalJingle: { files: ["goal-jingle"], gain: 0.6 },
  winnerJingle: { files: ["winner-jingle"], gain: 0.8 },
};

/** The die first touches the table 60 % into its flight (the bounce in race-motion). */
const DIE_TOUCH = 0.6;
/** The clatter's first hit sits this far into its file. */
const CLATTER_ATTACK_MS = 70;
/** When the clatter of a die thrown from `rolledAt` to `landsAt` starts, so its first hit meets the table. */
export const clatterAt = (rolledAt: number, landsAt: number) => rolledAt + (landsAt - rolledAt) * DIE_TOUCH - CLATTER_ATTACK_MS;
