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
import { createRouter, type CatalogOrigin, type RouterOptions } from "./routes";
import type { PlayerRequest } from "./http/contracts";

export interface RunningServer {
  port: number;
  /** This listener's reachable local endpoint, including its actual bound port. */
  baseUrl: string;
  close(): Promise<void>;
  /**
   * Serves another catalog from the next request on.
   *
   * The router is rebuilt rather than patched: everything it derives from the
   * index — the search index, every query's handle — is derived in
   * `createRouter`, so building it again over the new handle is the one place
   * that cannot forget something. A request already running keeps the router
   * it started with, so a stream in flight finishes on the catalog it opened.
   */
  replaceCatalog(next: { db: Database; catalog: CatalogOrigin }): void;
}

/**
 * The most a write may carry.
 *
 * Everything this API accepts is a position, a name or an id. A body larger
 * than this is not one of those, and reading it would be a way to make the
 * player hold megabytes on behalf of anyone who can reach the port.
 */
const MAX_BODY_BYTES = 64 * 1024;

/** The body of a write, or `null`. Refuses rather than truncates when over. */
async function readBody(request: IncomingMessage): Promise<string | null> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) return null;
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks).toString("utf8");
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
    maxrate: url.searchParams.get("maxrate"),
    audio: url.searchParams.get("audio"),
    vcodecs: url.searchParams.get("vcodecs"),
    query: url.searchParams.get("q"),
    final: url.searchParams.get("final"),
    // Both read here so a route compares them rather than reaching for
    // headers it would have to be handed anyway.
    contentType: (request.headers["content-type"] ?? null)?.split(";")[0]?.trim().toLowerCase() ?? null,
    origin: typeof request.headers.origin === "string" ? request.headers.origin : null,
    host: typeof request.headers.host === "string" ? request.headers.host : null,
    acceptEncoding: typeof request.headers["accept-encoding"] === "string" ? request.headers["accept-encoding"] : null,
    ifNoneMatch: typeof request.headers["if-none-match"] === "string" ? request.headers["if-none-match"] : null,
    cookie: typeof request.headers.cookie === "string" ? request.headers.cookie : null,
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
 *
 * A socket that dies while full never drains, so the close and error events
 * settle the wait too. Without them the handler stayed suspended for the life
 * of the process: the download it was driving was never cancelled, and every
 * seek or closed tab on a slow link left another one behind.
 */
export function write(response: ServerResponse, chunk: Uint8Array): Promise<void> {
  return new Promise((resolve, reject) => {
    const flushed = response.write(chunk, (error) => {
      if (error) reject(error);
    });
    if (flushed) {
      resolve();
      return;
    }

    const done = () => {
      response.off("drain", onDrain);
      response.off("close", onGone);
      response.off("error", onGone);
    };
    const onDrain = () => {
      done();
      resolve();
    };
    const onGone = () => {
      done();
      reject(new Error("the reader went away"));
    };
    response.once("drain", onDrain);
    response.once("close", onGone);
    response.once("error", onGone);
  });
}

export function startServer(options: RouterOptions & {
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
}): Promise<RunningServer> {
  const routerFor = (db: Database, catalog: CatalogOrigin | undefined) =>
    createRouter({ ...options, db, catalog });
  let route = routerFor(options.db, options.catalog);
  const trustProxy = options.trustProxy ?? false;
  const requests = new Map<ServerResponse, Promise<void>>();
  let stopping: Promise<void> | undefined;
  let accepting = true;

  const server = createServer((request, response) => {
    if (!accepting) {
      response.destroy();
      return;
    }
    const running = (async () => {
    const described = describe(request, trustProxy);
    // Read only for the methods that carry one, so a GET is never held up
    // waiting on a stream that will not produce anything.
    const carriesBody = !["GET", "HEAD", "DELETE"].includes(described.method);
    const planned = await route(
      carriesBody ? { ...described, body: await readBody(request) } : described,
    );
    response.writeHead(planned.status, planned.headers);

    if (planned.body === null) {
      response.end();
      return;
    }
    if (planned.body instanceof Uint8Array) {
      response.end(planned.body);
      return;
    }

    await pump(planned.body, response);
    })().catch((error) => {
      // Logged, not swallowed: a request that fails silently is a bug that
      // presents as an empty response with no explanation anywhere.
      console.error(`request failed: ${request.method} ${request.url}`, error);
      if (!response.headersSent) response.writeHead(500, { "content-length": "0" });
      response.end();
    }).finally(() => { requests.delete(response); });
    requests.set(response, running);
  });

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(options.port ?? 0, options.hostname ?? "127.0.0.1", () => {
      server.off("error", reject);
      const address = server.address();
      if (address === null || typeof address === "string") {
        server.close();
        reject(new Error("HTTP listener did not bind a TCP address"));
        return;
      }
      const port = address.port;
      const hostname = address.address === "0.0.0.0" ? "127.0.0.1"
        : address.address === "::" ? "::1" : address.address;
      const host = hostname.includes(":") ? `[${hostname}]` : hostname;
      resolve({
        port,
        baseUrl: `http://${host}:${port}`,
        close: () => stopping ??= (async () => {
          accepting = false;
          // Stop admission before destroying sockets; their pumps still own
          // upstream cancellation until the tracked request tasks settle.
          const closed = new Promise<void>((done) => { server.close(() => done()); });
          for (const response of requests.keys()) response.destroy();
          server.closeAllConnections?.();
          await closed;
          await Promise.all(requests.values());
        })(),
        replaceCatalog: (next) => {
          route = routerFor(next.db, next.catalog);
        },
      });
    });
  });
}

async function pump(body: ReadableStream<Uint8Array>, response: ServerResponse): Promise<void> {
  const reader = body.getReader();
  // A viewer who seeks or closes the tab abandons the response. Cancelling
  // the reader stops the download rather than paying for bytes nobody reads.
  let cancellation: Promise<void> | undefined;
  let reportedFailure = false;
  let failure: unknown;
  const abandon = () => {
    cancellation ??= reader.cancel().catch((error) => {
      if (!reportedFailure || !Object.is(error, failure)) console.error("stream cancellation failed", error);
    });
  };
  response.on("close", abandon);
  // Routing can finish after shutdown already closed this response.
  if (response.destroyed) abandon();

  let finished = false;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) { finished = true; break; }
      if (response.writableEnded || response.destroyed) break;
      await write(response, value);
    }
    response.end();
  } catch (error) {
    // The headers already promised a length we can no longer deliver, so the
    // only honest signal left is an incomplete response: destroy the socket
    // rather than end it cleanly and have the client believe it has the file.
    //
    // Logged only when this end failed. A viewer who seeks or closes the tab
    // lands here too, and that is what is supposed to happen; a truncated
    // download caused by an actual fault, though, is a bug that can only be
    // found by guessing if nothing says so.
    if (!response.destroyed && !response.writableEnded) {
      console.error("stream aborted mid-body", error);
      reportedFailure = true;
      failure = error;
    }
    response.destroy();
  } finally {
    response.off("close", abandon);
    if (!finished) abandon();
    await cancellation;
    reader.releaseLock();
  }
}
