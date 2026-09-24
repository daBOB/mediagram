import { expect, test } from "bun:test";
import { staticResponse } from "../src/http/static-files";

test("the browser's module graph is served at its actual relative URLs", () => {
  const pending = ["/app.js"];
  const visited = new Set<string>();
  const parser = new Bun.Transpiler({ loader: "js" });
  while (pending.length > 0) {
    const path = pending.pop()!;
    if (visited.has(path)) continue;
    visited.add(path);
    const response = staticResponse({ method: "GET", path, range: null });
    expect(response.status, path).toBe(200);
    expect(response.headers["content-type"], path).toContain("javascript");
    if (!(response.body instanceof Uint8Array)) throw new Error(`Missing module bytes: ${path}`);
    expect(response.headers["content-length"], path).toBe(String(response.body.byteLength));
    for (const dependency of parser.scanImports(new TextDecoder().decode(response.body))) {
      expect(dependency.path.startsWith(".") || dependency.path.startsWith("/"), dependency.path).toBe(true);
      pending.push(new URL(dependency.path, `http://player.local${path}`).pathname);
    }
  }
  // Cover each entry used by app routing plus the absolute installed vendor
  // endpoint, which a bundle-only check would treat as an external module.
  expect(visited.has("/lib/playback/player.js")).toBe(true);
  expect(visited.has("/lib/catalog/home-view.js")).toBe(true);
  expect(visited.has("/lib/status/status-view.js")).toBe(true);
  expect(visited.has("/lib/hls.mjs")).toBe(true);
});
