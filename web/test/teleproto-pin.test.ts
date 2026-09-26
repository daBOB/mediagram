import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * `measured-client.ts` reaches past `teleproto`'s public surface into three
 * seams verified by reading its source at this exact version: the shape of
 * `invoke`, the flood-wait log line's wording, and `UpdateConnectionState`
 * being reachable through `teleproto/network`. An upgrade that moves any of
 * those needs the same reading done again before it can be trusted, so the
 * version is pinned exactly rather than with a caret.
 */
test("teleproto stays pinned to the exact version its request-measuring seams were verified against", () => {
  const pkg = JSON.parse(readFileSync(join(import.meta.dir, "..", "package.json"), "utf8")) as {
    dependencies: Record<string, string>;
  };
  expect(pkg.dependencies.teleproto).toBe("1.229.0");
});
