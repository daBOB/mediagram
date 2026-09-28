/**
 * `categoryRows` against the fixture shared with the Android port: the
 * no-rows-until-filed rule, natural sort, "Other" always last, and the
 * order units keep within a row.
 */

import { expect, test } from "bun:test";
import { categoryRows } from "../public/lib/categories.js";
import cases from "./fixtures/categories/rows.json";

interface Unit {
  name: string;
  category: string | null;
}

for (const { name, units, rows } of cases as { name: string; units: Unit[]; rows: { title: string; units: string[] }[] }[]) {
  test(`categoryRows: ${name}`, () => {
    const got = categoryRows(units, (unit: Unit) => unit.category).map(
      (row) => ({ title: row.title, units: row.units.map((unit: Unit) => unit.name) }),
    );
    expect(got).toEqual(rows);
  });
}
