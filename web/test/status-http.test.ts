import { describe, expect, test } from "bun:test";
import { mkdtemp, rm, symlink, unlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createStatusRouter } from "../src/status/routes";
import { dirBytes } from "../src/status/dir-bytes";
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
};

const live = () => ({
  cacheHits: 1,
  cacheMisses: 1,
  cacheEvicted: 0,
  fetchedBytes: 10,
  transcodes: { running: 0, capacity: 4, sessions: [] },
  telegramConnected: true,
  failedReads: 0,
  memoryBytes: 1024,
});

function ask(over: Partial<PlayerRequest> = {}): PlayerRequest {
  return { method: "GET", path: "/api/status", range: null, client: "127.0.0.1", ...over };
}

describe("the status route", () => {
  test("a failed transcode scan retains its measured total and recovers on retry", async () => {
    const root = await mkdtemp(join(tmpdir(), "mediagram-status-disk-"));
    try {
      let now = 0;
      const route = createStatusRouter({ facts, live, transcodeBytes: () => dirBytes(root), now: () => now });
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
    const route = createStatusRouter({ facts, live });
    const answer = await route(ask());
    expect(answer?.status).toBe(200);
    expect(answer?.headers["content-type"]).toBe("application/json");
    expect(answer?.headers["cache-control"]).toBe("no-store");
  });

  test("answers a viewer elsewhere on the local network", async () => {
    const route = createStatusRouter({ facts, live });
    expect((await route(ask({ client: "192.168.1.40" })))?.status).toBe(200);
  });

  test("answers a viewer on the tailnet, whose phone this also is", async () => {
    // The link is an uplink, so playback still converts; the panel is about
    // who is asking, and a Tailscale peer was admitted to the tailnet first.
    const route = createStatusRouter({ facts, live });
    expect((await route(ask({ client: "100.95.219.10" })))?.status).toBe(200);
  });

  test("is a 404 from outside, not a 403: there is nothing to confirm", async () => {
    const route = createStatusRouter({ facts, live });
    const answer = await route(ask({ client: "203.0.113.9" }));
    expect(answer?.status).toBe(404);
    expect(answer?.body).toBe(null);
  });

  test("tells a caller from outside nothing a wrong method would have", async () => {
    // The address is checked first, so "wrong method here" and "nothing here"
    // read identically to anyone who should not know which it is.
    const route = createStatusRouter({ facts, live });
    expect((await route(ask({ client: "203.0.113.9", method: "POST" })))?.status).toBe(404);
  });

  test("treats an unknown address as one from outside", async () => {
    const route = createStatusRouter({ facts, live });
    expect((await route(ask({ client: undefined })))?.status).toBe(404);
  });

  test("refuses a write from a local caller, which is a method it has not got", async () => {
    const route = createStatusRouter({ facts, live });
    expect((await route(ask({ method: "POST" })))?.status).toBe(405);
  });

  test("answers HEAD with the same framing and no body", async () => {
    const route = createStatusRouter({ facts, live });
    const head = await route(ask({ method: "HEAD" }));
    const get = await route(ask());
    expect(head?.status).toBe(200);
    expect(head?.body).toBe(null);
    expect(head?.headers["content-length"]).toBe(get?.headers["content-length"]);
  });

  test("leaves every other path to the routes that own it", async () => {
    const route = createStatusRouter({ facts, live });
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
    });
    const answer = await route(ask());
    expect(answer?.status).toBe(200);
  });

  test("says nothing about cache size when there is no cache to measure", async () => {
    const route = createStatusRouter({ facts: { ...facts, cache: null }, live });
    const answer = await route(ask());
    const body = JSON.parse(new TextDecoder().decode(answer?.body as Uint8Array));
    expect(body.cache).toBe(null);
  });
});
