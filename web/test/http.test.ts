/**
 * The HTTP contract of the player backend, over a real socket.
 *
 * These assertions are about what goes on the wire, so they are made against
 * a bound listener rather than a handler in isolation. `Content-Length` on a
 * streamed body is exactly the kind of header that is present in the code and
 * absent from the response — and ffmpeg cannot seek without it.
 *
 * Telegram is replaced by a source that serves a known synthetic file, so a
 * wrong byte shows up as a wrong byte.
 */

import type { Database } from "bun:sqlite";
import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { startServer, type RunningServer } from "../src/server";
import type { ByteSource, HlsFile, HlsServer } from "../src/routes";
import { rawRequest } from "./raw-http";
import { emptyIndex } from "./index-fixture";
import { ALIGN, type Step } from "../src/range";
import type { PartLocation } from "../src/catalog";
import type { SessionSpec } from "../src/transcode/registry";

const SET = "01SET0000000000000000009";
const P0 = 2 * ALIGN + 100;
const P1 = ALIGN + 50;
const TOTAL = P0 + P1;

/** A file whose byte at offset i is derived from i, so a misplaced byte shows. */
function synthetic(len: number): Uint8Array {
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) bytes[i] = i % 251;
  return bytes;
}

const FILE = synthetic(TOTAL);

/** Resolves planned reads against bytes we hold, as Telegram would. */
class FakeSource implements ByteSource {
  stream(locations: PartLocation[], steps: Step[], _setId: string): ReadableStream<Uint8Array> {
    const chunks: Uint8Array[] = [];
    for (const step of steps) {
      const span = locations.find((l) => l.span.idx === step.partIdx)!.span;
      const from = span.off + step.offset + step.headDrop;
      chunks.push(FILE.subarray(from, from + step.take));
    }
    return new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(chunk);
        controller.close();
      },
    });
  }
}

function index(): Database {
  const db = emptyIndex();
  db.run("INSERT INTO assets VALUES (?, 'summary', '', 'Worum es geht.')", [SET]);
  db.run("INSERT INTO assets VALUES (?, 'subtitle', 'deu', 'WEBVTT\n\nhallo')", [SET]);
  // Columns named, not positional: a positional insert breaks silently the
  // next time the schema gains one.
  db.run(
    `INSERT INTO sets(set_id, kind, title, year, container, vcodec, acodec,
                      duration, total, part_count, status, created_at, spec_version)
     VALUES (?, 'movie', 'The Matrix', 1999, 'mkv', 'hevc', 'ac3', 8160, ?, 2, 'complete', 1700000000, 3)`,
    [SET, TOTAL],
  );
  const part = `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
                VALUES (?, ?, ?, ?, -1001, ?, 'done')`;
  db.run(part, [SET, 0, 0, P0, 100]);
  db.run(part, [SET, 1, P0, P1, 101]);
  return db;
}

let server: RunningServer;

beforeAll(async () => {
  server = await startServer({ db: index(), source: new FakeSource() });
});

afterAll(() => server.close());

/**
 * Every route is read straight off the socket. `fetch` swallows
 * `Content-Length` on a streamed body, and that header is the thing under
 * test — see `raw-http.ts`.
 */
function request(path: string, options?: { method?: string; range?: string }) {
  return rawRequest(server.port, path, options);
}

function lengthOf(response: { status: number; headers: Map<string, string> }): number {
  const header = response.headers.get("content-length");
  if (header === undefined) throw new Error(`no Content-Length on ${response.status}`);
  return Number(header);
}

describe("the catalog route", () => {
  test("lists what is playable", async () => {
    const response = await request("/api/sets");
    expect(response.status).toBe(200);

    const listed = JSON.parse(new TextDecoder().decode(response.body));
    expect(listed).toHaveLength(1);
    expect(listed[0].setId).toBe(SET);
    expect(listed[0].title).toBe("The Matrix");
    expect(listed[0].total).toBe(TOTAL);
    expect(listed[0].vcodec).toBe("hevc");
    expect(lengthOf(response)).toBe(response.body.byteLength);
  });

  /**
   * The browser is the one thing in this system that must never learn where
   * the bytes live. The package format encrypts exactly these fields.
   */
  test("tells the browser nothing about the channel or its messages", async () => {
    const body = new TextDecoder().decode((await request("/api/sets")).body);

    expect(body).not.toContain("1001");
    expect(body).not.toContain("chatId");
    expect(body).not.toContain("messageId");
  });
});

