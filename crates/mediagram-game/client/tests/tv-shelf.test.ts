// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { GameEntry } from "../src/protocol/GameEntry";
import { gameLine } from "../src/ui/lobby";
import { gameArtUrl, okStarts, scrollToShow, shelfLine, stepIndex, type ShelfFacts } from "../src/ui/shelf";

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

test("the row scrolls only as far as it takes to show the focused poster whole", () => {
  expect(scrollToShow(0, 1000, 200, 400)).toBe(0);
  expect(scrollToShow(0, 1000, 900, 1100)).toBe(100);
  expect(scrollToShow(500, 1000, 300, 500)).toBe(300);
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
