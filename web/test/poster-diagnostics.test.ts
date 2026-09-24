import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { PosterStore } from "../src/package/posters";

let directory: string;
let warning: ReturnType<typeof spyOn<typeof console, "warn">>;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), "mediagram-poster-diagnostics-"));
  warning = spyOn(console, "warn").mockImplementation(() => {});
});
afterEach(() => {
  warning.mockRestore();
  rmSync(directory, { recursive: true, force: true });
});

test("absent optional artwork remains quiet", () => {
  const store = new PosterStore(directory);
  expect(store.read("tmdb-movie-1")).toBeNull();
  expect(store.count()).toBe(0);
  expect(warning).not.toHaveBeenCalled();
});

test("an unreadable poster preserves the optional response and reports its original filesystem error", () => {
  const path = join(directory, "posters", "tmdb-movie-1.jpg");
  mkdirSync(path, { recursive: true });
  expect(new PosterStore(directory).read("tmdb-movie-1")).toBeNull();
  expect(warning).toHaveBeenCalledWith("Could not read poster", path, expect.objectContaining({ code: "EISDIR" }));
});

test("an unreadable artwork directory reports why its count is unavailable", () => {
  const path = join(directory, "posters");
  writeFileSync(path, "not a directory");
  expect(new PosterStore(directory).count()).toBe(0);
  expect(warning).toHaveBeenCalledWith("Could not count posters", path, expect.objectContaining({ code: "ENOTDIR" }));
});
