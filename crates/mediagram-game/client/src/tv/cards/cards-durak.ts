// Where Durak puts its cards: attack and defence lie as overlapping pairs in a
// grid right of the deck, and the trump card lies sideways under the deck so
// its corner sticks out.

import { DECK_AT, LAYER, TABLE_Y, yawFacing, type Placed } from "./cards-layout";

const COLS = [0.6, 2.5, 4.4];
const ROWS = [-1.1, 1.2];

export const isDurak = (game: string) => game === "Durak";

/** Card `i` (0 attack, 1 the card that beat it) of pair `pair`. */
export function pairCard(pair: number, i: number): Placed {
  return {
    x: COLS[pair % COLS.length] + (i ? 0.32 : 0),
    z: ROWS[Math.floor(pair / COLS.length) % ROWS.length] + (i ? 0.3 : 0),
    yaw: i ? 0.1 : -0.04,
    y: TABLE_Y + 0.02 + i * LAYER * 2,
  };
}

export const TRUMP_AT: Placed = { x: DECK_AT.x - 0.55, z: DECK_AT.z, yaw: yawFacing(-1, 0), y: TABLE_Y + 0.014 };
