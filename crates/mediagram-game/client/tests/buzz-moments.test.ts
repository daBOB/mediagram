// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { buzzFor } from "../src/phone/buzz-moments";
import type { PhoneScreen } from "../src/protocol/PhoneScreen";

const option = { label: "A", color: "red" };
const lobby = { kind: "lobby" } as unknown as PhoneScreen;
const wait = { kind: "wait", title: "Gleich geht's los", text: null } as PhoneScreen;
const choice = (opens_at: number, picked: number | null) =>
  ({ kind: "choice", prompt: null, options: [option], opens_at, closes_at: opens_at + 10_000, picked }) as unknown as PhoneScreen;
const sheet = (opens_at: number, options: number) =>
  ({ kind: "sheet", lines: [], hotels: [], prompt: null, options: Array(options).fill(option), opens_at, closes_at: null, bid: null }) as unknown as PhoneScreen;
const auction = (opens_at: number, placed: number | null) =>
  ({ ...sheet(opens_at, 0), closes_at: opens_at + 15_000, bid: { value: 1_000, max: 5_000, step: 100, placed } }) as unknown as PhoneScreen;
const outcome = (tone: "good" | "bad" | "neutral", title = "Falsch") =>
  ({ kind: "outcome", title, text: null, tone, points: 0 }) as PhoneScreen;
const final = (rank: number) => ({ kind: "final", rank, of: 3, score: 10, can_restart: false }) as PhoneScreen;
const cards = (...buttons: string[]) =>
  ({
    kind: "cards",
    desk: { prompt: "", info: null, table: [], hand: [], buttons: buttons.map((id) => ({ id, label: id, needs: "nothing", primary: false, suit: null })) },
  }) as unknown as PhoneScreen;

test("lobby never buzzes", () => {
  expect(buzzFor(null, lobby)).toBeNull();
  expect(buzzFor(lobby, lobby)).toBeNull();
});

test("a new question is your turn", () => {
  expect(buzzFor(wait, choice(1, null))).toBe("turn");
});

test("same screen again does not buzz again", () => {
  expect(buzzFor(choice(1, null), choice(1, null))).toBeNull();
  expect(buzzFor(outcome("bad"), outcome("bad"))).toBeNull();
  expect(buzzFor(final(1), final(1))).toBeNull();
});

test("the host taking your pick is buzzer accepted", () => {
  expect(buzzFor(choice(1, null), choice(1, 2))).toBe("accepted");
  expect(buzzFor(auction(1, null), auction(1, 500))).toBe("accepted");
  expect(buzzFor(auction(1, null), auction(1, 0))).toBe("accepted");
});

test("the next question after a pick is your turn again", () => {
  expect(buzzFor(choice(1, 2), choice(2, null))).toBe("turn");
});

test("only a bad outcome is a wrong answer", () => {
  expect(buzzFor(choice(1, 2), outcome("bad"))).toBe("wrong");
  expect(buzzFor(choice(1, 2), outcome("good"))).toBeNull();
  expect(buzzFor(choice(1, 2), outcome("neutral"))).toBeNull();
});

test("a different bad outcome buzzes again", () => {
  expect(buzzFor(outcome("bad", "Falsch"), outcome("bad", "Zu spät"))).toBe("wrong");
});

test("first place is game won", () => {
  expect(buzzFor(outcome("good"), final(1))).toBe("won");
  expect(buzzFor(outcome("good"), final(2))).toBeNull();
});

test("card buttons appearing is your turn, more buttons in the same turn are not", () => {
  expect(buzzFor(cards(), cards("play"))).toBe("turn");
  expect(buzzFor(cards("play"), cards("play", "keep"))).toBeNull();
  expect(buzzFor(cards("play"), cards())).toBeNull();
});

test("a sheet without options only informs", () => {
  expect(buzzFor(sheet(1, 0), sheet(1, 0))).toBeNull();
  expect(buzzFor(sheet(1, 0), sheet(1, 2))).toBe("turn");
  expect(buzzFor(sheet(1, 0), auction(2, null))).toBe("turn");
});

test("reconnecting onto an answered question stays quiet", () => {
  expect(buzzFor(null, choice(1, 2))).toBeNull();
});

const chase = (round: number, at: number | null) =>
  ({ kind: "fluechtig", phone: { round, ask: at === null ? null : { at, opens_at: round * 1000 + at } } }) as unknown as PhoneScreen;

test("a Flüchtig move buzzes once, not again for its ticket question", () => {
  expect(buzzFor(chase(1, null), chase(1, 6))).toBe("turn");
  expect(buzzFor(chase(1, 6), chase(1, 6))).toBeNull();
  // the second ride of a double move starts from the new field
  expect(buzzFor(chase(1, 6), chase(1, 7))).toBe("turn");
  expect(buzzFor(chase(1, 6), chase(1, null))).toBeNull();
});
