import { describe, expect, test } from "bun:test";
import { mkdtemp, rm, symlink, unlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createStatusRouter } from "../src/status/routes";
import { dirBytes } from "../src/status/dir-bytes";
import { PlaybackReports } from "../src/status/playback-reports";
import type { StartupFacts } from "../src/status/facts";
import type { PlayerRequest } from "../src/http/contracts";

const facts: StartupFacts = {
  catalog: {
    origin: "local",
    publishedAt: null,
    refresh: null,
    reason: null,
    schema: 6,
    sets: 12,
    posters: 3,
  },
  encoder: { name: "libx264", kind: "software", device: null },
  transcodeDir: "/tmp/transcode",
  cache: { dir: "/tmp/cache", budget: 1024, readahead: 2 },
  state: { remembered: false, path: null },
  startedAt: 0,
  runtime: { bun: "1.4.2" },
};

const live = () => ({
  cacheHits: 1,
  cacheMisses: 1,
  cacheEvicted: 0,
  fetchedBytes: 10,
  transcodes: { running: 0, capacity: 4, started: { encode: 0, copy: 0, hevcCopy: 0 }, sessions: [] },
  telegramConnected: true,
  link: { dcs: [], flood: { count: 0, totalSeconds: 0 }, reconnects: 0 },
  playback: [],
  failedReads: 0,
  host: { rssBytes: 1024, heapBytes: 512, loopLagMs: null, disks: [] },
});

/** These tests exercise `GET /api/status`; the playback POST path has its own tests below. */
const noPlayback = { put: () => {} };

function ask(over: Partial<PlayerRequest> = {}): PlayerRequest {
  return { method: "GET", path: "/api/status", range: null, client: "127.0.0.1", ...over };
}

describe("the status route", () => {
  test("a failed transcode scan retains its measured total and recovers on retry", async () => {
    const root = await mkdtemp(join(tmpdir(), "mediagram-status-disk-"));
    try {
      let now = 0;
      const route = createStatusRouter({ facts, live, transcodeBytes: () => dirBytes(root), now: () => now, playback: noPlayback });
      const bytes = async () => {
        const response = await route(ask());
        if (!(response?.body instanceof Uint8Array)) throw new Error("Missing status response");
        return JSON.parse(new TextDecoder().decode(response.body)).transcodes.heldBytes;
      };
      await writeFile(join(root, "segment"), new Uint8Array(80));
      expect(await bytes()).toBe(80);
      await writeFile(join(root, "segment"), new Uint8Array(200));
      await symlink("loop", join(root, "loop"));
      now = 20_000;
      expect(await bytes()).toBe(80);
      await unlink(join(root, "loop"));
      expect(await bytes()).toBe(200);
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
  test("answers a viewer on this machine", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    const answer = await route(ask());
    expect(answer?.status).toBe(200);
    expect(answer?.headers["content-type"]).toBe("application/json");
    expect(answer?.headers["cache-control"]).toBe("no-store");
  });

  test("answers a viewer elsewhere on the local network", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(ask({ client: "192.168.1.40" })))?.status).toBe(200);
  });

  test("answers a viewer on the tailnet, whose phone this also is", async () => {
    // The link is an uplink, so playback still converts; the panel is about
    // who is asking, and a Tailscale peer was admitted to the tailnet first.
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(ask({ client: "100.95.219.10" })))?.status).toBe(200);
  });

  test("is a 404 from outside, not a 403: there is nothing to confirm", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    const answer = await route(ask({ client: "203.0.113.9" }));
    expect(answer?.status).toBe(404);
    expect(answer?.body).toBe(null);
  });

  test("tells a caller from outside nothing a wrong method would have", async () => {
    // The address is checked first, so "wrong method here" and "nothing here"
    // read identically to anyone who should not know which it is.
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(ask({ client: "203.0.113.9", method: "POST" })))?.status).toBe(404);
  });

  test("treats an unknown address as one from outside", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(ask({ client: undefined })))?.status).toBe(404);
  });

  test("refuses a write from a local caller, which is a method it has not got", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(ask({ method: "POST" })))?.status).toBe(405);
  });

  test("answers HEAD with the same framing and no body", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    const head = await route(ask({ method: "HEAD" }));
    const get = await route(ask());
    expect(head?.status).toBe(200);
    expect(head?.body).toBe(null);
    expect(head?.headers["content-length"]).toBe(get?.headers["content-length"]);
  });

  test("leaves every other path to the routes that own it", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect(await route(ask({ path: "/api/sets" }))).toBe(null);
    expect(await route(ask({ path: "/api/status/extra" }))).toBe(null);
  });

  test("scans the cache once for a burst of polls rather than once each", async () => {
    let scans = 0;
    let clock = 0;
    const route = createStatusRouter({
      facts,
      live,
      heldBytes: async () => {
        scans += 1;
        return 512;
      },
      now: () => clock,
      playback: noPlayback,
    });

    await Promise.all([route(ask()), route(ask()), route(ask())]);
    expect(scans).toBe(1);

    // Still inside the window: the reading is reused rather than retaken.
    clock = 14_000;
    await route(ask());
    expect(scans).toBe(1);

    // Past it: worth measuring again.
    clock = 30_000;
    await route(ask());
    expect(scans).toBe(2);
  });

  test("reports no cache size rather than failing when the scan does", async () => {
    const route = createStatusRouter({
      facts,
      live,
      heldBytes: async () => {
        throw new Error("cache directory vanished");
      },
      playback: noPlayback,
    });
    const answer = await route(ask());
    expect(answer?.status).toBe(200);
  });

  test("says nothing about cache size when there is no cache to measure", async () => {
    const route = createStatusRouter({ facts: { ...facts, cache: null }, live, playback: noPlayback });
    const answer = await route(ask());
    const body = JSON.parse(new TextDecoder().decode(answer?.body as Uint8Array));
    expect(body.cache).toBe(null);
  });
});

