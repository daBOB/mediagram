// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { BauherrTurn } from "../src/protocol/BauherrTurn";
import { bauherrCues } from "../src/tv/bauherr/bauherr-sound-cues";

const cues = (t: BauherrTurn, now = 0) => bauherrCues(t, now)?.cues.map((c) => `${c.sound}@${c.at}`);

test("every die throw clatters once, as the die lands", () => {
  const rolled: BauherrTurn = { stage: "rolled", player: 1, value: 4, rolled_at: 5000, lands_at: 6150 };
  const guest: BauherrTurn = { stage: "guest", player: 1, owner: 2, plot: 0, nights: 3, amount: 900, rolled_at: 5000, lands_at: 6150, paid_at: 7000 };
  const permit: BauherrTurn = { stage: "permit", player: 1, plot: 0, outcome: "approved", rolled_at: 5000, lands_at: 6150 };
  for (const t of [rolled, guest, permit]) expect(cues(t)).toEqual([`die@${5000 + 1150 * 0.6 - 70}`]);
  expect(bauherrCues(rolled, 0)!.key).toBe("rolled 5000");
});

test("a walk knocks on every field it lands on, and passing the bank adds the jingle", () => {
  const walk = (bonus: boolean): BauherrTurn => ({ stage: "walking", player: 1, value: 3, from: 34, to: 1, started_at: 10_000, hop_ms: 280, bonus });
  expect(cues(walk(false))).toEqual(["knock@10280", "knock@10560", "knock@10840"]);
  expect(cues(walk(true))).toEqual(["knock@10280", "knock@10560", "knock@10840", "goalJingle@10840"]);
});

test("waiting and deciding are silent; the winner's jingle plays at once", () => {
  expect(bauherrCues({ stage: "roll", player: 1 }, 0)).toBeNull();
  expect(bauherrCues({ stage: "ask", player: 1, prompt: "?", plot: null, marks: [] }, 0)).toBeNull();
  expect(bauherrCues({ stage: "final", winner: 2, ranking: [2, 1] }, 777)).toEqual({ key: "final 2", cues: [{ sound: "winnerJingle", at: 777 }] });
});
