// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { euro, linePlace, stackPins, toCents, typeKey } from "../src/ui/price";

const MAX = 10_000_000;
const typed = (keys: string) => [...keys].reduce((text, key) => typeKey(text, key, MAX), "");

test("prices read the German way", () => {
  expect([euro(1219), euro(899), euro(100_000), euro(12_345_678)]).toEqual(["12,19 €", "8,99 €", "1.000,00 €", "123.456,78 €"]);
});

test("the keypad types euros, one comma and at most two cents", () => {
  expect(typed("29,99")).toBe("29,99");
  expect(typed("29,999")).toBe("29,99");
  expect(typed("2,,9")).toBe("2,9");
  expect(typed(",5")).toBe("0,5");
  expect(typed("007")).toBe("7");
  expect(typed("12⌫")).toBe("1");
  expect(typeKey("", "⌫", MAX)).toBe("");
});

test("the keypad stops at the highest price", () => {
  expect(typed("1000000")).toBe("100000");
  expect(typeKey("5", "1", 500)).toBe("5", "51,00 € is over 5,00 €");
});

test("typed text is a price in cents, or nothing to send", () => {
  expect([toCents("29,9"), toCents("29,99"), toCents("29"), toCents("0,05")]).toEqual([2990, 2999, 2900, 5]);
  expect([toCents(""), toCents("0"), toCents("0,00"), toCents("x")]).toEqual([null, null, null, null]);
});

test("a guess sits on the line from 0 to twice the price, beyond it at the end", () => {
  expect([linePlace(0, 1000), linePlace(1000, 1000), linePlace(1500, 1000), linePlace(5000, 1000)]).toEqual([0, 0.5, 0.75, 1]);
});

test("pins too close to each other stack in rows", () => {
  expect(stackPins([0.5, 0.52, 0.9, 0.51], 0.05)).toEqual([0, 2, 0, 1]); // placed cheapest first
  expect(stackPins([0.1, 0.3], 0.05)).toEqual([0, 0]);
});
