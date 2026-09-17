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
import { clientAddress } from "./client-reach";
import { createRouter, type ByteSource, type HlsServer, type PlayerRequest } from "./routes";

export interface RunningServer {
  port: number;
  close(): Promise<void>;
}

function describe(request: IncomingMessage, trustProxy: boolean): PlayerRequest {
  const rawRange = request.headers.range;
  const url = new URL(request.url ?? "/", "http://localhost");
  const forwarded = request.headers["x-forwarded-for"];
  return {
    method: request.method ?? "GET",
    path: url.pathname,
    range: typeof rawRange === "string" ? rawRange : null,
    seek: url.searchParams.get("seek"),
    // Resolved here, once, rather than left for a route to work out: behind a
    // proxy every request arrives from loopback, and whether the forwarded
    // address may be believed is a property of how this server was started.
    client: clientAddress(
      request.socket.remoteAddress ?? null,
      typeof forwarded === "string" ? forwarded : (forwarded?.[0] ?? null),
      trustProxy,
    ),
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
  hls?: HlsServer;
  port?: number;
  hostname?: string;
  /**
   * Whether `X-Forwarded-For` may be believed.
   *
   * Only true where a reverse proxy really is in front. Anywhere else the
   * header is whatever the caller chose to send, and believing it would let
   * anyone claim to be on the local network.
   */
  trustProxy?: boolean;
  maxBitrate?: number;
}): Promise<RunningServer> {
  const route = createRouter({
    db: options.db,
    source: options.source,
    hls: options.hls,
    maxBitrate: options.maxBitrate,
  });
  const trustProxy = options.trustProxy ?? false;

  const server = createServer((request, response) => {
    void (async () => {
    const planned = await route(describe(request, trustProxy));
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
    })().catch((error) => {
      // Logged, not swallowed: a request that fails silently is a bug that
      // presents as an empty response with no explanation anywhere.
      console.error(`request failed: ${request.method} ${request.url}`, error);
      if (!response.headersSent) response.writeHead(500, { "content-length": "0" });
      response.end();
    });
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
  } catch (error) {
    // The headers already promised a length we can no longer deliver, so the
    // only honest signal left is an incomplete response: destroy the socket
    // rather than end it cleanly and have the client believe it has the file.
    // Logged too: a truncated download with nothing in the log is a bug that
    // can only be found by guessing.
    console.error("stream aborted mid-body", error);
    response.destroy();
  } finally {
    response.off("close", abandon);
  }
}
