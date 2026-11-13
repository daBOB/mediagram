// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { GameEntry } from "../src/protocol/GameEntry";
import { gameLine, seatsLine } from "../src/ui/lobby";
import { gameArtUrl, okStarts, shelfLine, stepIndex, windowStart, type ShelfFacts } from "../src/ui/shelf";
import { UPCOMING, shelfItems, upcomingLine } from "../src/ui/shelf-games";

const quiz: GameEntry = { kind: "quiz", name: "Buzzer-Quiz", min: 1, max: 8 };
const race: GameEntry = { kind: "race", name: "Heimlauf", min: 2, max: 4 };
const facts = (f: Partial<ShelfFacts> = {}): ShelfFacts => ({
  seated: 3,
  minPlayers: 1,
  name: "Preisfrage",
  heard: true,
  blocked: null,
  leader: "Ana",
  ...f,
});

test("the line under a game's name gives its seats, and the quiz its questions", () => {
  expect(gameLine(quiz, "films")).toBe("1–8 Spieler · Fragen: Filme");
  expect(gameLine(race, "mixed")).toBe("2–4 Spieler");
});

test("art is asked for by the game's kind, in the file names the app keeps", () => {
  expect(gameArtUrl("mau_mau", "poster")).toBe("/game-art/poster-mau-mau.webp");
  expect(gameArtUrl("preisfrage", "backdrop")).toBe("/game-art/backdrop-preisfrage.webp");
});

test("Left and Right stop at the ends of the row", () => {
  expect(stepIndex(0, -1, 11)).toBe(0);
  expect(stepIndex(4, 1, 11)).toBe(5);
  expect(stepIndex(10, 1, 11)).toBe(10);
});

test("the row shows five posters and moves only as far as it takes to keep the focused one on screen", () => {
  expect(windowStart(0, 3, 5)).toBe(0);
  expect(windowStart(0, 6, 5)).toBe(2);
  expect(windowStart(2, 1, 5)).toBe(1);
  expect(windowStart(2, 5, 5)).toBe(2);
});

test("the status line says what OK does, or why it cannot", () => {
  expect(shelfLine(facts({ seated: 0 }))).toBe("Zum Mitspielen: Handy-Kamera auf den Code");
  expect(shelfLine(facts())).toBe("OK startet Preisfrage · Ana kann auch am Handy starten");
  expect(shelfLine(facts({ leader: null }))).toBe("OK startet Preisfrage");
  expect(shelfLine(facts({ blocked: "Preisfrage gibt es nicht im Kindermodus." }))).toBe("Preisfrage gibt es nicht im Kindermodus.");
  expect(shelfLine(facts({ seated: 1, minPlayers: 2 }))).toBe("Wartet auf 1 Spieler");
  // a pick on its way: the host's reason is still about the game before it
  expect(shelfLine(facts({ heard: false, blocked: "Heimlauf geht mit höchstens 4 Spielern." }))).toBe("");
});

test("OK starts with someone at the table, unless the host has said why not", () => {
  expect(okStarts(facts())).toBe(true);
  expect(okStarts(facts({ seated: 0 }))).toBe(false);
  expect(okStarts(facts({ blocked: "nein" }))).toBe(false);
  // not heard yet: the host judges the pick it receives with the start
  expect(okStarts(facts({ heard: false, blocked: "about the old pick" }))).toBe(true);
});

test("a game for exactly so many players says that number once", () => {
  expect(seatsLine(2, 6)).toBe("2–6 Spieler");
  expect(seatsLine(4, 4)).toBe("4 Spieler");
});

test("a game not playable yet asks for its art by its shelf key", () => {
  expect(gameArtUrl("tien-len", "poster")).toBe("/game-art/poster-tien-len.webp");
  expect(gameArtUrl("grand_allee", "backdrop")).toBe("/game-art/backdrop-grand-allee.webp");
});

test("the shelf shows the minigames, then the full games, each playable first, then WIP, then coming soon", () => {
  // Arrange: the host lists a full game before a minigame
  const durak: GameEntry = { kind: "durak", name: "Durak", min: 2, max: 6 };

  // Act
  const items = shelfItems([durak, race, quiz]);
  const keys = items.map((i) => i.key);

  // Assert
  expect(keys.slice(0, 2)).toEqual(["quiz", "neunmalklug"]);
  expect(keys.slice(2, 5)).toEqual(["durak", "race", "lebensweg"]);
  expect(keys.at(-1)).toBe("klabberjass");
  expect(items.length).toBe(3 + UPCOMING.length);
  expect(items[0].game).toBe(quiz);
  expect(items.find((i) => i.key === "lebensweg")?.badge).toBe("wip");
  expect(items.find((i) => i.key === "wissensreise")?.badge).toBe("soon");
});

test("every game on the shelf has its own key and says what it is", () => {
  const items = shelfItems([quiz, race]);
  expect(new Set(items.map((i) => i.key)).size).toBe(items.length);
  for (const i of items) expect(i.blurb.length).toBeGreaterThan(40);
});

test("an unplayable game's line says it cannot start yet, and why", () => {
  expect(upcomingLine("wip")).toBe("In Arbeit – noch nicht spielbar");
  expect(upcomingLine("soon")).toBe("Kommt bald – noch nicht spielbar");
});
