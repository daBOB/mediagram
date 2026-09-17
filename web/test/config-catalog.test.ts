/**
 * Which settings a player actually needs depends on where its catalog comes
 * from.
 *
 * A player given a published package never opens a local `library.db` — the
 * whole point of the package is that there is no uploader filesystem to read.
 * Demanding a path to a file it will not touch turns a working configuration
 * into a startup failure for no reason.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";

import { load } from "../src/config";

const REQUIRED = {
  MEDIAGRAM_API_ID: "12345",
  MEDIAGRAM_API_HASH: "hash",
  MEDIAGRAM_SESSION: "session",
  MEDIAGRAM_CHAT_ID: "-1001",
  MEDIAGRAM_CHANNEL_ACCESS_HASH: "99",
};

const TOUCHED = [
  ...Object.keys(REQUIRED),
  "MEDIAGRAM_LIBRARY_DB",
  "MEDIAGRAM_PACKAGE_URL",
  "MEDIAGRAM_PACKAGE_KEY",
];

let saved: Record<string, string | undefined>;

beforeEach(() => {
  saved = Object.fromEntries(TOUCHED.map((name) => [name, process.env[name]]));
  for (const name of TOUCHED) delete process.env[name];
  Object.assign(process.env, REQUIRED);
});
afterEach(() => {
  for (const [name, value] of Object.entries(saved)) {
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
});

describe("where the catalog comes from", () => {
  test("without a package, a local index is required", () => {
    expect(() => load()).toThrow(/MEDIAGRAM_LIBRARY_DB/);
  });

  test("with a package, it is not", () => {
    process.env.MEDIAGRAM_PACKAGE_URL = "https://packages.example.test/mediagram";
    process.env.MEDIAGRAM_PACKAGE_KEY = "a".repeat(44);

    const config = load();

    expect(config.libraryDb).toBeNull();
    expect(config.packageUrl).toBe("https://packages.example.test/mediagram");
  });

  test("a package URL without its key still needs the local index", () => {
    // Half a package configuration reads the local index, so it has to have
    // one; silently serving nothing would be worse.
    process.env.MEDIAGRAM_PACKAGE_URL = "https://packages.example.test/mediagram";

    expect(() => load()).toThrow(/MEDIAGRAM_LIBRARY_DB/);
  });

  test("a local index alongside a package is kept, not discarded", () => {
    // It is ignored while the package is configured, but removing the
    // package should not then require finding the path again.
    process.env.MEDIAGRAM_LIBRARY_DB = "/srv/library.db";
    process.env.MEDIAGRAM_PACKAGE_URL = "https://packages.example.test/mediagram";
    process.env.MEDIAGRAM_PACKAGE_KEY = "a".repeat(44);

    expect(load().libraryDb).toBe("/srv/library.db");
  });
});