describe("the stream route", () => {
  test("a request without a range streams the whole file", async () => {
    const response = await request(`/api/sets/${SET}/stream`);

    expect(response.status).toBe(200);
    expect(response.headers.get("accept-ranges")).toBe("bytes");
    expect(lengthOf(response)).toBe(TOTAL);
    expect(response.body).toEqual(FILE);
  });

  test("an open-ended range is partial content with the whole remainder", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "bytes=0-" });

    expect(response.status).toBe(206);
    expect(response.headers.get("content-range")).toBe(`bytes 0-${TOTAL - 1}/${TOTAL}`);
    expect(lengthOf(response)).toBe(TOTAL);
    expect(response.body).toEqual(FILE);
  });

  /** The case that only exists because a file is split across messages. */
  test("a range across the part boundary returns exactly those bytes", async () => {
    const start = P0 - 1000;
    const end = P0 + 999;
    const response = await request(`/api/sets/${SET}/stream`, { range: `bytes=${start}-${end}` });

    expect(response.status).toBe(206);
    expect(lengthOf(response)).toBe(2000);
    expect(response.headers.get("content-range")).toBe(`bytes ${start}-${end}/${TOTAL}`);
    expect(response.body).toEqual(FILE.subarray(start, end + 1));
  });

  test("a suffix range returns the end of the file", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "bytes=-500" });

    expect(response.status).toBe(206);
    expect(lengthOf(response)).toBe(500);
    expect(response.body).toEqual(FILE.subarray(TOTAL - 500));
  });

  test("an unsatisfiable range is refused with the total size", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "bytes=99999999999-" });

    expect(response.status).toBe(416);
    expect(response.headers.get("content-range")).toBe(`bytes */${TOTAL}`);
    expect(lengthOf(response)).toBe(0);
  });

  test("a range in units we do not speak is ignored, not refused", async () => {
    // RFC 9110 14.2: an origin server MUST ignore a Range header field that
    // contains a range unit it does not understand. Ignoring it means serving
    // the whole representation, which every client can use; refusing it means
    // breaking playback over a header the client did not need us to honour.
    const response = await request(`/api/sets/${SET}/stream`, { range: "kilometres=0-99" });

    expect(response.status).toBe(200);
    expect(lengthOf(response)).toBe(TOTAL);
    expect(response.headers.get("content-range")).toBeUndefined();
  });

  test("a byte range we cannot parse is ignored the same way", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "bytes=abc-def" });

    expect(response.status).toBe(200);
    expect(lengthOf(response)).toBe(TOTAL);
  });

  test("a multi-range request is refused rather than answered partly", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "bytes=0-99,200-299" });

    expect(response.status).toBe(416);
  });

  /** What a player asks before it asks for bytes. */
  test("HEAD reports the size and that ranges are supported", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { method: "HEAD" });

    expect(response.status).toBe(200);
    expect(lengthOf(response)).toBe(TOTAL);
    expect(response.headers.get("accept-ranges")).toBe("bytes");
    expect(response.body.length).toBe(0);
  });

  test("a set that is not playable is not found", async () => {
    const response = await request("/api/sets/01NOSUCHSET00000000000001/stream");

    expect(response.status).toBe(404);
    expect(lengthOf(response)).toBe(0);
  });

  test("an unknown path is not found", async () => {
    expect((await request("/nope")).status).toBe(404);
  });
});

