// Where things stand on the Bauherr board, in world units (1 unit is about 13 m; x right, z towards the
// viewer). The rules only know field indices and each hotel's entrance fields (bauherr_board.rs); this file
// is the one place that turns them into places: a winding road of 36 fields, a lot beside each hotel's
// stretch of road, and the plaza where the dice roll. The bake tool and the TV both read it.

export const FIELD_COUNT = 36;
/** Hotel names in plot order, as in the rules. */
export const HOTEL_NAMES = ["Kastanienhof", "Seeblick", "Waldhaus", "Stadtpalais", "Alpenrose", "Sternwarte", "Kurhaus", "Leuchtturm"] as const;
/** Entrance fields per hotel, as in the rules (a hotel's fields border its lot). */
export const HOTEL_FIELDS: readonly (readonly number[])[] = [
  [1, 2, 35, 34], [3, 4, 5, 6], [7, 8, 10, 11], [12, 13, 14, 15], [16, 17, 19, 20], [21, 22, 23, 24], [25, 26, 28, 29], [30, 31, 32, 33],
];
/** Field kinds by index, as in the rules: B(ank), R(athaus), P bauplatz, S baustelle, F free entrance, . plain. */
export const FIELD_KINDS = "BP.P.S..SF.PP.S..SR.PP.S..SF.PP.S..S";

export interface FieldPlace {
  x: number;
  z: number;
  /** Direction of the walk, radians from +x towards +z. */
  ang: number;
}
export interface Lot {
  plot: number;
  x: number;
  z: number;
  /** Turn about the vertical (three's rotation.y); the hotel's front looks along (sin, cos). */
  rot: number;
  w: number;
  d: number;
}

export const ROAD_WIDTH = 1.5;
export const FIELD_SIZE = 1.05;
export const LOT_W = 6.3;
export const LOT_D = 5.6;
/** The road's course, clockwise seen from above: a west cap, the back run, an east cap and the front run. */
const COURSE: [number, number][] = [
  [-13.6, 3.0], [-16.0, 0.0], [-14.6, -3.8], [-11, -5.8], [-6, -5.0], [-1, -6.2], [4, -5.4], [9, -5.9],
  [13.2, -4.6], [15.8, -0.6], [14, 3.4], [10, 4.6], [5, 3.8], [0, 4.6], [-5, 3.7], [-9.5, 4.4],
];
const SAMPLES = 2880;
/** A closed Catmull-Rom spline through the course, sampled evenly in its parameter. */
const loop: [number, number][] = Array.from({ length: SAMPLES + 1 }, (_, i) => {
  const n = COURSE.length, w = (i / SAMPLES) * n, k = Math.floor(w) % n, f = w - Math.floor(w);
  const [p0, p1, p2, p3] = [-1, 0, 1, 2].map((d) => COURSE[(k + d + n) % n]);
  const cr = (a: number, b: number, c: number, d: number) =>
    0.5 * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f * f * f);
  return [cr(p0[0], p1[0], p2[0], p3[0]), cr(p0[1], p1[1], p2[1], p3[1])];
});
const arc = [0];
for (let i = 1; i <= SAMPLES; i++) arc.push(arc[i - 1] + Math.hypot(loop[i][0] - loop[i - 1][0], loop[i][1] - loop[i - 1][1]));
export const ROAD_LENGTH = arc[SAMPLES];
/** Field 6.5 is the west end of the road; the fields run clockwise seen from above. */
const FIELD_ZERO = 0.0;

/** The road at `u` fields from field 0 (fractions and wraps allowed): position and walking direction. */
export function roadPoint(u: number): FieldPlace {
  const s = ((((u - FIELD_ZERO) / FIELD_COUNT) % 1) + 1) % 1 * ROAD_LENGTH;
  let lo = 0, hi = SAMPLES;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (arc[mid] <= s) lo = mid;
    else hi = mid;
  }
  const f = (s - arc[lo]) / (arc[hi] - arc[lo] || 1);
  const a = loop[lo], b = loop[hi];
  return { x: a[0] + (b[0] - a[0]) * f, z: a[1] + (b[1] - a[1]) * f, ang: Math.atan2(b[1] - a[1], b[0] - a[0]) };
}

/** Field `i` stands `FIELD_SIZE`-sized in the road; walking order is the index order. */
export const FIELDS: FieldPlace[] = Array.from({ length: FIELD_COUNT }, (_, i) => roadPoint(i));
/** Fields spaced along the road, so the walk's pace is even. */
export const FIELD_SPACING = ROAD_LENGTH / FIELD_COUNT;

/** The road's middle line, for painting: points at every `step` fields. */
export const roadLine = (step = 0.25): FieldPlace[] => Array.from({ length: Math.round(FIELD_COUNT / step) }, (_, i) => roadPoint(i * step));

/**
 * Where each hotel's lot stands, by plot id. The back row (outside the loop, behind the road seen from the viewer)
 * is Seeblick, Waldhaus, Stadtpalais, Alpenrose; the front row (inside the loop) is Kastanienhof, Leuchtturm,
 * Kurhaus, Sternwarte. Each lot lies beside the stretch of road its entrance fields are on, turned a little to face it.
 */
const LOT_AT: [number, number, number][] = [
  [-10.3, -0.8, -0.07], [-14.8, -9.8, -0.65], [-5.0, -9.5, 0.16], [4.8, -9.8, 0.05],
  [14.7, -9.7, 0.65], [10.2, -0.2, 0.07], [3.4, -0.1, 0], [-3.5, -0.2, -0.05],
];
export const LOTS: Lot[] = LOT_AT.map(([x, z, rot], plot) => ({ plot, x, z, rot, w: LOT_W, d: LOT_D }));

/** Lot-local point (x across the front, z towards the road) in the world. */
export function lotToWorld(lot: Lot, lx: number, lz: number): [number, number] {
  const c = Math.cos(lot.rot), s = Math.sin(lot.rot);
  return [lot.x + lx * c + lz * s, lot.z - lx * s + lz * c];
}

/** The paved pad in front of the road, where the dice roll: just big enough for two large dice. */
export const PLAZA = { x0: -4.4, x1: 4.4, z0: 5.65, z1: 8.0 };
export const DICE_AT = { number: [-1.45, 6.6], permit: [1.45, 6.6] } as const;
/** The overview: the whole road with its caps across the frame, the back row of lots near the top. */
/** Where the evening sun stands (towards it): low, from the left and a little from the viewer. The bake and the TV light by it. */
export const SUN_DIR = [-0.87, 0.28, 0.42] as const;
export const CAMERA = { pos: [0, 16.8, 23.6], at: [0, 0, -1.8], fov: 36 } as const;
/** The rows of the screen (fractions from the top) the HUD keeps: the turn banner above `top`, the player tags below `bottom`. */
export const HUD_BAND = { top: 0.105, bottom: 0.883 };
/** The die's result pill (fractions of the screen): low in the right corner, on the lawn below the road, where no pawn walks. */
export const RESULT_PILL = { left: 0.7, top: 0.79, right: 0.978, bottom: 0.886 };
/** The ground print covers this rectangle. */
export const GROUND = { x0: -27, x1: 27, z0: -23, z1: 15 };

