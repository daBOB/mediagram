import type { FluechtigSpot } from "../../protocol/FluechtigSpot";

/** A smooth curve through `points`, as an SVG path. */
export function smoothPath(points: FluechtigSpot[]) {
  let d = `M${points[0].x} ${points[0].y}`;
  for (let i = 1; i < points.length; i++) {
    const [a, b] = [points[i - 1], points[i]];
    const before = points[Math.max(i - 2, 0)];
    const after = points[Math.min(i + 1, points.length - 1)];
    const c1 = `${(a.x + (b.x - before.x) / 6).toFixed(1)} ${(a.y + (b.y - before.y) / 6).toFixed(1)}`;
    const c2 = `${(b.x - (after.x - a.x) / 6).toFixed(1)} ${(b.y - (after.y - a.y) / 6).toFixed(1)}`;
    d += `C${c1},${c2},${b.x} ${b.y}`;
  }
  return d;
}

/** The middle of the river at `x`; `river` runs west to east. */
export function riverY(river: FluechtigSpot[], x: number) {
  if (x <= river[0].x) return river[0].y;
  for (let i = 1; i < river.length; i++) {
    const [a, b] = [river[i - 1], river[i]];
    if (x <= b.x) return a.y + ((b.y - a.y) * (x - a.x)) / (b.x - a.x || 1);
  }
  return river[river.length - 1].y;
}

/** The ferry's lane between two piers: down the middle of the river, from one pier's jetty to the other's. */
export function ferryLane(river: FluechtigSpot[], a: FluechtigSpot, b: FluechtigSpot) {
  const steps = Math.max(1, Math.ceil(Math.abs(b.x - a.x) / 12));
  return Array.from({ length: steps + 1 }, (_, i) => {
    const x = a.x + ((b.x - a.x) * i) / steps;
    return `${i ? "L" : "M"}${x.toFixed(1)} ${riverY(river, x).toFixed(1)}`;
  }).join("");
}
