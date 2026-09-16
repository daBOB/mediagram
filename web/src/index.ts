/**
 * The player backend: catalog and bytes, over HTTP.
 *
 * This process speaks MTProto and holds the session. The browser is an
 * ordinary HTTP client of it and is told what it may play, never where the
 * bytes live.
 */

import { Database } from "bun:sqlite";
import { describe, load } from "./config";
import { listPlayable } from "./catalog";
import { startServer } from "./server";
import { Telegram } from "./telegram/client";
import { TelegramSource } from "./telegram/source";

const config = load();
console.log("player:", describe(config));

// Read-only: the player never writes, and a writable handle would let it
// checkpoint or migrate an index the uploader owns.
const db = new Database(config.libraryDb, { readonly: true });
console.log(`catalog: ${listPlayable(db).length} playable sets`);

const telegram = await Telegram.connect(config);
const server = await startServer({
  db,
  source: new TelegramSource(telegram),
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
