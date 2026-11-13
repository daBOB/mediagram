// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { FluechtigHunter } from "../src/protocol/FluechtigHunter";
import { andList, figureOf, genitive, ticketLine } from "../src/ui/fluechtig-naming";

const hunters = (owners: number[]): FluechtigHunter[] =>
  owners.map((player, i) => ({ player, at: i + 1, tickets: { taxi: 10, bus: 8, ubahn: 4, schatten: 0, doppel: 0 } }));

test("a player with one figure has no figure number; a lone hunter's two are 1 and 2", () => {
  const each = hunters([2, 3, 4]);
  expect(each.map((_, i) => figureOf(each, i))).toEqual([null, null, null]);
  const lone = hunters([2, 2]);
  expect(lone.map((_, i) => figureOf(lone, i))).toEqual([1, 2]);
});

test("lists read as German prose", () => {
  expect(andList([3, 8, 13, 18, 24])).toBe("3, 8, 13, 18 und 24");
  expect(andList(["Cem"])).toBe("Cem");
  expect(andList(["Cem", "Ela"])).toBe("Cem und Ela");
});

test("a name's genitive takes an apostrophe after s, x and z", () => {
  expect(genitive("Dana")).toBe("Danas");
  expect(genitive("Max")).toBe("Max’");
  expect(genitive("Jens")).toBe("Jens’");
  expect(genitive("Fritz")).toBe("Fritz’");
});

test("the fugitive's special tickets are counted, and double moves left out when there are none", () => {
  const five = ticketLine(5, 2, "Dana");
  expect(five).toContain("5 Schattenfahrten");
  expect(five).toContain("2 Doppelzüge");
  const one = ticketLine(1, 0, "Dana");
  expect(one).toContain("1 Schattenfahrt");
  expect(one).not.toContain("Schattenfahrten");
  expect(one).not.toContain("Doppel");
  expect(ticketLine(2, 1, "Dana")).toContain("1 Doppelzug");
});
