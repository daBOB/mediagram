import { expect, test } from "bun:test";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readCacheConfig, sign } from "./cache-upload";

const TOKEN = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

test("signs the published blob vector exactly", () => {
  // docs/running-the-player.md, the blob vector.
  expect(sign(TOKEN, "PUT", "/v1/art/w342/abc.jpg", new TextEncoder().encode("hello"))).toBe(
    "e1919b6ff65a768f6d2bc1b80f88296901cbb04fe0a0f74736e2ff0e8b7cdf65",
  );
});

test("reads the cache server and token from the mediagram config", () => {
  const dir = mkdtempSync(join(tmpdir(), "preisfrage-"));
  const file = join(dir, "config.toml");
  writeFileSync(file, `channel = "x"\ncache_server = "http://10.0.0.5:7788/"\ncache_server_token = "${TOKEN}"\n`);
  expect(readCacheConfig(file)).toEqual({ server: "http://10.0.0.5:7788", token: TOKEN });
  writeFileSync(file, `channel = "x"\ncache_server = ""\n`);
  expect(() => readCacheConfig(file)).toThrow(/cache_server/);
  expect(() => readCacheConfig(join(dir, "missing.toml"))).toThrow(/config/);
});
