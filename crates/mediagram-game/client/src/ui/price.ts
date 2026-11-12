// Preisfrage's numbers on screen: prices in euros, the phone's typing, the reveal's line.

/** `1219` → `12,19 €`. */
export const euro = (cents: number) =>
  (cents / 100).toLocaleString("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " €";

/**
 * The keypad's text after `key` (a digit, `,` or `⌫`): one comma, at most two digits after it, no leading
 * zero, and a key that would go above `maxCents` does nothing.
 */
export function typeKey(text: string, key: string, maxCents: number): string {
  if (key === "⌫") return text.slice(0, -1);
  if (key === ",") return text.includes(",") ? text : (text || "0") + ",";
  if (!/^\d$/.test(key) || (text.split(",")[1]?.length ?? 0) >= 2) return text;
  const next = text === "0" ? key : text + key;
  return (toCents(next) ?? 0) > maxCents ? text : next;
}

/** `29,9` → 2990; `null` for nothing typed or nothing to pay. */
export function toCents(text: string): number | null {
  if (!/^\d+(,\d{0,2})?$/.test(text)) return null;
  const [euros, cents = ""] = text.split(",");
  const total = Number(euros) * 100 + Number(cents.padEnd(2, "0"));
  return total > 0 ? total : null;
}

/** Where `cents` sits on the reveal's line from 0 to twice `price`, as 0..1; a guess beyond it sits at the end. */
export const linePlace = (cents: number, price: number) => Math.min(1, cents / (2 * price));

/** The row each pin at `places` (0..1) stands in, so that no two in a row are closer than `gap`. */
export function stackPins(places: number[], gap: number): number[] {
  const lastInRow: number[] = [];
  const rows = new Array<number>(places.length);
  for (const i of places.map((_, i) => i).sort((a, b) => places[a] - places[b])) {
    let row = lastInRow.findIndex((x) => places[i] - x >= gap);
    if (row < 0) row = lastInRow.length;
    lastInRow[row] = places[i];
    rows[i] = row;
  }
  return rows;
}
