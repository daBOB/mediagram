// Where things sit on the card table. World units; the table is the XZ plane,
// the camera looks down from the south (+z). Seats stand round an ellipse, the
// first one at the bottom, the rest clockwise.

export const CARD_W = 1.25;
export const CARD_H = 1.75;
export const TABLE_Y = 0;
/** Cards lie this high above one another so overlapping ones never fight. */
export const LAYER = 0.012;

export const DECK_AT = { x: -1.6, z: 0.1 } as const;
export const DISCARD_AT = { x: 1.6, z: 0.1 } as const;
/** Where the wished suit's badge stands, beside the discards. */
export const WISH_AT = { x: 4.0, z: 0.1 } as const;

const RZ = 4.3, FAN_STEP = 0.62;
/** A crowded table sits closer in and fans its hands shorter, so neighbours keep clear of each other. */
const radiusX = (seats: number) => (seats <= 5 ? 8.4 : 7.3);
const fanLength = (seats: number) => (seats <= 4 ? 5 : seats <= 6 ? 3.8 : 2.8);

export interface Spot {
  x: number;
  z: number;
  /** Unit vector from the seat towards the middle. */
  dx: number;
  dz: number;
  /** Where the seat's tag stands in the world, outside the fan. */
  tagX: number;
  tagZ: number;
  /** Longest the fan of this seat may be. */
  fan: number;
  /** Cards shown face up lie inside the fan, towards the middle. */
  openX: number;
  openZ: number;
}

export function seatSpot(index: number, count: number): Spot {
  const a = Math.PI / 2 + (index * 2 * Math.PI) / count;
  const x = radiusX(count) * Math.cos(a), z = RZ * Math.sin(a), len = Math.hypot(x, z) || 1;
  const dx = -x / len, dz = -z / len;
  return { x, z, dx, dz, fan: fanLength(count), tagX: x - dx * 2.5, tagZ: z - dz * 1.9, openX: x + dx * 1.9, openZ: z + dz * 1.9 };
}

/** Yaw that turns a card's top edge (-z) to face `(dx, dz)`. */
export const yawFacing = (dx: number, dz: number) => Math.atan2(-dx, -dz);

export interface Placed {
  x: number;
  z: number;
  yaw: number;
  y: number;
}

/** The `i`-th of `k` cards in a hand fanned at a seat. */
export function fanCard(s: Spot, i: number, k: number): Placed {
  const step = k > 1 ? Math.min(FAN_STEP, s.fan / (k - 1)) : 0, off = (i - (k - 1) / 2) * step;
  // the sideways direction of the fan is the facing rotated a quarter turn
  const tx = -s.dz, tz = s.dx;
  return { x: s.x + tx * off, z: s.z + tz * off, yaw: yawFacing(s.dx, s.dz) - (i - (k - 1) / 2) * 0.04, y: TABLE_Y + 0.02 + i * LAYER };
}

/** The `i`-th of `k` face-up cards laid out beside the fan. */
export function openCard(s: Spot, i: number, k: number): Placed {
  const off = (i - (k - 1) / 2) * 1.45, tx = -s.dz, tz = s.dx;
  return { x: s.openX + tx * off, z: s.openZ + tz * off, yaw: yawFacing(s.dx, s.dz), y: TABLE_Y + 0.02 + i * LAYER };
}

/** A discard lies a little crooked, the same way every time for the same card. */
export function pileCard(base: { x: number; z: number }, seed: number, layer: number): Placed {
  const r = Math.sin(seed * 12.9898) * 43758.5453, u = r - Math.floor(r);
  return { x: base.x + (u - 0.5) * 0.35, z: base.z + (((u * 7) % 1) - 0.5) * 0.3, yaw: (u - 0.5) * 0.7, y: TABLE_Y + 0.02 + layer * LAYER };
}
