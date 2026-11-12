// Which amazon.de searches one run makes: a term, a results page, the order and a price band each.

/** Amazon's price filter `p_36` takes euro cents: 5-20, 20-50, 50-150 and 150-500 €. */
export const BANDS = [
  [500, 2000],
  [2000, 5000],
  [5000, 15000],
  [15000, 50000],
] as const;

export type Band = (typeof BANDS)[number];
export type Search = { term: string; page: number; newest: boolean; band: Band };

/**
 * `count` searches, the bands taken in turn so each gets its share. Terms are drawn without
 * repeats until all are used; the page (1-4) and newest-first (40 %) are random, so a run
 * finds articles a bestseller list never shows.
 */
export function planSearches(terms: string[], count: number, random = Math.random): Search[] {
  let pool: string[] = [];
  return Array.from({ length: count }, (_, i) => {
    if (pool.length === 0) pool = shuffled(terms, random);
    return {
      term: pool.pop()!,
      page: 1 + Math.floor(random() * 4),
      newest: random() < 0.4,
      band: BANDS[i % BANDS.length],
    };
  });
}

export function searchUrl({ term, page, newest, band }: Search): string {
  const order = newest ? "&s=date-desc-rank" : "";
  return `https://www.amazon.de/s?k=${encodeURIComponent(term)}&page=${page}${order}&rh=p_36%3A${band[0]}-${band[1]}`;
}

function shuffled<T>(list: T[], random: () => number): T[] {
  const out = [...list];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}
