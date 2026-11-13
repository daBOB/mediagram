// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import city from "../../cities/fluechtig-big-city.json";
import type { FluechtigBoard } from "../src/protocol/FluechtigBoard";
import type { FluechtigTicket } from "../src/protocol/FluechtigTicket";
import { bridgeDecks, streetRoutes } from "../src/tv/fluechtig/street-routes";

/** The city the room plays, as the TV gets it. */
const lines: [FluechtigTicket, number[][]][] = [
  ["taxi", city.taxi],
  ["bus", city.bus],
  ["ubahn", city.ubahn],
  ["schatten", city.ferry],
];
const board: FluechtigBoard = {
  width: city.size[0],
  height: city.size[1],
  spots: city.spots.map(([x, y]) => ({ x, y })),
  links: lines.flatMap(([kind, pairs]) => pairs.map(([a, b]) => ({ a, b, kind }))),
  river: city.river.map(([x, y]) => ({ x, y })),
};
const key = (a: number, b: number) => `${Math.min(a, b)}-${Math.max(a, b)}`;

test("every bus drives along streets from its stop to the next, passing no other stop", () => {
  const streets = new Set(board.links.filter((l) => l.kind === "taxi").map((l) => key(l.a, l.b)));
  const rides = board.links.filter((l) => l.kind === "bus");
  const stops = new Set(rides.flatMap((l) => [l.a, l.b]));
  const routes = streetRoutes(board, rides);
  expect(routes.length).toBe(rides.length);
  routes.forEach((route, i) => {
    const { a, b } = rides[i];
    expect([route[0], route[route.length - 1]]).toEqual([a, b]);
    for (let j = 1; j < route.length; j++) expect(streets.has(key(route[j - 1], route[j]))).toBe(true);
    expect(route.slice(1, -1).filter((f) => stops.has(f))).toEqual([]);
  });
});

test("the river gets a deck under each of its six bridges", () => {
  expect(bridgeDecks(board).match(/M/g)?.length).toBe(6);
});

test("a U-Bahn under the river needs no deck", () => {
  const under: FluechtigBoard = {
    width: 300,
    height: 300,
    spots: [
      { x: 100, y: 100 },
      { x: 100, y: 200 },
    ],
    links: [{ a: 1, b: 2, kind: "ubahn" }],
    river: [
      { x: -10, y: 150 },
      { x: 310, y: 150 },
    ],
  };
  expect(bridgeDecks(under)).toBe("");
});
