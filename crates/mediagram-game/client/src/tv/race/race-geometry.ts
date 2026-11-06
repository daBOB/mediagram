// Where things sit on the race board: an 11×11 grid of cells, 40 track
// fields round the cross, four goal lanes and four home yards. Corner c
// starts on track field c·10, like the host's rules (race_rules.rs).

export type Cell = readonly [number, number]; // grid units; ×CELL for world units

export const CELL = 1.2;
export const BOARD = 14.4; // board face side, world units
export const BOARD_Y = 0.06;
export const TABLE_Y = -0.44;
export const TRACK = 40;

export const PATH: Cell[] = [];
const run = (x0: number, z0: number, dx: number, dz: number, n: number) => {
  for (let i = 0; i < n; i++) PATH.push([x0 + dx * i, z0 + dz * i]);
};
run(-5, -1, 1, 0, 5); run(-1, -2, 0, -1, 4); run(0, -5, 0, 0, 1);
run(1, -5, 0, 1, 5); run(2, -1, 1, 0, 4); run(5, 0, 0, 0, 1);
run(5, 1, -1, 0, 5); run(1, 2, 0, 1, 4); run(0, 5, 0, 0, 1);
run(-1, 5, 0, -1, 5); run(-2, 1, -1, 0, 4); run(-5, 0, 0, 0, 1);

/** Goal lane per corner, entrance first. */
export const GOALS: Cell[][] = [
  [[-4, 0], [-3, 0], [-2, 0], [-1, 0]],
  [[0, -4], [0, -3], [0, -2], [0, -1]],
  [[4, 0], [3, 0], [2, 0], [1, 0]],
  [[0, 4], [0, 3], [0, 2], [0, 1]],
];

/** Home yard per corner: where waiting pieces stand. */
export const YARDS: Cell[][] = [
  [[-4.3, -4.3], [-3.3, -4.3], [-4.3, -3.3], [-3.3, -3.3]],
  [[3.3, -4.3], [4.3, -4.3], [3.3, -3.3], [4.3, -3.3]],
  [[3.3, 3.3], [4.3, 3.3], [3.3, 4.3], [4.3, 4.3]],
  [[-4.3, 3.3], [-3.3, 3.3], [-4.3, 4.3], [-3.3, 4.3]],
];

/** Screen side of each corner: top-left, top-right, bottom-right, bottom-left. */
export const CORNER_LEFT = [true, false, false, true];
export const CORNER_TOP = [true, true, false, false];

/** The cell of a piece: `null` waits in the yard, 0..39 on the track, 40..43 in the goal. */
export function cellOf(corner: number, progress: number | null, piece: number): Cell {
  if (progress === null) return YARDS[corner][piece];
  if (progress < TRACK) return PATH[(corner * 10 + progress) % TRACK];
  return GOALS[corner][progress - TRACK];
}

/** Every cell a move passes through, starting where it leaves from. */
export function hopCells(corner: number, piece: number, from: number | null, to: number): Cell[] {
  const cells = [cellOf(corner, from, piece)];
  for (let p = from === null ? to : from + 1; p <= to; p++) cells.push(cellOf(corner, p, piece));
  return cells;
}

export const world = ([gx, gz]: Cell): [number, number] => [gx * CELL, gz * CELL];
