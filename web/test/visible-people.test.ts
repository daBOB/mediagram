import { expect, test } from "bun:test";
import { visiblePeople } from "../public/lib/catalog/cast.js";

test("search keeps only people credited on a title this profile can see, counted by those", () => {
  const visible = new Set(["tmdb-movie-1"]);
  const byKey = (key: string) => ({ films: visible.has(key) ? [{}] : [], shows: [] });
  const people = [
    { personId: 1, name: "Seen", titles: ["tmdb-movie-1", "tmdb-movie-2"] },
    { personId: 2, name: "Hidden", titles: ["tmdb-movie-2"] },
  ];
  expect(visiblePeople(people, byKey).map((p: { name: string; titles: number }) => [p.name, p.titles])).toEqual([["Seen", 1]]);
});
