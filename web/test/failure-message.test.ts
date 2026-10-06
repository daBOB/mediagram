import { expect, test } from "bun:test";
import { readFile } from "node:fs/promises";
import { Database } from "bun:sqlite";
import { errorCode } from "../src/failure-message";

test("errorCode reads the code an fs or SQLite error carries", async () => {
  expect(errorCode(await readFile("/no/such/file").catch((error: unknown) => error))).toBe("ENOENT");

  const db = new Database(":memory:");
  db.run("PRAGMA foreign_keys = ON");
  db.run("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
  db.run("CREATE TABLE child(parent INTEGER REFERENCES parent(id))");
  let refusal: unknown;
  try { db.run("INSERT INTO child VALUES (1)"); } catch (error) { refusal = error; }
  expect(errorCode(refusal)).toBe("SQLITE_CONSTRAINT_FOREIGNKEY");
});

test("errorCode answers undefined, rather than throwing, for a rejection with no string code", () => {
  for (const rejection of [null, undefined, "ENOENT", 2, new Error("plain"), { code: 2 }, { code: null }]) {
    expect(errorCode(rejection)).toBeUndefined();
  }
});
