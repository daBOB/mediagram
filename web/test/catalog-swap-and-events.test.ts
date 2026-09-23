/**
 * A catalog swapped while the server runs, and the pages told about it.
 */

import { afterEach, describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { CatalogEvents } from "../src/catalog-events";
import { startServer, type RunningServer } from "../src/server";
import { emptyIndex } from "./index-fixture";

/** A catalog holding one complete, playable film called `title`. */
function catalogWith(title: string): Database {
  const db = emptyIndex();
  db.run(
    `INSERT INTO sets (set_id, kind, title, container, total, part_count, status, created_at, spec_version)
     VALUES ('01SET', 'movie', ?1, 'mp4', 10, 1, 'complete', 1, 4)`,
    [title],
  );
  db.run(
    `INSERT INTO parts (set_id, idx, message_id, byte_offset, byte_length, status)
     VALUES ('01SET', 0, 1, 0, 10, 'done')`,
  );
  return db;
}

const source = { stream: () => new ReadableStream() } as never;
let server: RunningServer | null = null;
afterEach(async () => {
  await server?.close();
  server = null;
});

async function titles(): Promise<string[]> {
  const response = await fetch(`http://127.0.0.1:${server!.port}/api/sets`);
  return ((await response.json()) as { title: string }[]).map((set) => set.title);
}

describe("swapping the catalog under a running server", () => {
  test("the next request answers from the new catalog", async () => {
    server = await startServer({ db: catalogWith("Before"), source });
    expect(await titles()).toEqual(["Before"]);
    server.replaceCatalog({ db: catalogWith("After"), catalog: { origin: "channel", publishedAt: 5000 } });
    expect(await titles()).toEqual(["After"]);
  });

  test("search follows the swap too, not only the shelf", async () => {
    server = await startServer({ db: catalogWith("Before"), source });
    server.replaceCatalog({ db: catalogWith("Afterwards"), catalog: { origin: "channel", publishedAt: 5000 } });
    const response = await fetch(`http://127.0.0.1:${server.port}/api/search?q=afterwards`);
    const { hits } = (await response.json()) as { hits: { title: string }[] };
    expect(hits.map((hit) => hit.title)).toEqual(["Afterwards"]);
  });

  test("the page is told where the new catalog came from", async () => {
    server = await startServer({ db: catalogWith("Before"), source, catalog: { origin: "local", publishedAt: null } });
    server.replaceCatalog({ db: catalogWith("After"), catalog: { origin: "channel", publishedAt: 5000 } });
    const said = (await (await fetch(`http://127.0.0.1:${server.port}/api/player`)).json()) as {
      catalog: { origin: string; publishedAt: number };
    };
    expect(said.catalog).toEqual({ origin: "channel", publishedAt: 5000 });
  });
});

describe("telling open pages", () => {
  test("a page's stream opens at once and hears a catalog change", async () => {
    const events = new CatalogEvents();
    server = await startServer({ db: catalogWith("Before"), source, events });
    const response = await fetch(`http://127.0.0.1:${server.port}/api/events`);
    expect(response.headers.get("content-type")).toBe("text/event-stream");
    const reader = response.body!.getReader();
    const decoder = new TextDecoder();

    expect(decoder.decode((await reader.read()).value)).toBe(": connected\n\n");
    events.catalogChanged(5000);
    expect(decoder.decode((await reader.read()).value)).toBe('event: catalog\ndata: {"publishedAt":5000}\n\n');
    await reader.cancel();
  });

  test("a page that goes away stops being written to", async () => {
    const events = new CatalogEvents();
    const stream = events.subscribe();
    expect(events.count).toBe(1);
    await stream.cancel();
    expect(events.count).toBe(0);
    // Nothing left to write to, so this must not throw.
    events.catalogChanged(null);
  });

  test("without an event hub there is no stream to open", async () => {
    server = await startServer({ db: catalogWith("Before"), source });
    expect((await fetch(`http://127.0.0.1:${server.port}/api/events`)).status).toBe(404);
  });
});
