// Run with `bun test` in crates/mediagram-game/client.
import { afterEach, expect, test } from "bun:test";
import { boardCapable, buzz, inApp, listenToBoard, memory } from "../src/phone/app-bridge";

type Bag = Record<string, string>;
const g = globalThis as Record<string, unknown>;
const nav = globalThis.navigator as unknown as Record<string, unknown>;

function fakeStorage(bag: Bag = {}) {
  return {
    bag,
    getItem: (k: string) => (k in bag ? bag[k] : null),
    setItem: (k: string, v: string) => {
      bag[k] = v;
    },
    removeItem: (k: string) => {
      delete bag[k];
    },
  };
}

function fakeBridge(stored: Bag = {}) {
  const calls: { vibrate: string[]; setMemory: [string, string | null][] } = { vibrate: [], setMemory: [] };
  return {
    calls,
    vibrate: (k: string) => calls.vibrate.push(k),
    getMemory: () => JSON.stringify(stored),
    setMemory: (k: string, v: string | null) => calls.setMemory.push([k, v]),
  };
}

afterEach(() => {
  delete g.mediagramApp;
  delete g.mediagramBoard;
  delete g.localStorage;
  delete nav.vibrate;
});

test("no bridge: memory reads and writes localStorage", () => {
  const storage = fakeStorage();
  g.localStorage = storage;

  memory.set("spieltisch.name", "Ada");
  expect(storage.getItem("spieltisch.name")).toBe("Ada");
  expect(memory.get("spieltisch.name")).toBe("Ada");

  memory.set("spieltisch.name", null);
  expect(storage.getItem("spieltisch.name")).toBeNull();
  expect(inApp()).toBe(false);
});

test("bridge: memory comes from getMemory and writes go to setMemory", () => {
  const storage = fakeStorage();
  g.localStorage = storage;
  const bridge = fakeBridge({ "spieltisch.name": "Ada" });
  g.mediagramApp = bridge;

  expect(inApp()).toBe(true);
  expect(memory.get("spieltisch.name")).toBe("Ada");

  memory.set("spieltisch.token", "t1");
  expect(bridge.calls.setMemory).toEqual([["spieltisch.token", "t1"]]);
  expect(memory.get("spieltisch.token")).toBe("t1");

  memory.set("spieltisch.name", null);
  expect(memory.get("spieltisch.name")).toBeNull();
  expect(storage.bag).toEqual({});
});

test("bridge: buzz calls vibrate with the kind", () => {
  const bridge = fakeBridge();
  g.mediagramApp = bridge;
  const browser: unknown[] = [];
  nav.vibrate = (p: unknown) => browser.push(p);

  buzz("won");

  expect(bridge.calls.vibrate).toEqual(["won"]);
  expect(browser).toEqual([]);
});

test("browser: buzz uses navigator.vibrate when present and never throws without it", () => {
  const browser: unknown[] = [];
  nav.vibrate = (p: unknown) => browser.push(p);
  buzz("turn");
  expect(browser.length).toBe(1);

  delete nav.vibrate;
  expect(() => buzz("wrong")).not.toThrow();
});

test("storage that throws is ignored", () => {
  g.localStorage = {
    getItem: () => {
      throw new Error("denied");
    },
    setItem: () => {
      throw new Error("denied");
    },
    removeItem: () => {
      throw new Error("denied");
    },
  };

  expect(memory.get("spieltisch.name")).toBeNull();
  expect(() => memory.set("spieltisch.name", "Ada")).not.toThrow();
});

test("the board is offered only when the app says it is an iPad", () => {
  expect(boardCapable()).toBe(false);
  g.mediagramApp = { ...fakeBridge(), board: false };
  expect(boardCapable()).toBe(false);
  g.mediagramApp = { ...fakeBridge(), board: true };
  expect(boardCapable()).toBe(true);
});

test("board acts reach the page parsed, until removed", () => {
  const acts: unknown[] = [];
  const remove = listenToBoard((act) => acts.push(act));
  const board = () => g.mediagramBoard as { receive(json: string): void } | undefined;

  board()!.receive('{"t":"pick","player":3}');
  board()!.receive("not json");
  board()!.receive("null");
  remove();

  expect(acts).toEqual([{ t: "pick", player: 3 }]);
  expect(board()).toBeUndefined();
});
