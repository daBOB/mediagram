import { describe, expect, test } from "bun:test";
import { HandleMap } from "../src/settings/handles";

describe("HandleMap", () => {
  test("a value round-trips through its handle", () => {
    const map = new HandleMap<{ title: string }>();
    const [entry] = map.reset([{ title: "Mediagram" }]);
    expect(map.get(entry!.handle)).toEqual({ title: "Mediagram" });
  });

  test("resetting discards every previous handle", () => {
    const map = new HandleMap<string>();
    const [first] = map.reset(["a"]);
    map.reset(["b"]);
    expect(map.get(first!.handle)).toBeUndefined();
  });

  test("an unknown handle answers undefined", () => {
    const map = new HandleMap<string>();
    expect(map.get("nonsense")).toBeUndefined();
  });

  test("handles are distinct across a listing", () => {
    const map = new HandleMap<number>();
    const entries = map.reset([1, 2, 3]);
    expect(new Set(entries.map((e) => e.handle)).size).toBe(3);
  });
});
