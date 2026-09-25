import { describe, expect, test } from "bun:test";
import { ageLabel, ageOf, forKidsProfile, kidsVerdict } from "../public/lib/age-rating.js";

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

describe("the three rules", () => {
  test("FSK 12 and younger is for kids; FSK 16 and 18 are not", () => {
    for (const fsk of ["0", "6", "12"]) expect(kidsVerdict(film("A", fsk))).toBe("safe");
    for (const fsk of ["16", "18"]) expect(kidsVerdict(film("A", fsk))).toBe("unsafe");
    expect(kidsVerdict(film("A", null))).toBe("unrated");
  });
});

describe("what a kids profile sees", () => {
  const sets = [
    film("Zero", "0"), film("Six", "6"), film("Twelve", "12"),
    film("Sixteen", "16"), film("Eighteen", "18"),
    film("Unrated", null), film("UnratedMarked", null),
    film("SixteenMarked", "16"),
    { setId: "lesson-1", kind: "tut", show: "Course", fsk: null },
    { setId: "lesson-2", kind: "tut", show: "Course", fsk: null },
    episode("Bluey", "0"), episode("Dexter", "18"),
  ] as any;
  const marked = new Set(["UnratedMarked", "SixteenMarked", "lesson-2"]);
  const seen = forKidsProfile(sets, marked).map((set) => set.setId);

  test("rated twelve and under is shown; sixteen and eighteen are not", () => {
    expect(seen).toEqual(expect.arrayContaining(["Zero", "Six", "Twelve", "Bluey-1"]));
    expect(seen).not.toContain("Sixteen");
    expect(seen).not.toContain("Eighteen");
    expect(seen).not.toContain("Dexter-1");
  });

  test("unrated is hidden unless marked by hand, lessons included", () => {
    expect(seen).not.toContain("Unrated");
    expect(seen).toContain("UnratedMarked");
    expect(seen).not.toContain("lesson-1");
    expect(seen).toContain("lesson-2");
  });

  test("a hand mark does not override a rating", () => {
    expect(seen).not.toContain("SixteenMarked");
  });

  test("the catalog's order is kept", () => {
    expect(seen).toEqual(["Zero", "Six", "Twelve", "UnratedMarked", "lesson-2", "Bluey-1"]);
  });
});
