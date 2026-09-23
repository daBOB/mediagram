import { describe, expect, test } from "bun:test";
import { ageLabel, ageOf, kidsShelf, kidsVerdict } from "../public/lib/age-rating.js";

const film = (title: string, fsk: string | null) => ({ setId: title, kind: "movie", title, fsk });
const episode = (show: string, fsk: string | null, n = 1) => ({ setId: `${show}-${n}`, kind: "ep", show, fsk });
const series = (name: string, fsk: string | null) => ({
  name,
  divisions: [{ title: "Season 1", items: [episode(name, fsk)] }],
});

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

describe("the Kids shelf", () => {
  const library = {
    movies: [film("Toy Story", "0"), film("Looper", "16"), film("Unrated Film", null), film("Hook", "6")],
    series: [series("30 Rock", "12"), series("Spartacus", "18"), series("Home Videos", null)],
    tutorials: [],
  };

  test("holds what is rated for kids without anyone marking it", () => {
    const shelf = kidsShelf(library, []);
    expect(shelf.films.map((s: { title: string }) => s.title)).toEqual(["Toy Story", "Hook"]);
    expect(shelf.series.map((c: { name: string }) => c.name)).toEqual(["30 Rock"]);
    expect(shelf.byHand).toEqual([]);
  });

  test("adds an unrated title only when someone marked it", () => {
    const marked = [film("Unrated Film", null), episode("Home Videos", null)];
    expect(kidsShelf(library, marked).byHand.map((s: { setId: string }) => s.setId)).toEqual([
      "Unrated Film",
      "Home Videos-1",
    ]);
  });

  test("a mark cannot put a title rated too old on it", () => {
    const marked = [film("Looper", "16"), episode("Spartacus", "18")];
    const shelf = kidsShelf(library, marked);
    expect(shelf.byHand).toEqual([]);
    expect(shelf.films.some((s: { title: string }) => s.title === "Looper")).toBe(false);
  });

  test("a mark on something already there is not listed twice", () => {
    const marked = [film("Toy Story", "0"), episode("30 Rock", "12", 2)];
    expect(kidsShelf(library, marked).byHand).toEqual([]);
  });
});
