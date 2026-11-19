// Run with `bun test` in crates/mediagram-game/client.
import { afterEach, expect, test } from "bun:test";
import { BoardMode } from "../src/phone/use-board-mode";
import type { BoardScreen } from "../src/protocol/BoardScreen";
import type { ClientMsg } from "../src/protocol/ClientMsg";

type Bag = Record<string, string>;
const g = globalThis as Record<string, unknown>;
const CHOICE = "spieltisch.board";
const boardHello: ClientMsg = { t: "hello", role: "board", name: null, token: null };
const phoneHello: ClientMsg = { t: "hello", role: "phone", name: "Ada", token: "p1" };

// what the fake app's native board was told, in order: a show's resync flag, or "hide"
let native: (boolean | "hide")[] = [];

/** The app's bridge on an iPad (`board: true`) or an iPhone; every memory write, in order. */
function fakeApp(stored: Bag, board: boolean) {
  const writes: [string, string | null][] = [];
  native = [];
  g.mediagramApp = {
    board,
    vibrate: () => {},
    getMemory: () => JSON.stringify(stored),
    setMemory: (k: string, v: string | null) => writes.push([k, v]),
    showBoard: (json: string) => native.push(JSON.parse(json).resync),
    hideBoard: () => native.push("hide"),
  };
  return writes;
}

/** A BoardMode over a fake connection whose hello is PhoneApp's: the board's, else a phone's when named. */
function boardMode(named: boolean) {
  const sent: ClientMsg[] = [];
  const conn = {
    serverNow: () => 0,
    send: (msg: ClientMsg) => void sent.push(msg),
    rehello: () => {
      const hello = mode.hello() ?? (named ? phoneHello : null);
      if (hello) sent.push(hello);
      return hello !== null;
    },
  };
  const mode: BoardMode = new BoardMode(conn, () => {});
  return { mode, sent, conn };
}

afterEach(() => {
  delete g.mediagramApp;
  delete g.localStorage;
});

test("an iPad with no choice joins as the board", () => {
  fakeApp({}, true);
  const { mode, sent, conn } = boardMode(false);
  expect(mode.asBoard).toBe(true);
  conn.rehello(); // the socket opened
  expect(sent).toEqual([boardHello]);
});

test("a remembered player choice keeps the iPad playing", () => {
  fakeApp({ [CHOICE]: "player" }, true);
  const { mode } = boardMode(true);
  expect(mode.asBoard).toBe(false);
  expect(mode.hello()).toBeNull();
});

test("Doch mitspielen is remembered, and so is Als Zeichenbrett verwenden", () => {
  const writes = fakeApp({}, true);
  const { mode, sent } = boardMode(true);
  mode.leave();
  expect(mode.asBoard).toBe(false);
  expect(sent).toEqual([phoneHello]);
  expect(boardMode(true).mode.asBoard).toBe(false); // after a restart

  mode.become();
  expect(sent).toEqual([phoneHello, boardHello]);
  expect(boardMode(true).mode.asBoard).toBe(true);
  expect(writes.filter(([k]) => k === CHOICE)).toEqual([
    [CHOICE, "player"],
    [CHOICE, "board"],
  ]);
});

test("Doch mitspielen without a name lets go of the board with a nameless hello", () => {
  fakeApp({}, true);
  const { mode, sent } = boardMode(false);
  mode.leave();
  expect(sent).toEqual([{ t: "hello", role: "phone", name: null, token: null }]);
});

test("a browser never becomes the board", () => {
  g.localStorage = { getItem: (k: string) => (k === CHOICE ? "board" : null), setItem() {}, removeItem() {} };
  expect(boardMode(true).mode.asBoard).toBe(false);
  delete g.localStorage;

  fakeApp({}, false); // an iPhone
  const { mode } = boardMode(true);
  expect(mode.asBoard).toBe(false);
  expect(mode.hello()).toBeNull();
});

test("a refused board hello plays without forgetting the choice", () => {
  const writes = fakeApp({}, true);
  const { mode, sent, conn } = boardMode(true);
  conn.rehello();
  const refused = { t: "error", code: "not_allowed", message: "Ein anderes Gerät ist gerade das Zeichenbrett." } as const;
  expect(mode.onMessage(refused)).toBe(false); // PhoneApp toasts why
  expect(mode.asBoard).toBe(false);
  expect(sent).toEqual([boardHello, phoneHello]);
  expect(writes.filter(([k]) => k === CHOICE)).toEqual([]);
  expect(boardMode(true).mode.asBoard).toBe(true); // the next start tries again
});

const handover: BoardScreen = { kind: "handover", turn: 1, drawer: { id: 1, name: "Ada", color: "#2f6fde", avatar: null } };

/** The socket opens: hello, Welcome, then the board screen. */
function connect(mode: BoardMode) {
  mode.hello();
  mode.onMessage({ t: "welcome", player: null, token: "b1", now: 0 });
  mode.onMessage({ t: "board", screen: handover });
}

test("a closed socket hides the native board", () => {
  fakeApp({}, true);
  const { mode } = boardMode(false);
  connect(mode);
  mode.connection("closed");
  expect(native).toEqual([true, "hide"]);
});

test("a reconnect shows the board again with resync", () => {
  fakeApp({}, true);
  const { mode } = boardMode(false);
  connect(mode);
  mode.connection("closed");
  mode.connection("connecting");
  mode.connection("open");
  connect(mode);
  expect(native).toEqual([true, "hide", true]);
});

test("an open socket never hides the board", () => {
  fakeApp({}, true);
  const { mode } = boardMode(false);
  mode.connection("connecting");
  mode.connection("open");
  connect(mode);
  mode.connection("open");
  expect(native).toEqual([true]);
});