describe("assets", () => {
  test("a summary is served as text a page can render", async () => {
    const response = await request(`/api/sets/${SET}/summary`);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/");
    expect(new TextDecoder().decode(response.body)).toBe("Worum es geht.");
    expect(lengthOf(response)).toBe(response.body.byteLength);
  });

  /** A <track> element fetches this URL directly, so the type must be right. */
  test("a subtitle is served as WebVTT", async () => {
    const response = await request(`/api/sets/${SET}/subtitles/deu.vtt`);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/vtt");
    expect(new TextDecoder().decode(response.body)).toContain("WEBVTT");
  });

  test("a language that is not there is not found", async () => {
    expect((await request(`/api/sets/${SET}/subtitles/eng.vtt`)).status).toBe(404);
  });

  test("the catalog says what a set has, so the page need not ask", async () => {
    const listed = JSON.parse(new TextDecoder().decode((await request("/api/sets")).body));

    expect(listed[0].hasSummary).toBe(true);
    expect(listed[0].subtitles).toEqual(["deu"]);
  });

  /** A language is a label, not a path: it must not reach the filesystem. */
  test("a language that looks like a path is refused", async () => {
    for (const lang of ["..%2f..%2fetc", "a/b", "%2e%2e"]) {
      const response = await request(`/api/sets/${SET}/subtitles/${lang}.vtt`);
      expect([400, 404]).toContain(response.status);
    }
  });
});

describe("the page", () => {
  test("the root serves a page, not a 404", async () => {
    const response = await request("/");

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/html");
    expect(lengthOf(response)).toBe(response.body.byteLength);
    expect(new TextDecoder().decode(response.body)).toContain("<title>mediagram</title>");
  });

  test("the page's script is served", async () => {
    const response = await request("/app.js");

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("javascript");
  });

  /** A malformed escape is a path that does not exist, not a server fault. */
  test("an undecodable path is a 404", async () => {
    for (const path of ["/%zz", "/%", "/a%2"]) {
      const response = await request(path);
      expect(response.status).toBe(404);
    }
  });

  /** A path that climbs out of the public directory must not be served. */
  test("a traversal attempt is refused", async () => {
    for (const path of ["/../src/config.ts", "/..%2fsrc%2fconfig.ts", "/../../.env"]) {
      const response = await request(path);
      expect([400, 404]).toContain(response.status);
    }
  });
});

/**
 * A transcode that is running and has produced nothing yet, which is the
 * uninteresting case. A test overrides only the method it is about, so a
 * method added to `HlsServer` costs one edit here rather than one per stub.
 */
function fakeHls(over: Partial<HlsServer> = {}): HlsServer {
  return {
    begin: async () => `/hls/${"a".repeat(16)}/index.m3u8`,
    file: async () => "not-ready",
    end: async () => {},
    ...over,
  };
}

describe("the HLS routes", () => {
  /**
   * A player asking for a playlist must not get an empty one: hls.js treats
   * a playlist with no segments as a failure rather than as "wait".
   */
  test("a playlist is not served until it has a segment", async () => {
    const response = await request(`/hls/${"0".repeat(16)}/index.m3u8`);

    expect([404, 503]).toContain(response.status);
  });

  /** A segment name reaches a path, so it must be inert. */
  test("a segment name that looks like a path is refused", async () => {
    for (const name of ["..%2f..%2fetc%2fpasswd", "a%2fb.ts", "....%2f.env"]) {
      const response = await request(`/hls/${"0".repeat(16)}/${name}`);
      expect([400, 404]).toContain(response.status);
    }
  });

  test("a session id that is not a session id is refused", async () => {
    const response = await request("/hls/..%2f..%2fetc/index.m3u8");

    expect([400, 404]).toContain(response.status);
  });

  test("every HLS response states its length", async () => {
    const response = await request(`/hls/${"0".repeat(16)}/index.m3u8`);

    expect(lengthOf(response)).toBe(response.body.byteLength);
  });
});

/**
 * A player retries a 503 and gives up on a 404, so the two have to mean what
 * they say. A session still starting is worth retrying; one that was stopped
 * or reaped is never coming back, and a player told to wait for it waits
 * forever instead of falling back to direct play.
 */
