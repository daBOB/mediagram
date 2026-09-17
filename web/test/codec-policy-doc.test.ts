/**
 * The codec table in the operating document, against the code that decides.
 *
 * Which profiles direct-play is the first question anyone asks, so it is
 * written down — and a table written down is a table that goes stale. This
 * fails when the two disagree, which is the only way prose about code stays
 * true.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";

import { AUDIO, CONTAINERS, VIDEO } from "../public/lib/playable.js";

const DOC = new URL("../../docs/running-the-player.md", import.meta.url).pathname;

/**
 * The `Direct play` cell of the row whose first cell is `label`, as a list.
 *
 * Read from the rendered table rather than from a fenced block, so the thing
 * checked is the thing a reader sees.
 */
function documented(label: string): string[] {
  const text = readFileSync(DOC, "utf8");
  const row = text
    .split("\n")
    .find((line) => line.startsWith(`| ${label} |`));
  if (!row) throw new Error(`no \`${label}\` row in the codec table of ${DOC}`);

  const cell = row.split("|")[2];
  if (cell === undefined) throw new Error(`the \`${label}\` row has no direct-play column`);
  return cell
    .split(",")
    .map((name) => name.trim().replace(/`/g, ""))
    .filter(Boolean);
}

describe("the documented codec policy", () => {
  test("lists exactly the containers that direct-play", () => {
    expect(documented("Container").sort()).toEqual([...CONTAINERS].sort());
  });

  test("lists exactly the video codecs that direct-play", () => {
    expect(documented("Video").sort()).toEqual([...VIDEO].sort());
  });

  test("lists exactly the audio codecs that direct-play", () => {
    expect(documented("Audio").sort()).toEqual([...AUDIO].sort());
  });
});
