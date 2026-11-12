// The city's blocks: the areas the streets enclose, found from the street net itself.

export type Pt = { x: number; y: number };

/** Signed area: its sign tells which way round the polygon runs. */
export function area(poly: Pt[]) {
  return poly.reduce((sum, a, i) => sum + a.x * poly[(i + 1) % poly.length].y - poly[(i + 1) % poly.length].x * a.y, 0) / 2;
}

export function centroid(poly: Pt[]): Pt {
  return { x: poly.reduce((s, p) => s + p.x, 0) / poly.length, y: poly.reduce((s, p) => s + p.y, 0) / poly.length };
}

export function inside(pt: Pt, poly: Pt[]) {
  let hit = false;
  for (let i = 0; i < poly.length; i++) {
    const [a, b] = [poly[i], poly[(i + 1) % poly.length]];
    if (a.y > pt.y !== b.y > pt.y && pt.x < a.x + ((b.x - a.x) * (pt.y - a.y)) / (b.y - a.y)) hit = !hit;
  }
  return hit;
}

/**
 * Every block of a street net that does not cross itself: walk each street in
 * both directions, always taking the next street round the corner. The one
 * walk that runs the other way round is the outside of the city, not a block.
 */
export function streetBlocks(spots: Pt[], streets: { a: number; b: number }[]): Pt[][] {
  const at = (field: number) => spots[field - 1];
  const round = new Map<number, number[]>();
  for (const { a, b } of streets) {
    round.set(a, [...(round.get(a) ?? []), b]);
    round.set(b, [...(round.get(b) ?? []), a]);
  }
  for (const [v, list] of round) list.sort((p, q) => Math.atan2(at(p).y - at(v).y, at(p).x - at(v).x) - Math.atan2(at(q).y - at(v).y, at(q).x - at(v).x));
  const walked = new Set<string>();
  const walks: Pt[][] = [];
  for (const [u, list] of round) {
    for (const v of list) {
      const walk: Pt[] = [];
      let [a, b] = [u, v];
      while (!walked.has(`${a}>${b}`)) {
        walked.add(`${a}>${b}`);
        walk.push(at(a));
        const corner = round.get(b)!;
        [a, b] = [b, corner[(corner.indexOf(a) - 1 + corner.length) % corner.length]];
      }
      if (walk.length >= 3) walks.push(walk);
    }
  }
  const way = walks.filter((w) => area(w) > 0).length * 2 > walks.length ? 1 : -1;
  return walks.filter((w) => area(w) * way > 0);
}

/** The polygon moved inward by `d` on every side, or null when too little of it is left. */
export function inset(poly: Pt[], d: number, least = 220): Pt[] | null {
  const normal = (a: Pt, b: Pt, sign: number): Pt => {
    const len = Math.hypot(b.x - a.x, b.y - a.y) || 1;
    return { x: (-(b.y - a.y) / len) * sign, y: ((b.x - a.x) / len) * sign };
  };
  const first = normal(poly[0], poly[1], 1);
  const probe = { x: (poly[0].x + poly[1].x) / 2 + first.x * 1.5, y: (poly[0].y + poly[1].y) / 2 + first.y * 1.5 };
  const sign = inside(probe, poly) ? 1 : -1;
  const moved = poly.map((v, i) => {
    const n1 = normal(poly[(i - 1 + poly.length) % poly.length], v, sign);
    const n2 = normal(v, poly[(i + 1) % poly.length], sign);
    const den = 1 + n1.x * n2.x + n1.y * n2.y;
    // a sharp corner's mitre would shoot far out: cap it
    let [ox, oy] = den > 0.25 ? [((n1.x + n2.x) / den) * d, ((n1.y + n2.y) / den) * d] : [n1.x * d, n1.y * d];
    const len = Math.hypot(ox, oy);
    if (len > 2.6 * d) [ox, oy] = [(ox / len) * 2.6 * d, (oy / len) * 2.6 * d];
    return { x: v.x + ox, y: v.y + oy };
  });
  // moved too far, a side comes out the other way round: nothing of the block is left
  const turned = poly.some((v, i) => {
    const j = (i + 1) % poly.length;
    return (moved[j].x - moved[i].x) * (poly[j].x - v.x) + (moved[j].y - moved[i].y) * (poly[j].y - v.y) <= 0;
  });
  return !turned && Math.abs(area(moved)) > least ? moved : null;
}

export const polygonPath = (poly: Pt[]) => `M${poly.map((p) => `${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join("L")}Z`;
