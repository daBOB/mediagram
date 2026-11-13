// Run with `bun test` in crates/mediagram-game/client.
import { afterEach, expect, test } from "bun:test";
import { NativeBoardFeed, nativeShow, type NativeBoardShow } from "../src/phone/board-native";
import type { BoardPlayer } from "../src/protocol/BoardPlayer";
import type { BoardScreen } from "../src/protocol/BoardScreen";
import type { DrawOp } from "../src/protocol/DrawOp";

const g = globalThis as Record<string, unknown>;
const ada: BoardPlayer = { id: 1, name: "Ada", color: "#2f6fde", avatar: null };
const bo: BoardPlayer = { id: 2, name: "Bo", color: "#d8342b", avatar: "fox" };
const wait: BoardScreen = { kind: "wait", title: "Zeichenbrett", text: null, can_leave: true };
const handover = (turn: number): BoardScreen => ({ kind: "handover", turn, drawer: ada });
const drawing = (endsAt: number | null): BoardScreen => ({
  kind: "drawing",
  turn: 1,
  drawer: ada,
  word: "Hund",
  clock: { ends_at: endsAt, left_ms: 90_000, total_ms: 90_000 },
});
const line: DrawOp = { t: "line", color: 0, width: 1, points: [10, 20] };
const more: DrawOp = { t: "more", points: [30, 40] };

/** A fake iPad app: every show (parsed back from its JSON) and every hide, in order. */
function fakeApp() {
  const calls: (NativeBoardShow | "hide")[] = [];
  g.mediagramApp = {
    board: true,
    showBoard: (json: string) => calls.push(JSON.parse(json)),
    hideBoard: () => calls.push("hide"),
  };
  return calls;
}
const brief = (calls: (NativeBoardShow | "hide")[]) =>
  calls.map((c) => (c === "hide" ? c : [c.screen.kind, c.resync, c.ops.length]));

afterEach(() => {
  delete g.mediagramApp;
});

test("a wait screen is left to the page", () => {
  expect(nativeShow(wait, [line], true, 10_000, 1_700_000_000_000)).toBeNull();
});

test("a running clock turns into wall time", () => {
  const show = nativeShow(drawing(40_000), [], false, 10_000, 1_700_000_000_000);
  expect(show?.screen).toEqual({
    kind: "drawing",
    turn: 1,
    drawer: ada,
    word: "Hund",
    clock: { ends_at_epoch_ms: 1_700_000_030_000, left_ms: 90_000, total_ms: 90_000 },
  });
});

test("a paused clock stays paused", () => {
  const picking: BoardScreen = {
    kind: "picking",
    turn: 3,
    drawer: ada,
    word: "Hund",
    clock: { ends_at: null, left_ms: 31_500, total_ms: 90_000 },
    guessers: [bo],
    can_go_back: true,
  };
  expect(nativeShow(picking, [], false, 10_000, 1_700_000_000_000)?.screen).toEqual({
    kind: "picking",
    turn: 3,
    drawer: ada,
    word: "Hund",
    clock: { ends_at_epoch_ms: null, left_ms: 31_500, total_ms: 90_000 },
    guessers: [bo],
    can_go_back: true,
  });
});

test("a show carries the ops and the resync flag", () => {
  expect(nativeShow(handover(2), [line, more], true, 0, 0)).toEqual({
    screen: { kind: "handover", turn: 2, drawer: ada },
    ops: [line, more],
    resync: true,
  });
});

test("a reconnect reshows an unchanged screen with resync", () => {
  const calls = fakeApp();
  const feed = new NativeBoardFeed(() => 10_000);
  feed.welcome();
  feed.board(handover(1));
  feed.board(handover(1)); // the box sending the same screen again shows nothing
  feed.welcome();
  feed.board(handover(1));
  // after a Welcome the box sends the drawing from 0, then the screen: one show, with both
  feed.welcome();
  feed.draw({ turn: 1, from: 0, ops: [line] });
  feed.board(drawing(40_000));
  feed.board(drawing(41_000));
  expect(brief(calls)).toEqual([
    ["handover", true, 0],
    ["handover", true, 0],
    ["drawing", true, 1],
    ["drawing", false, 1],
  ]);
});

test("new ops reshow the screen, a gap does not", () => {
  const calls = fakeApp();
  const feed = new NativeBoardFeed(() => 10_000);
  feed.welcome();
  feed.board(drawing(40_000));
  feed.draw({ turn: 1, from: 0, ops: [line] });
  feed.draw({ turn: 1, from: 1, ops: [more] });
  feed.draw({ turn: 1, from: 9, ops: [more] });
  expect(brief(calls)).toEqual([
    ["drawing", true, 0],
    ["drawing", false, 1],
    ["drawing", false, 2],
  ]);
});

test("a wait hides the board and the first native show after the Welcome still resyncs", () => {
  const calls = fakeApp();
  const feed = new NativeBoardFeed(() => 10_000);
  feed.welcome();
  feed.board(wait);
  feed.draw({ turn: 1, from: 0, ops: [] }); // a draw under a wait shows nothing
  feed.board(handover(1));
  feed.stop();
  expect(brief(calls)).toEqual(["hide", ["handover", true, 0], "hide"]);
});