describe("telling a starting transcode from a gone one", () => {
  const LIVE = "b".repeat(16);
  const GONE = "c".repeat(16);
  let server: RunningServer;

  // LIVE is running but has written nothing yet — the window between
  // starting and the first segment. GONE is no session at all.
  const hls = fakeHls({
    begin: async () => `/hls/${LIVE}/index.m3u8`,
    file: async (sessionId: string): Promise<HlsFile> => (sessionId === LIVE ? "not-ready" : "gone"),
  });

  beforeAll(async () => {
    server = await startServer({ db: index(), source: new FakeSource(), hls });
  });
  afterAll(async () => {
    await server.close();
  });

  test("a session that exists but has written nothing yet says wait", async () => {
    const response = await rawRequest(server.port, `/hls/${LIVE}/index.m3u8`);

    expect(response.status).toBe(503);
  });

  test("a session nobody is running says gone, so the player stops asking", async () => {
    const response = await rawRequest(server.port, `/hls/${GONE}/index.m3u8`);

    expect(response.status).toBe(404);
  });
});

/**
 * A transcode holds the hardware encoder, so a viewer who stops watching has
 * to be able to say so. Waiting for the idle reaper means a machine with one
 * encoder runs two of them for minutes after a seek.
 */
describe("releasing a transcode", () => {
  const SESSION = "a".repeat(16);
  let ended: string[] = [];
  let hlsServer: RunningServer;

  const hls = fakeHls({
    begin: async () => `/hls/${SESSION}/index.m3u8`,
    end: async (sessionId: string) => {
      ended.push(sessionId);
    },
  });

  beforeAll(async () => {
    hlsServer = await startServer({ db: index(), source: new FakeSource(), hls });
  });
  afterAll(async () => {
    await hlsServer.close();
  });

  test("DELETE on a session stops it", async () => {
    ended = [];
    const response = await rawRequest(hlsServer.port, `/hls/${SESSION}`, { method: "DELETE" });

    expect(response.status).toBe(204);
    expect(ended).toEqual([SESSION]);
  });

  test("a session id that is not one is refused without reaching the registry", async () => {
    ended = [];
    for (const id of ["..%2f..%2fetc", "short", "A".repeat(16)]) {
      const response = await rawRequest(hlsServer.port, `/hls/${id}`, { method: "DELETE" });
      expect([400, 404]).toContain(response.status);
    }

    expect(ended).toEqual([]);
  });

  test("a conversion that cannot start says so, and is not a server error", async () => {
    const failing = fakeHls({
      begin: async () => {
        throw new Error("the conversion produced no segment within 45s");
      },
    });
    const server = await startServer({ db: index(), source: new FakeSource(), hls: failing });
    try {
      const response = await rawRequest(server.port, `/api/sets/${SET}/transcode`);

      expect(response.status).toBe(503);
      expect(new TextDecoder().decode(response.body)).toContain("no segment");
    } finally {
      await server.close();
    }
  });

  /**
   * A player that has measured a link too slow for the default asks for an
   * encode that fits. The number is a hint from a browser, so it is clamped
   * rather than trusted: above the configured cap it would saturate the very
   * uplink the cap exists to protect, and below a floor it would produce
   * something not worth watching.
   */
  test("a requested bitrate is honoured between the floor and the configured cap", async () => {
    const asked: number[] = [];
    const recording = fakeHls({
      begin: async ({ maxrateBits }: SessionSpec) => {
        asked.push(maxrateBits);
        return `/hls/${SESSION}/index.m3u8`;
      },
    });
    const server = await startServer({
      db: index(),
      source: new FakeSource(),
      hls: recording,
      maxBitrate: 8_000_000,
    });
    try {
      for (const rate of ["3000000", "99000000", "1", "", "abc", "-5", "Infinity"]) {
        await rawRequest(server.port, `/api/sets/${SET}/transcode?maxrate=${rate}`);
      }

      // Asked for, clamped, clamped, clamped, then the default four times.
      expect(asked[0]).toBe(3_000_000);
      expect(asked[1]).toBe(8_000_000);
      expect(asked[2]).toBeGreaterThan(1);
      for (const rate of asked) {
        expect(rate).toBeLessThanOrEqual(8_000_000);
        expect(rate).toBeGreaterThan(0);
        expect(Number.isFinite(rate)).toBe(true);
      }
      expect(asked.slice(3)).toEqual([8_000_000, 8_000_000, 8_000_000, 8_000_000]);
    } finally {
      await server.close();
    }
  });

  test("a seek that is not a number of seconds never reaches ffmpeg", async () => {
    const asked: number[] = [];
    const recording = fakeHls({
      begin: async ({ seekSeconds: seek }: SessionSpec) => {
        asked.push(seek);
        return `/hls/${SESSION}/index.m3u8`;
      },
    });
    const server = await startServer({ db: index(), source: new FakeSource(), hls: recording });
    try {
      // `Infinity` passes a NaN check and reaches the command line as `-ss
      // Infinity`, which ffmpeg exits on immediately.
      for (const seek of ["Infinity", "-Infinity", "1e400", "NaN", "abc", "-5"]) {
        await rawRequest(server.port, `/api/sets/${SET}/transcode?seek=${seek}`);
      }

      for (const seek of asked) {
        expect(Number.isFinite(seek)).toBe(true);
        expect(seek).toBeGreaterThanOrEqual(0);
      }
    } finally {
      await server.close();
    }
  });

  test("the audio route reports what the file holds", async () => {
    const reader = {
      read: async () => [
        { index: 0, lang: "deu", codec: "ac3", channels: 6, title: null, isDefault: true },
        { index: 1, lang: "eng", codec: "aac", channels: 2, title: null, isDefault: false },
      ],
    };
    const server = await startServer({
      db: index(),
      source: new FakeSource(),
      audio: reader as never,
    });
    try {
      const response = await rawRequest(server.port, `/api/sets/${SET}/audio`);
      expect(response.status).toBe(200);
      const { tracks } = JSON.parse(new TextDecoder().decode(response.body));
      expect(tracks).toHaveLength(2);
      expect(tracks[1]).toMatchObject({ index: 1, lang: "eng" });
    } finally {
      await server.close();
    }
  });

  test("a player built without a prober offers no choice rather than failing", async () => {
    const server = await startServer({ db: index(), source: new FakeSource() });
    try {
      const response = await rawRequest(server.port, `/api/sets/${SET}/audio`);
      expect(response.status).toBe(200);
      expect(JSON.parse(new TextDecoder().decode(response.body))).toEqual({ tracks: [] });
    } finally {
      await server.close();
    }
  });

  test("audio for a set the catalog will not play is a 404", async () => {
    const server = await startServer({ db: index(), source: new FakeSource() });
    try {
      const response = await rawRequest(server.port, "/api/sets/01NOPE/audio");
      expect(response.status).toBe(404);
    } finally {
      await server.close();
    }
  });

  /**
   * The ordinal reaches an ffmpeg command line as `-map 0:a:N`, so it gets the
   * same treatment the seek does: anything that is not a whole count of
   * streams becomes the first one.
   */
  test("an audio track that is not a stream number never reaches ffmpeg", async () => {
    const asked: number[] = [];
    const recording = fakeHls({
      begin: async ({ audioTrack }: SessionSpec) => {
        asked.push(audioTrack);
        return `/hls/${SESSION}/index.m3u8`;
      },
    });
    const server = await startServer({ db: index(), source: new FakeSource(), hls: recording });
    try {
      for (const track of ["2", "0", "-1", "1.7", "abc", "Infinity", "", "1e400"]) {
        await rawRequest(server.port, `/api/sets/${SET}/transcode?audio=${track}`);
      }
      // Asked for, then the first stream for everything that was not a count.
      expect(asked).toEqual([2, 0, 0, 1, 0, 0, 0, 0]);
      for (const track of asked) {
        expect(Number.isInteger(track)).toBe(true);
        expect(track).toBeGreaterThanOrEqual(0);
      }
    } finally {
      await server.close();
    }
  });

  test("a transcode with no audio asked for takes the first stream", async () => {
    const asked: (number | undefined)[] = [];
    const recording = fakeHls({
      begin: async ({ audioTrack }: SessionSpec) => {
        asked.push(audioTrack);
        return `/hls/${SESSION}/index.m3u8`;
      },
    });
    const server = await startServer({ db: index(), source: new FakeSource(), hls: recording });
    try {
      await rawRequest(server.port, `/api/sets/${SET}/transcode?seek=0`);
      expect(asked).toEqual([0]);
    } finally {
      await server.close();
    }
  });

  test("other methods on a session are still refused", async () => {
    const response = await rawRequest(hlsServer.port, `/hls/${SESSION}`, { method: "PUT" });

    expect(response.status).toBe(405);
  });
});

