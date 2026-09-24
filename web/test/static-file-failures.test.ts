import { expect, test } from "bun:test";
import { mkdtempSync, rmSync, symlinkSync } from "node:fs";
import { join, basename } from "node:path";
import { staticResponse } from "../src/http/static-files";

const get = (path: string) => staticResponse({ method: "GET", path, range: null });

test("missing files and malformed paths remain ordinary 404 responses", () => {
  for (const path of ["/missing-file.txt", "/%zz", "/%00", "/app.js/child"]) {
    expect(get(path).status).toBe(404);
  }
});

test("unexpected filesystem errors reach the request error boundary", () => {
  const directory = mkdtempSync(new URL("../public/static-test-", import.meta.url).pathname);
  try {
    symlinkSync("loop", join(directory, "loop"));
    expect(() => get(`/${basename(directory)}/loop`)).toThrow(/ELOOP/);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});
