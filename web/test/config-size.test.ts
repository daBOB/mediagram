/**
 * The cache quota is a number a person types, so it accepts the units a
 * person uses. "8589934592" is a typo waiting to change an order of
 * magnitude.
 */

import { describe, expect, test } from "bun:test";
import { parseSize } from "../src/config";

describe("sizes", () => {
  test("units mean what they look like", () => {
    expect(parseSize("1024")).toBe(1024);
    expect(parseSize("1K")).toBe(1024);
    expect(parseSize("512M")).toBe(512 * 1024 ** 2);
    expect(parseSize("8G")).toBe(8 * 1024 ** 3);
    expect(parseSize("1T")).toBe(1024 ** 4);
  });

  test("the spellings people actually use are accepted", () => {
    expect(parseSize("8g")).toBe(8 * 1024 ** 3);
    expect(parseSize("8GB")).toBe(8 * 1024 ** 3);
    expect(parseSize(" 8 G ")).toBe(8 * 1024 ** 3);
    expect(parseSize("1.5G")).toBe(Math.floor(1.5 * 1024 ** 3));
  });

  test("zero is a legitimate answer: it turns the cache off", () => {
    expect(parseSize("0")).toBe(0);
  });

  test("nonsense is refused rather than guessed at", () => {
    for (const bad of ["", "lots", "8X", "-1", "G"]) {
      expect(() => parseSize(bad)).toThrow();
    }
  });
});
