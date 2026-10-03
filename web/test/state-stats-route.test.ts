/** `GET /api/profiles/{profileId}/stats`, through the router that serves it. */

import { afterEach, expect, setSystemTime, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { ByteSource } from "../src/http/stream";
import { createRouter } from "../src/routes";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const dirs: string[] = [];
function routed() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-stats-route-"));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  const route = createRouter({ db: emptyIndex(), source: NO_BYTES, state });
  const ask = (method: string, path = `/api/profiles/${me}/stats`) => route({ method, path, range: null });
  return { state, me, ask };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const bodyOf = (response: { body: unknown }) => JSON.parse(new TextDecoder().decode(response.body as Uint8Array));

test("answers the profile's summary, as of today on this server's calendar", async () => {
  const { state, me, ask } = routed();
  setSystemTime(new Date(2026, 9, 3, 21, 0, 0));
  state.setProgress(me, "01FILM", 100, 7200);
  setSystemTime(new Date(2026, 9, 3, 21, 0, 10));
  state.setProgress(me, "01FILM", 110, 7200);

  const response = await ask("GET");
  expect(response.status).toBe(200);
  expect(response.headers["content-type"]).toBe("application/json");
  const summary = bodyOf(response);
  expect(summary).toMatchObject({ weekSeconds: 10, monthSeconds: 10, allSeconds: 10 });
  expect(summary.last30.at(-1)).toEqual({ day: "2026-10-03", seconds: 10 });
  expect(summary.history).toEqual([
    { kind: "started", setId: "01FILM", at: new Date(2026, 9, 3, 21, 0, 0).getTime(), seconds: 10 },
  ]);
});

test("an unknown profile is a 404, a write is a 405, a HEAD has no body", async () => {
  const { ask } = routed();
  expect((await ask("GET", "/api/profiles/nobody/stats")).status).toBe(404);
  expect((await ask("POST")).status).toBe(405);
  expect((await ask("DELETE")).status).toBe(405);
  const head = await ask("HEAD");
  expect(head.status).toBe(200);
  expect(head.body).toBeNull();
});