const REPORT = {
  viewer: "11111111-1111-4111-8111-111111111111",
  setId: "01SET",
  title: "A Film",
  mode: "direct",
  videoCodec: "h264",
  audioCodec: "aac",
  bitrateBits: 8_200_000,
  ahead: 72,
  health: "ok",
  fillRate: 1.1,
  dropped: 0,
  frames: 41_200,
  paused: false,
  held: false,
};

function post(over: Partial<PlayerRequest> = {}): PlayerRequest {
  return {
    method: "POST",
    path: "/api/status/playback",
    range: null,
    client: "127.0.0.1",
    origin: "http://127.0.0.1:8770",
    host: "127.0.0.1:8770",
    contentType: "application/json",
    body: JSON.stringify(REPORT),
    ...over,
  };
}

describe("POST /api/status/playback", () => {
  test("an outsider gets 404, the same as a GET would", async () => {
    const puts: unknown[] = [];
    const route = createStatusRouter({ facts, live, playback: { put: (report) => puts.push(report) } });
    const answer = await route(post({ client: "203.0.113.9" }));
    expect(answer?.status).toBe(404);
    expect(puts).toEqual([]);
  });

  test("a household caller's write is accepted and stored", async () => {
    const puts: unknown[] = [];
    const route = createStatusRouter({ facts, live, playback: { put: (report) => puts.push(report) } });
    const answer = await route(post());
    expect(answer?.status).toBe(204);
    expect(puts).toHaveLength(1);
  });

  test("a cross-origin write is refused, the same guard the state routes share", async () => {
    const puts: unknown[] = [];
    const route = createStatusRouter({ facts, live, playback: { put: (report) => puts.push(report) } });
    const answer = await route(post({ origin: "https://elsewhere.example" }));
    expect(answer?.status).toBe(403);
    expect(puts).toEqual([]);
  });

  test("a form post without a JSON content type is refused", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    const answer = await route(post({ contentType: "text/plain" }));
    expect(answer?.status).toBe(415);
  });

  test("an unusable body is a 400, not a crash", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    const answer = await route(post({ body: JSON.stringify({ mode: "not-a-mode" }) }));
    expect(answer?.status).toBe(400);
  });

  test("any method but POST is refused", async () => {
    const route = createStatusRouter({ facts, live, playback: noPlayback });
    expect((await route(post({ method: "GET", body: null })))?.status).toBe(405);
  });

  test("a stored report appears in the next GET, without the viewer id", async () => {
    // The real store, so the POST-then-GET round trip is genuine rather than
    // asserting on a mock told what to say.
    const store = new PlaybackReports();
    const route = createStatusRouter({ facts, live: () => ({ ...live(), playback: store.list() }), playback: store });
    await route(post());
    const answer = await route(ask());
    const body = JSON.parse(new TextDecoder().decode(answer?.body as Uint8Array));
    expect(body.playback).toHaveLength(1);
    expect(body.playback[0].setId).toBe("01SET");
    expect(JSON.stringify(body.playback)).not.toContain(REPORT.viewer);
  });
});
