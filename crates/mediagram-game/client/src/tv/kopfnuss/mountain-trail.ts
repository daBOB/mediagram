// The Gipfelsturm pyramid in the drawing's viewBox units (1600 × 900, y grows
// downward). Pure, so where a climber stands can be tested without a screen.

export type Point = { x: number; y: number };

export const VIEW = { width: 1600, height: 900 };
export const SUMMIT: Point = { x: 800, y: 220 };

/** The foot and ten tier tops from bottom to summit. */
export const TRAIL: Point[] = [
  { x: 800, y: 820 },
  { x: 570, y: 760 },
  { x: 1030, y: 700 },
  { x: 540, y: 640 },
  { x: 1060, y: 580 },
  { x: 600, y: 520 },
  { x: 1000, y: 460 },
  { x: 660, y: 400 },
  { x: 940, y: 340 },
  { x: 740, y: 280 },
  SUMMIT,
];

/** Every leg counts the same, so one answer moves a climber exactly one tier. */
export function pointAt(share: number): Point {
  const along = Math.min(1, Math.max(0, share)) * (TRAIL.length - 1);
  const leg = Math.min(Math.floor(along), TRAIL.length - 2);
  const t = along - leg;
  const [a, b] = [TRAIL[leg], TRAIL[leg + 1]];
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
}

const SPREAD = 86;

/** Where each climber stands: on their tier, side by side with anyone sharing it. */
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
