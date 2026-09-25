import { describe, expect, test } from "bun:test";
import { pickerRows } from "../public/lib/catalog/collection-add.js";

const hit = (setId: string, title: string) => ({ setId, title, kind: "movie" });

describe("the picker's rows", () => {
  test("marks the titles already on the list", () => {
    const rows = pickerRows([hit("a", "Heat"), hit("b", "Arrival")], ["b"]);
    expect(rows.map((r) => [r.set.setId, r.inList])).toEqual([
      ["a", false],
      ["b", true],
    ]);
  });

  test("shows titles already on the list rather than hiding them", () => {
    // Hiding them would make a title the viewer just filed vanish under the
    // cursor, and leave no way to take it off again from here.
    const rows = pickerRows([hit("a", "Heat")], ["a"]);
    expect(rows).toHaveLength(1);
    expect(rows[0]?.inList).toBe(true);
  });

  test("keeps the order the search answered in", () => {
    const rows = pickerRows([hit("c", "C"), hit("a", "A"), hit("b", "B")], []);
    expect(rows.map((r) => r.set.setId)).toEqual(["c", "a", "b"]);
  });

  test("copes with an empty list and with no hits", () => {
    expect(pickerRows([], ["a"])).toEqual([]);
    expect(pickerRows([hit("a", "Heat")], [])[0]?.inList).toBe(false);
  });
});
