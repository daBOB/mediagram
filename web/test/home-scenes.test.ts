import { describe, expect, test } from "bun:test";
import { catalogSet } from "./support/catalog-set";
import { sceneCards } from "../public/lib/catalog/home-scenes.js";

describe("sceneCards", () => {
  test("keeps newest first and drops marks for missing titles", () => {
    const film = catalogSet({ setId: "FILM", title: "Heat" });
    const other = catalogSet({ setId: "OTHER", title: "Alien" });
    const byId = new Map([[film.setId, film], [other.setId, other]]);
    const cards = sceneCards([
      { id: "a", setId: "OTHER", at: 5 },
      { id: "b", setId: "GONE", at: 9 },
      { id: "c", setId: "FILM", at: 3725 },
    ], byId);
    expect(cards.map((card: { id: string }) => card.id)).toEqual(["a", "c"]);
    expect(cards[1]?.set).toBe(film);
    expect(cards[1]?.at).toBe(3725);
    expect(cards[1]?.label).toBe("Heat · 1:02:05");
  });

  test("an episode's label names the episode", () => {
    const ep = catalogSet({ setId: "EP", kind: "ep", show: "Lost", season: 1, episode: "2", title: "Pilot" });
    const [card] =  sceneCards([{ id: "a", setId: "EP", at: 65 }], new Map([[ep.setId, ep]]));
    expect(card?.label).toBe("Lost · S1E2 · Pilot · 1:05");
  });
});
