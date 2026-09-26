import { describe, expect, test } from "bun:test";
import { errors } from "teleproto";
import { LinkStats } from "../src/telegram/link-stats";

describe("link stats", () => {
  test("reports nothing requested yet for a DC that has never been asked", () => {
    expect(new LinkStats().snapshot().dcs).toEqual([]);
  });

  test("counts requests, errors and bytes per DC", () => {
    const stats = new LinkStats();
    stats.request(2, 100, 1_000_000);
    stats.request(2, 200, 500_000);
    stats.failed(2, new Error("boom"));
    stats.request(4, 50, 200_000);

    const [dc2, dc4] = stats.snapshot().dcs;
    expect(dc2).toMatchObject({ dc: 2, requests: 2, errors: 1, bytes: 1_500_000 });
    expect(dc4).toMatchObject({ dc: 4, requests: 1, errors: 0, bytes: 200_000 });
  });

  test("sorts DC rows by DC number", () => {
    const stats = new LinkStats();
    stats.request(5, 1, 1);
    stats.request(1, 1, 1);
    stats.request(3, 1, 1);
    expect(stats.snapshot().dcs.map((row) => row.dc)).toEqual([1, 3, 5]);
  });

  test("computes p50 and p95 by nearest rank over known samples", () => {
    const stats = new LinkStats();
    // 1..100 ms, in order: p50 is the 50th value, p95 the 95th.
    for (let ms = 1; ms <= 100; ms++) stats.request(2, ms, 0);
    const [dc] = stats.snapshot().dcs;
    expect(dc?.p50Ms).toBe(50);
    expect(dc?.p95Ms).toBe(95);
  });

  test("keeps only the last 256 samples per DC, wrapping the ring", () => {
    const stats = new LinkStats();
    // 300 requests at 1ms, then 12 at 1000ms: the ring holds the last 256,
    // so the slow dozen are a visible fraction once they crowd out the fast ones.
    for (let i = 0; i < 300; i++) stats.request(2, 1, 0);
    for (let i = 0; i < 12; i++) stats.request(2, 1000, 0);
    const [dc] = stats.snapshot().dcs;
    // 256 samples held: 244 at 1ms and 12 at 1000ms. p95 rank = ceil(0.95*256) = 244th value.
    expect(dc?.p95Ms).toBe(1);
    expect(dc?.p50Ms).toBe(1);
  });

  test("counts a flood wait reported only through the sleeping log line", () => {
    const stats = new LinkStats();
    stats.flood(3);
    stats.flood(5);
    expect(stats.snapshot().flood).toEqual({ count: 2, totalSeconds: 8 });
  });

  test("counts a thrown flood-wait error as both a failure and a flood", () => {
    const stats = new LinkStats();
    const error = new errors.FloodWaitError({ request: undefined, capture: 90 });
    stats.failed(2, error);
    const snapshot = stats.snapshot();
    expect(snapshot.dcs[0]).toMatchObject({ dc: 2, errors: 1 });
    expect(snapshot.flood).toEqual({ count: 1, totalSeconds: 90 });
  });

  test("does not count an ordinary failure as a flood wait", () => {
    const stats = new LinkStats();
    stats.failed(2, new Error("not a flood"));
    expect(stats.snapshot().flood).toEqual({ count: 0, totalSeconds: 0 });
  });

  test("counts reconnects independently of any DC", () => {
    const stats = new LinkStats();
    stats.reconnect();
    stats.reconnect();
    expect(stats.snapshot().reconnects).toBe(2);
  });
});
