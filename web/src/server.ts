/**
 * The listener, on `node:http` rather than `Bun.serve`.
 *
 * This is not a preference. `Bun.serve` discards a manually set
 * `Content-Length` and sends `Transfer-Encoding: chunked` for any streamed
 * body it cannot buffer first — measured on Bun 1.4.2 across one-chunk,
 * many-chunk, async and "direct" streams. ffmpeg cannot seek an HTTP source
 * without `Content-Length`; it reads from byte zero instead, which would
 * quietly make every transcode start at the beginning of the film.
 * `node:http` writes the headers it is given.
 *
 * Throughput here is bounded by Telegram, not by the HTTP layer, so the
 * trade costs nothing that matters.
 */

import type { Database } from "bun:sqlite";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { createRouter, type ByteSource, type PlayerRequest } from "./routes";

export interface RunningServer {
  port: number;
  close(): Promise<void>;
}

function describe(request: IncomingMessage): PlayerRequest {
  const rawRange = request.headers.range;
  return {
    method: request.method ?? "GET",
    // The path only; a query string is not part of any route here.
    path: (request.url ?? "/").split("?")[0]!,
    range: typeof rawRange === "string" ? rawRange : null,
  };
}

/**
 * Writes `chunk` and resolves once it is safe to write again.
 *
 * Without waiting for drain, a fast download into a slow connection queues
 * the whole set in memory, which is exactly what streaming is meant to avoid.
 */
function write(response: ServerResponse, chunk: Uint8Array): Promise<void> {
  return new Promise((resolve, reject) => {
    const flushed = response.write(chunk, (error) => {
      if (error) reject(error);
    });
    if (flushed) resolve();
    else response.once("drain", resolve);
  });
}

export function startServer(options: {
  db: Database;
  source: ByteSource;
  port?: number;
  hostname?: string;
}): Promise<RunningServer> {
  const route = createRouter(options.db, options.source);

  const server = createServer((request, response) => {
    const planned = route(describe(request));
    response.writeHead(planned.status, planned.headers);

    if (planned.body === null) {
      response.end();
      return;
    }
    if (planned.body instanceof Uint8Array) {
      response.end(planned.body);
      return;
    }

    void pump(planned.body, response);
  });

  return new Promise((resolve) => {
    server.listen(options.port ?? 0, options.hostname ?? "127.0.0.1", () => {
      const address = server.address();
      const port = typeof address === "object" && address !== null ? address.port : 0;
      resolve({
        port,
        close: () =>
          new Promise<void>((done) => {
            server.closeAllConnections?.();
            server.close(() => done());
          }),
      });
    });
  });
}

async function pump(body: ReadableStream<Uint8Array>, response: ServerResponse): Promise<void> {
  const reader = body.getReader();
  // A viewer who seeks or closes the tab abandons the response. Cancelling
  // the reader stops the download rather than paying for bytes nobody reads.
  const abandon = () => void reader.cancel().catch(() => {});
  response.on("close", abandon);

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (response.writableEnded || response.destroyed) break;
      await write(response, value);
    }
    response.end();
  } catch {
    // The headers already promised a length we can no longer deliver, so the
    // only honest signal left is an incomplete response: destroy the socket
    // rather than end it cleanly and have the client believe it has the file.
    response.destroy();
  } finally {
    response.off("close", abandon);
  }
}
