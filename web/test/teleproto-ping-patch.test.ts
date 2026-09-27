/**
 * Guards the one-constant patch to `teleproto`'s keepalive loop.
 *
 * The loop always sleeps a full `PING_INTERVAL` between pings, so if its own
 * "just woke from sleep" threshold sits below that, the wake-up branch fires
 * on every routine ping, not just a genuine gap — and that branch dispatches
 * a synthetic `UpdateConnectionState.connected` on success. `watchReconnects`
 * (`measured-client.ts`) counts every one of those as a reconnect, so the
 * System page's reconnect count climbs on its own at idle. If a future
 * `teleproto` upgrade drops `web/patches/teleproto@1.229.0.patch`, this test
 * fails with a message pointing back here instead of the count silently
 * lying again.
 */

import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const source = readFileSync(Bun.resolveSync("teleproto/client/updates/dispatch.js", import.meta.dir), "utf8");

function constant(name: string): number {
  const match = new RegExp(`const ${name} = (\\d+);`).exec(source);
  if (!match) throw new Error(`${name} not found in teleproto's dispatch.js — did its source change shape?`);
  return Number(match[1]);
}

test("the wake-up threshold only fires on a genuine gap, not every ping cycle", () => {
  const pingInterval = constant("PING_INTERVAL");
  const wakeUpThreshold = constant("PING_INTERVAL_TO_WAKE_UP");
  expect(wakeUpThreshold).toBeGreaterThan(pingInterval);
  // The unpatched upstream value (5000) sits below any plausible PING_INTERVAL;
  // failing here means the patch was dropped, e.g. by a fresh `bun install`
  // that didn't reapply `web/patches/teleproto@1.229.0.patch`.
  expect(wakeUpThreshold).not.toBe(5000);
});
