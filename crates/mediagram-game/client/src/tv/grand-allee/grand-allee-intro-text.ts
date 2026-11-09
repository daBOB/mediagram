// The rules cards' text: gold keywords marked in the host's lines, the lines wrapped to the card, and whose phone skips. Kept
// apart from the drawing so it runs in the tests without a canvas.

export interface Run {
  text: string;
  gold: boolean;
}

/** "a **b** c" as runs of text, gold between the double stars. */
export const runs = (line: string): Run[] => line.split("**").map((text, i) => ({ text, gold: i % 2 === 1 })).filter((r) => r.text);

/** Breaks a line's runs into rows no wider than `max`, only at spaces; `width` measures a run in its own font. */
export function wrap(line: Run[], max: number, width: (r: Run) => number): Run[][] {
  // words: the pieces up to and including a space, so "**Baustelle**?" stays one word in two colours
  const words: Run[][] = [];
  let open = false;
  for (const r of line) {
    for (const text of r.text.split(/(?<= )/)) {
      if (!open) words.push([]);
      words[words.length - 1].push({ text, gold: r.gold });
      open = !text.endsWith(" ");
    }
  }
  const rows: Run[][] = [];
  let row: Run[] = [], x = 0;
  for (const word of words) {
    const full = word.reduce((s, r) => s + width(r), 0), last = word[word.length - 1];
    const bare = full - width(last) + width({ ...last, text: last.text.trimEnd() });
    if (row.length && x + bare > max) {
      rows.push(row);
      [row, x] = [[], 0];
    }
    row.push(...word);
    x += full;
  }
  if (row.length) rows.push(row);
  return rows;
}

/** "Lenas", but "Klaus’": a name ending in an s sound takes only the apostrophe. */
export const genitive = (name: string) => (/[sxzß]$/i.test(name) ? `${name}’` : `${name}s`);
