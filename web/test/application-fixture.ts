import type { Config } from "../src/config";
import { EXPECTED_SCHEMA } from "../src/catalog";
import type { Telegram } from "../src/telegram/client";
import { emptyIndex } from "./index-fixture";
import { join } from "node:path";

export function library(title: string, setId = "01SET") {
  const db = emptyIndex();
  db.run("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  db.query("INSERT INTO meta VALUES ('schema_version', ?)").run(String(EXPECTED_SCHEMA));
  db.query(`INSERT INTO sets (set_id, kind, title, container, total, part_count, status, created_at, spec_version)
    VALUES (?, 'movie', ?, 'mp4', 10, 1, 'complete', 1, 4)`).run(setId, title);
  db.query(`INSERT INTO parts (set_id, idx, message_id, byte_offset, byte_length, status)
    VALUES (?, 0, 1, 0, 10, 'done')`).run(setId);
  return db;
}

export function snapshot(title: string, pushedAt: number, setId = "01SET") {
  const db = library(title, setId);
  const bytes = db.serialize();
  db.close();
  return { messageId: 1, pushedAt, chunks: async function* () { yield bytes; } };
}

export function configIn(root: string): Config {
  return {
    apiId: 1, apiHash: "test-api-hash", session: "", chatId: -1000000000001, channelAccessHash: 1n,
    libraryDb: join(root, "library.db"), cacheDir: join(root, "cache"), cacheMaxBytes: 0, cacheReadahead: 0,
    transcodeDir: join(root, "transcode"), stateDb: join(root, "state.db"), syncState: false, syncEveryMs: 60000,
    thumbsDir: join(root, "thumbs"), transcodeMaxrate: 8000000, packageUrl: null, packageKey: null,
    catalogDir: join(root, "package"), channelIndexDir: join(root, "channel"), postersCommand: "unused-posters",
    trustProxy: false, seriesPreload: false, hostname: "127.0.0.1", port: 0,
    telegramFilePath: join(root, "telegram.json"), adminTokenPath: join(root, "admin-token"),
    channelCatalogDir: join(root, "channel-catalog"),
  };
}

/** Only the Telegram connection boundary is replaced; no session is opened. */
export function telegramBoundary(order: string[]): Telegram {
  return {
    connected: true,
    client: {},
    disconnect: async () => { order.push("disconnect"); },
    partMedia: async () => { throw new Error("unexpected live media read"); },
  } as unknown as Telegram;
}

export function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}