/**
 * A remote viewer is offered a conversion rather than the original file: the
 * page cannot tell where it is, and the server can.
 */
describe("how the player is being reached", () => {
  test("a request from this machine is local", async () => {
    const response = await request("/api/player");
    const said = JSON.parse(new TextDecoder().decode(response.body));

    expect(response.status).toBe(200);
    expect(said.remote).toBe(false);
    expect(said.maxBitrate).toBeGreaterThan(0);
  });

  test("a forwarded address is ignored when no proxy is trusted", async () => {
    // Otherwise anyone can claim to be on the LAN and ask for the original.
    const response = await rawRequest(server.port, "/api/player", {
      headers: { "x-forwarded-for": "203.0.113.9" },
    });
    const said = JSON.parse(new TextDecoder().decode(response.body));

    expect(said.remote).toBe(false);
  });

  test("with a trusted proxy, a forwarded internet address is remote", async () => {
    const proxied = await startServer({
      db: index(),
      source: new FakeSource(),
      trustProxy: true,
    });
    try {
      const response = await rawRequest(proxied.port, "/api/player", {
        headers: { "x-forwarded-for": "203.0.113.9" },
      });
      const said = JSON.parse(new TextDecoder().decode(response.body));

      expect(said.remote).toBe(true);
    } finally {
      await proxied.close();
    }
  });
});

