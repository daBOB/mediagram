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

import { Database } from "bun:sqlite";
import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { startServer, type RunningServer } from "../src/server";
import type { ByteSource } from "../src/routes";
import { rawRequest } from "./raw-http";
import { ALIGN, type Step } from "../src/range";
import type { PartLocation } from "../src/catalog";

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
  const db = new Database(":memory:");
  db.run(`CREATE TABLE sets(
      set_id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT, show TEXT, chap TEXT, path TEXT,
      season INTEGER, episode TEXT, year INTEGER, container TEXT NOT NULL,
      vcodec TEXT, acodec TEXT, duration INTEGER,
      total INTEGER NOT NULL, part_count INTEGER NOT NULL,
      status TEXT NOT NULL, created_at INTEGER NOT NULL)`);
  db.run(`CREATE TABLE assets(
      set_id TEXT NOT NULL, kind TEXT NOT NULL,
      lang TEXT NOT NULL DEFAULT '', body TEXT NOT NULL,
      PRIMARY KEY(set_id, kind, lang))`);
  db.run("INSERT INTO assets VALUES (?, 'summary', '', 'Worum es geht.')", [SET]);
  db.run("INSERT INTO assets VALUES (?, 'subtitle', 'deu', 'WEBVTT\n\nhallo')", [SET]);
  db.run(`CREATE TABLE parts(
      set_id TEXT NOT NULL, idx INTEGER NOT NULL,
      byte_offset INTEGER NOT NULL, byte_length INTEGER NOT NULL,
      chat_id INTEGER, message_id INTEGER, status TEXT NOT NULL,
      PRIMARY KEY(set_id, idx))`);
  // Columns named, not positional: a positional insert breaks silently the
  // next time the schema gains one.
  db.run(
    `INSERT INTO sets(set_id, kind, title, year, container, vcodec, acodec,
                      duration, total, part_count, status, created_at)
     VALUES (?, 'movie', 'The Matrix', 1999, 'mkv', 'hevc', 'ac3', 8160, ?, 2, 'complete', 1700000000)`,
    [SET, TOTAL],
  );
  db.run(`INSERT INTO parts VALUES (?, 0, 0, ?, -1001, 100, 'done')`, [SET, P0]);
  db.run(`INSERT INTO parts VALUES (?, 1, ?, ?, -1001, 101, 'done')`, [SET, P0, P1]);
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

  test("a malformed range is a bad request", async () => {
    const response = await request(`/api/sets/${SET}/stream`, { range: "kilometres=0-99" });

    expect(response.status).toBe(400);
    expect(lengthOf(response)).toBe(0);
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

  /** A path that climbs out of the public directory must not be served. */
  test("a traversal attempt is refused", async () => {
    for (const path of ["/../src/config.ts", "/..%2fsrc%2fconfig.ts", "/../../.env"]) {
      const response = await request(path);
      expect([400, 404]).toContain(response.status);
    }
  });
});
