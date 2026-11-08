// The Gipfelsturm trail: a switchback path up the mountain, in the units of
// the mountain drawing's viewBox (1600 × 900, y grows downward). Pure, so
// where a climber stands can be tested without a screen.

export type Point = { x: number; y: number };

export const VIEW = { width: 1600, height: 900 };
export const SUMMIT: Point = { x: 800, y: 120 };

/** Corners of the path from the foot to the summit; every corner is higher than the last. */
export const TRAIL: Point[] = [
  { x: 800, y: 860 },
  { x: 560, y: 800 },
  { x: 1040, y: 730 },
  { x: 640, y: 650 },
  { x: 980, y: 580 },
  { x: 700, y: 500 },
  { x: 900, y: 430 },
  { x: 745, y: 350 },
  { x: 860, y: 280 },
  { x: 775, y: 200 },
  SUMMIT,
];

/**
 * The point `share` of the way up (0 = foot, 1 = summit). Every leg of the
 * trail counts the same, so with one step per leg each step stands on a corner.
 */
export function pointAt(share: number): Point {
  const along = Math.min(1, Math.max(0, share)) * (TRAIL.length - 1);
  const leg = Math.min(Math.floor(along), TRAIL.length - 2);
  const t = along - leg;
  const [a, b] = [TRAIL[leg], TRAIL[leg + 1]];
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
}

/** Room each climber takes beside others on the same step. */
const SPREAD = 76;

/** Where each climber stands: on their step, side by side with anyone sharing it. */
export function spots(climbers: { player: number; step: number }[], steps: number): Map<number, Point> {
  const byStep = new Map<number, number[]>();
  for (const c of climbers) byStep.set(c.step, [...(byStep.get(c.step) ?? []), c.player]);
  const at = new Map<number, Point>();
  for (const [step, players] of byStep) {
    const spot = pointAt(steps > 0 ? step / steps : 0);
    players.forEach((p, i) => at.set(p, { x: spot.x + (i - (players.length - 1) / 2) * SPREAD, y: spot.y }));
  }
  return at;
}
