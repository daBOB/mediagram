import type { FluechtigHunter } from "../protocol/FluechtigHunter";

/**
 * Figure `index`'s number among its player's figures, or null when that player
 * has one: only a lone hunter (two players) moves two figures, and only then
 * do texts and the plan number them.
 */
export function figureOf(hunters: FluechtigHunter[], index: number): number | null {
  const owner = hunters[index]?.player;
  const mine = hunters.filter((h) => h.player === owner).length;
  if (mine < 2) return null;
  return hunters.slice(0, index).filter((h) => h.player === owner).length + 1;
}

/** "3, 8, 13, 18 und 24"; one item alone; two as "A und B". */
export function andList(items: (string | number)[]): string {
  if (items.length < 2) return items.map(String).join("");
  return `${items.slice(0, -1).join(", ")} und ${items[items.length - 1]}`;
}

/** A name's genitive ("Danas", "Max’"), as Grand Allee's cards write it. */
export { genitive } from "../tv/grand-allee/grand-allee-intro-text";

/** The fugitive's special tickets in a line: shadow rides, and double moves only when there are any. */
export function ticketLine(schatten: number, doppel: number, runner: string): string {
  const shadow = `${schatten} ${schatten === 1 ? "Schattenfahrt" : "Schattenfahrten"} (Linie geheim)`;
  const double = doppel > 0 ? ` und ${doppel} ${doppel === 1 ? "Doppelzug" : "Doppelzüge"} (zwei Fahrten am Stück)` : "";
  return `${runner} hat ${shadow}${double}.`;
}
