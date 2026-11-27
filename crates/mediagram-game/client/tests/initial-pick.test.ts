// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { initialPick } from "../src/tv/lobby/initial-pick";

// The TV home screen opens Spieltisch with `?game=<kind>`; the lobby makes it the room's pick once.
const playable = ["kopfnuss", "durak"];

test("picks a playable game", () => {
  expect(initialPick("?game=kopfnuss", playable)).toBe("kopfnuss");
});

test("with films=0 too", () => {
  expect(initialPick("?films=0&game=durak", playable)).toBe("durak");
});

test("unknown game is null", () => {
  expect(initialPick("?game=chess", playable)).toBeNull();
});

test("not playable is null", () => {
  expect(initialPick("?game=feldzug", playable)).toBeNull();
});

test("no param is null", () => {
  expect(initialPick("", playable)).toBeNull();
  expect(initialPick("?films=0", playable)).toBeNull();
});
