import { describe, expect, test } from "bun:test";
import * as rating from "../public/lib/age-rating.js";
import { ageLabel, ageOf, forKidsProfile, kidsLimitOf, kidsVerdict } from "../public/lib/age-rating.js";

const film = (title: string, fsk: string | null) => ({ setId: title, kind: "movie", title, fsk });
const episode = (show: string, fsk: string | null, n = 1) => ({ setId: `${show}-${n}`, kind: "ep", show, fsk });

describe("reading a rating", () => {
  test("an FSK is an age", () => {
    expect(ageOf(film("A", "12"))).toBe(12);
    expect(ageOf(film("A", "0"))).toBe(0);
    expect(ageLabel(film("A", "16"))).toBe("FSK 16");
  });

  test("no rating, or one that is not an age, is unrated", () => {
    expect(ageOf(film("A", null))).toBeNull();
    expect(ageOf(film("A", "PG-13"))).toBeNull();
    expect(ageLabel(film("A", null))).toBeNull();
  });
});

describe("the three rules, at a kid's own limit", () => {
  test("at 12, FSK 12 and younger is for kids; 16 and 18 are not", () => {
    for (const fsk of ["0", "6", "12"]) expect(kidsVerdict(film("A", fsk), 12)).toBe("safe");
    for (const fsk of ["16", "18"]) expect(kidsVerdict(film("A", fsk), 12)).toBe("unsafe");
    expect(kidsVerdict(film("A", null), 12)).toBe("unrated");
  });

  test("at 6, FSK 12 is not for kids", () => {
    for (const fsk of ["0", "6"]) expect(kidsVerdict(film("A", fsk), 6)).toBe("safe");
    for (const fsk of ["12", "16"]) expect(kidsVerdict(film("A", fsk), 6)).toBe("unsafe");
  });

  test("there is no one limit for every kid any more", () => {
    expect("KIDS_AGE_LIMIT" in rating).toBe(false);
  });
});

describe("a profile's limit", () => {
  test("a kid's own, 12 when it has none that reads as 6, and none for a grown-up", () => {
    expect(kidsLimitOf({ kids: true, kidsAge: 6 })).toBe(6);
    expect(kidsLimitOf({ kids: true, kidsAge: 12 })).toBe(12);
    expect(kidsLimitOf({ kids: true, kidsAge: null })).toBe(12);
    expect(kidsLimitOf({ kids: true })).toBe(12);
    expect(kidsLimitOf({ kids: false, kidsAge: null })).toBeNull();
    expect(kidsLimitOf(null)).toBeNull();
  });
});

describe("what a kids profile sees", () => {
  const sets = [
    film("Zero", "0"), film("Six", "6"), film("Twelve", "12"), film("Sixteen", "16"), film("Eighteen", "18"),
    film("Unrated", null), film("FromSix", null), film("FromTwelve", null), film("SixteenMarked", "16"),
    { setId: "lesson-1", kind: "tut", show: "Course", fsk: null },
    { setId: "lesson-2", kind: "tut", show: "Course", fsk: null },
    episode("Bluey", "0"), episode("Dexter", "18"),
  ] as any;
  const marks = new Map([["FromSix", 6], ["FromTwelve", 12], ["SixteenMarked", 6], ["lesson-2", 12]]);
  const seen = (limit: number) => forKidsProfile(sets, marks, limit).map((set: { setId: string }) => set.setId);

  test("at 12: rated twelve and under, and marks from 6 or 12, in the catalog's order", () => {
    expect(seen(12)).toEqual(["Zero", "Six", "Twelve", "FromSix", "FromTwelve", "lesson-2", "Bluey-1"]);
  });

  test("at 6: rated six and under, and marks from 6 only", () => {
    expect(seen(6)).toEqual(["Zero", "Six", "FromSix", "Bluey-1"]);
  });

  test("a hand mark does not override a rating", () => {
    for (const limit of [6, 12]) expect(seen(limit)).not.toContain("SixteenMarked");
  });

  test("unrated and unmarked is hidden at either limit", () => {
    for (const limit of [6, 12]) {
      expect(seen(limit)).not.toContain("Unrated");
      expect(seen(limit)).not.toContain("lesson-1");
    }
  });
});