/**
 * A library of a hundred and seventy lessons called things like "Definition"
 * is not browsable, only searchable.
 */
describe("the search route", () => {
  const find = async (query: string) => {
    const response = await request(`/api/search?q=${encodeURIComponent(query)}`);
    return {
      status: response.status,
      body: JSON.parse(new TextDecoder().decode(response.body)) as {
        query: string;
        hits: Array<{ title: string; matched: string; excerpt: string | null }>;
      },
    };
  };

  test("finds a title", async () => {
    const { status, body } = await find("matrix");

    expect(status).toBe(200);
    expect(body.hits.map((h) => h.title)).toContain("The Matrix");
    expect(body.hits[0]?.matched).toBe("title");
  });

  test("folds case and diacritics, because the library is German", async () => {
    // The fixture title has none, so this checks the query side folds too.
    const { body } = await find("MATRIX");

    expect(body.hits.map((h) => h.title)).toContain("The Matrix");
  });

  test("searches summary text", async () => {
    // The fixture set carries a summary reading "Worum es geht."
    const { body } = await find("worum");

    expect(body.hits[0]?.matched).toBe("summary");
    expect(body.hits[0]?.excerpt).toContain("Worum");
  });

  test("an empty query is not an error and finds nothing", async () => {
    for (const query of ["", "   "]) {
      const { status, body } = await find(query);

      expect(status).toBe(200);
      expect(body.hits).toEqual([]);
    }
  });

  test("a query matching nothing is an empty list, not a 404", async () => {
    const { status, body } = await find("kryptowahrung");

    expect(status).toBe(200);
    expect(body.hits).toEqual([]);
  });

  test("results carry no channel or message identifiers", async () => {
    const { body } = await find("matrix");

    const text = JSON.stringify(body);
    for (const leak of ["chatId", "messageId", "docId", "chat_id", "message_id"]) {
      expect(text).not.toContain(leak);
    }
  });

  /**
   * A hit is played by the same dialog a shelf row is, so it has to carry the
   * same fields. Missing `subtitles` loses the tracks; missing `hasSummary`
   * loses the notes panel; a stray `tmdb` is a field the catalog route is
   * careful not to send.
   */
  test("a hit carries what a catalog row carries, and nothing more", async () => {
    const catalog = await request("/api/sets");
    const rows = JSON.parse(new TextDecoder().decode(catalog.body)) as Array<Record<string, unknown>>;
    const { body } = await find("matrix");

    const expected = [...Object.keys(rows[0]!), "matched", "excerpt"].sort();
    expect(Object.keys(body.hits[0]!).sort()).toEqual(expected);
  });

  test("every response states its length", async () => {
    const response = await request("/api/search?q=matrix");

    expect(lengthOf(response)).toBe(response.body.byteLength);
  });
});
