/**
 * The player backend: catalog and bytes, over HTTP.
 *
 * This process speaks MTProto and holds the session. The browser is an
 * ordinary HTTP client of it and is told what it may play, never where the
 * bytes live.
 */

import { Database } from "bun:sqlite";
import { describe, load } from "./config";
import { assertSchema, listPlayable } from "./catalog";
import { startServer } from "./server";
import { CachedReader } from "./cache/reader";
import { ChunkCache } from "./cache/store";
import { Telegram } from "./telegram/client";
import { TelegramSource } from "./telegram/source";

const config = load();
console.log("player:", describe(config));

// Read-only: the player never writes, and a writable handle would let it
// checkpoint or migrate an index the uploader owns.
const db = new Database(config.libraryDb, { readonly: true });
assertSchema(db);
console.log(`catalog: ${listPlayable(db).length} playable sets`);

const telegram = await Telegram.connect(config);

// A budget of zero turns caching off, which is a legitimate choice on a
// machine with no disk to spare.
const cache = config.cacheMaxBytes > 0 ? new ChunkCache(config.cacheDir, config.cacheMaxBytes) : null;
if (cache) {
  const held = await cache.sizeOnDisk();
  console.log(
    `cache: ${(held / 1024 ** 3).toFixed(2)} GB of ${(config.cacheMaxBytes / 1024 ** 3).toFixed(2)} GB in ${config.cacheDir}`,
  );
} else {
  console.log("cache: disabled");
}

const server = await startServer({
  db,
  source: new TelegramSource(telegram, cache ? new CachedReader(cache) : undefined),
  port: config.port,
  hostname: config.hostname,
});

console.log(`serving on http://${config.hostname}:${server.port}`);

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    void (async () => {
      console.log("\nstopping");
      await server.close();
      await telegram.disconnect();
      db.close();
      process.exit(0);
    })();
  });
}
