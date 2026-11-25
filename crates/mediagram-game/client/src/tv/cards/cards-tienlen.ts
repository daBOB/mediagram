// Where Tiến Lên puts the play to beat: its cards lie side by side across the
// middle, lowest on the left. Up to seven lie clear of each other; more overlap,
// each keeping its corner index free, so the longest play, a run of all
// thirteen ranks, still fits between the seats.

import { CARD_W, LAYER, TABLE_Y, type Placed } from "./cards-layout";

export const isTienLen = (game: string) => game === "Tiến Lên";

/** The widest a play may lie, and the step between cards that lie clear. */
export const ROW_W = 9.4;
const STEP = 1.45, ROW_Z = 0.1;

/** Card `i` of the `n` that make the play. */
export function rowCard(i: number, n: number): Placed {
  const step = n > 1 ? Math.min(STEP, (ROW_W - CARD_W) / (n - 1)) : 0;
  return { x: (i - (n - 1) / 2) * step, z: ROW_Z, yaw: 0, y: TABLE_Y + 0.02 + i * LAYER };
}
