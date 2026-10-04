/** Covers a grown-up's PIN: what counts as one, and how it is kept. */

import { describe, expect, test } from "bun:test";
import { hashPin, newPin, pinMatches, validPin } from "../src/state/profiles-pin";

describe("a PIN", () => {
  test("is exactly four ASCII digits", () => {
    for (const pin of ["0000", "1234", "9999"]) expect(validPin(pin)).toBe(true);
    for (const pin of ["", "123", "12345", "12a4", " 1234", "1234 ", "1234\n", "١٢٣٤", 1234, null, undefined]) {
      expect(validPin(pin)).toBe(false);
    }
  });

  test("is kept as a fresh salt and the hash of salt and PIN", () => {
    const first = newPin("1234");
    const second = newPin("1234");
    expect(first.salt).toMatch(/^[0-9a-f]{32}$/);
    expect(first.hash).toMatch(/^[0-9a-f]{64}$/);
    expect(first.hash).toBe(hashPin(first.salt, "1234"));
    expect(second.salt).not.toBe(first.salt);
  });

  test("matches only the PIN it was made from", () => {
    const { hash, salt } = newPin("2468");
    expect(pinMatches(hash, salt, "2468")).toBe(true);
    expect(pinMatches(hash, salt, "2469")).toBe(false);
  });

  test("a stored hash of the wrong length is a mismatch, not a crash", () => {
    const { salt } = newPin("2468");
    expect(pinMatches("abcd", salt, "2468")).toBe(false);
    expect(pinMatches("", salt, "2468")).toBe(false);
  });
});
