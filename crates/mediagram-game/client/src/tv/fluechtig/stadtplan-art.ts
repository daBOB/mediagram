// The Stadtplan's decoration, worked out from the city itself: blocks between
// the streets, parks, courtyards, landmark signs and district names. The same
// city always gets the same plan.
import type { FluechtigBoard } from "../../protocol/FluechtigBoard";
import { area, centroid, inset, inside, polygonPath, streetBlocks, type Pt } from "./city-blocks";
import { riverY } from "./fluechtig-river";

const DISTRICTS = ["NORDEND", "ALTSTADT", "DOMVIERTEL", "VILLENBERG", "HAFEN", "MÜHLENVIERTEL", "SÜDSTADT", "MESSE"];
/** Landmark signs, drawn as outlines about 20 wide: church, works, hall, mast, castle, wheel, arena. */
const SIGNS = [
  "M-6 9V-1L0 -8L6 -1V9Z M0 -8V-14 M-3 -11.5H3",
  "M-9 9V-2L-4 2V-2L1 2V-2L6 2V9Z M6 9V-10H9V9",
  "M-8 9V1H8V9Z M-8 1A8 7 0 0 1 8 1 M-3 9V4H3V9",
  "M0 9V-12 M-3 -9H3 M-5 9H5 M-2 -4H2",
  "M-9 9V-6H-6V-3H-3V-6H3V-3H6V-6H9V9Z",
  "M0 -9A9 9 0 1 0 0.01 -9Z M0 -9V9 M-9 0H9 M-6.4 -6.4L6.4 6.4 M-6.4 6.4L6.4 -6.4",
  "M-10 6A10 6 0 1 0 10 6A10 6 0 1 0 -10 6Z M-6 6A6 3 0 1 0 6 6A6 3 0 1 0 -6 6Z",
];

export type StadtplanArt = {
  /** The fields' radius; every other size on the plan follows it. */
  radius: number;
  /** City blocks in four tones, one path each, so neighbouring districts differ. */
  tones: string[];
  yards: string;
  parks: string;
  trees: (Pt & { r: number })[];
  signs: (Pt & { d: string })[];
  names: (Pt & { text: string })[];
  /** Where the river names its ferry, if the city has one. */
  ferryName: Pt | null;
};

/** A small seeded generator. */
function seeded(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), a | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Fields are drawn as large as the closest pair allows, within what a TV still reads. */
export function fieldRadius(spots: Pt[]) {
  let least = Infinity;
  for (let i = 0; i < spots.length; i++) {
    for (let j = i + 1; j < spots.length; j++) least = Math.min(least, Math.hypot(spots[i].x - spots[j].x, spots[i].y - spots[j].y));
  }
  return Math.min(14, Math.max(9, least * 0.31));
}

