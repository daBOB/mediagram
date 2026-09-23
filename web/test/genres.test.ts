import { describe, expect, test } from "bun:test";
import { genreHash, genreShelf, genresOf, scoreLabel } from "../public/lib/genres.js";

const film = (title: string, genres?: string[]) => ({ setId: title, kind: "movie", title, genres });
const show = (name: string, genres: string[]) => ({
  name,
  divisions: [{ title: "Season 1", items: [{ setId: `${name}-1`, kind: "ep", show: name, genres }] }],
});

describe("a genre's shelf", () => {
  const library = {
    movies: [film("Logan Lucky", ["Komödie", "Krimi"]), film("Looper", ["Action", "Thriller"]), film("Untagged")],
    series: [show("30 Rock", ["Komödie"]), show("Star City", ["Drama"])],
    tutorials: [],
  };

  test("holds the films and the series tagged with it", () => {
    const { films, series } = genreShelf(library, "Komödie");
    expect(films.map((set: { title: string }) => set.title)).toEqual(["Logan Lucky"]);
    expect(series.map((c: { name: string }) => c.name)).toEqual(["30 Rock"]);
  });

  test("matches the name exactly, as the uploader stored it", () => {
    expect(genreShelf(library, "komödie")).toEqual({ films: [], series: [] });
    expect(genreShelf(library, "Krim")).toEqual({ films: [], series: [] });
  });

  test("a title with no genres recorded is on no shelf, and does not break one", () => {
    expect(genresOf(film("Untagged"))).toEqual([]);
    expect(genreShelf(library, "Action").films.map((set: { title: string }) => set.title)).toEqual(["Looper"]);
  });
});

describe("links and the score", () => {
  test("a genre's link survives names a URL would mangle", () => {
    expect(genreHash("Science Fiction")).toBe("#/genre/Science%20Fiction");
    expect(decodeURIComponent(genreHash("Action & Adventure").slice("#/genre/".length))).toBe("Action & Adventure");
  });

  test("the score is printed to one place, and an unrated title says nothing", () => {
    expect(scoreLabel(6.734)).toBe("★ 6.7");
    expect(scoreLabel(0)).toBeNull();
    expect(scoreLabel(null)).toBeNull();
  });
});
