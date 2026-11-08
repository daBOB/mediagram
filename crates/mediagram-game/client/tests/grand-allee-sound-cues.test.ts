// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { grandAlleeCues } from "../src/tv/grand-allee/grand-allee-sound-cues";
import { clatterAt } from "../src/tv/race/race-sound-cues";
import { sceneOf } from "./grand-allee-fixture";

// a 4 from field 38 whose landing field (2) is taken: on to 3, five hops, passing the Bank on the second
test("a walk knocks on every hop it makes, not on the die's count, and the Bank pays on the hop that passes it", () => {
  const plan = grandAlleeCues(sceneOf({ turn: { stage: "walking", player: 0, value: 4, from: 38, to: 3, started_at: 1000, hop_ms: 280, bank: true } }), 0)!;
  expect(plan.cues.filter((c) => c.sound === "knock").map((c) => c.at)).toEqual([1280, 1560, 1840, 2120, 2400]);
  expect(plan.cues.find((c) => c.sound === "goalJingle")?.at).toBe(1560);
});

test("a guest's nights die clatters and the coins arrive with a jingle at paid_at", () => {
  const plan = grandAlleeCues(sceneOf({ turn: { stage: "guest", player: 0, owner: 1, hotel: 2, nights: 3, amount: 750, rolled_at: 0, lands_at: 1150, paid_at: 2050 } }), 0)!;
  expect(plan.cues).toEqual([{ sound: "die", at: clatterAt(0, 1150) }, { sound: "goalJingle", at: 2050 }]);
});

test("a player going broke thuds and screams; other notes are silent", () => {
  const note = { stage: "note", player: 1, text: "", started_at: 500 } as const;
  const broke = sceneOf({ turn: note });
  broke.seats[1].out = true;
  expect(grandAlleeCues(broke, 0)?.cues.map((c) => c.sound)).toEqual(["thunk", "scream"]);
  expect(grandAlleeCues(sceneOf({ turn: note }), 0)).toBeNull();
});

test("each stage's cues are keyed by its own clock, so they are scheduled once", () => {
  const permit = sceneOf({ turn: { stage: "permit", player: 0, hotel: 1, count: 1, permit: "approved", rolled_at: 42, lands_at: 1192 } });
  expect(grandAlleeCues(permit, 0)?.key).toBe("permit 42");
  expect(grandAlleeCues(permit, 999)?.key).toBe("permit 42");
});