export function stadtplanArt(board: FluechtigBoard): StadtplanArt {
  const { spots, river, width, height } = board;
  const radius = fieldRadius(spots);
  const u = radius / 11.5;
  const rnd = seeded(spots.length * 7919 + board.links.length);
  const bank = (p: Pt) => p.y - riverY(river, p.x);
  const blocks = streetBlocks(spots, board.links.filter((l) => l.kind === "taxi"))
    .map((outline) => ({ outline, shape: inset(outline, 4.6 * u, 220 * u * u), mid: centroid(outline), size: Math.abs(area(outline)) }))
    .filter((b): b is typeof b & { shape: Pt[] } => b.shape !== null);
  const dry = blocks.filter((b) => Math.abs(bank(b.mid)) > 30 * u && b.outline.every((p) => Math.abs(bank(p)) > 26 * u)).sort((a, b) => b.size - a.size);

  // districts lie either side of the river, four to a bank
  const seeds = DISTRICTS.map((text, i) => {
    const x = (((i % 4) + 0.5) / 4) * width;
    return { text, x, y: riverY(river, x) + (i < 4 ? -0.3 : 0.3) * height };
  });
  const district = (p: Pt) => {
    const mine = seeds.map((s, i) => ({ i, far: bank(s) * bank(p) > 0 ? Math.hypot(s.x - p.x, s.y - p.y) : Infinity }));
    return mine.reduce((a, b) => (b.far < a.far ? b : a)).i;
  };

  const pool = dry.slice(0, Math.ceil(blocks.length * 0.27));
  const parks = new Set<(typeof blocks)[number]>();
  while (parks.size < Math.min(Math.round(blocks.length * 0.075), pool.length)) parks.add(pool[Math.floor(rnd() * pool.length)]);

  const tones = ["", "", "", ""];
  let yards = "";
  const trees: StadtplanArt["trees"] = [];
  for (const b of blocks) {
    if (!parks.has(b)) {
      tones[district(b.mid) % 4] += polygonPath(b.shape);
      const yard = Math.abs(area(b.shape)) > 1500 * u * u ? inset(b.shape, 9 * u, 220 * u * u) : null;
      if (yard) yards += polygonPath(yard);
      continue;
    }
    const xs = b.shape.map((p) => p.x);
    const ys = b.shape.map((p) => p.y);
    for (let n = Math.max(3, Math.floor(Math.abs(area(b.shape)) / (260 * u * u))), tries = n * 12; n > 0 && tries > 0; tries--) {
      const p = { x: Math.min(...xs) + rnd() * (Math.max(...xs) - Math.min(...xs)), y: Math.min(...ys) + rnd() * (Math.max(...ys) - Math.min(...ys)) };
      if (inside(p, b.shape) && b.shape.every((v) => Math.hypot(v.x - p.x, v.y - p.y) > 5 * u)) {
        trees.push({ ...p, r: (2.2 + rnd() * 1.4) * u });
        n--;
      }
    }
  }

  const clear = (p: Pt) => Math.min(...spots.map((s) => Math.hypot(s.x - p.x, s.y - p.y)));
  const signs = dry
    .filter((b) => !parks.has(b) && clear(b.mid) > 24 * u)
    .slice(0, Math.round(blocks.length * 0.08))
    .map((b, i) => ({ ...b.mid, d: SIGNS[i % SIGNS.length] }));

  // a name goes into the block of its district where the fields leave it the most room
  const names = seeds.flatMap((seed, i) => {
    const half = seed.text.length * 6.2 * u;
    const room = (c: Pt) => Math.min(...spots.map((s) => Math.max(Math.abs(s.x - c.x) - half, 0) + Math.abs(s.y - c.y) * 1.4));
    const home = dry.filter((b) => district(b.mid) === i).map((b) => b.mid);
    return home.length ? [{ ...home.reduce((a, b) => (room(b) > room(a) ? b : a)), text: seed.text }] : [];
  });

  return { radius, tones, yards, parks: [...parks].map((b) => polygonPath(b.shape)).join(""), trees, signs, names, ferryName: ferryName(board, u) };
}

/** The clearest stretch of the ferry's lane: away from fields and from every line crossing the river. */
function ferryName(board: FluechtigBoard, u: number): Pt | null {
  const { spots, river } = board;
  const at = (f: number) => spots[f - 1];
  const piers = board.links.filter((l) => l.kind === "schatten").flatMap((l) => [at(l.a).x, at(l.b).x]);
  if (!piers.length) return null;
  const crossings = board.links.flatMap((l) => {
    const [a, b] = [at(l.a), at(l.b)];
    const [da, db] = [a.y - riverY(river, a.x), b.y - riverY(river, b.x)];
    return l.kind !== "schatten" && da * db < 0 ? [a.x + (da / (da - db)) * (b.x - a.x)] : [];
  });
  let best: Pt | null = null;
  let room = -1;
  for (let x = Math.min(...piers) + 30 * u; x < Math.max(...piers) - 30 * u; x += 6) {
    const p = { x, y: riverY(river, x) };
    const free = Math.min(...spots.map((s) => Math.hypot(s.x - p.x, s.y - p.y)), ...crossings.map((c) => Math.abs(c - x)));
    if (free > room) [best, room] = [p, free];
  }
  return best;
}
