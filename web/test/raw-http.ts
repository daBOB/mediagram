/**
 * A minimal HTTP/1.1 client that reports what is actually on the wire.
 *
 * `fetch` cannot be used to check `Content-Length`: Bun's client consumes the
 * header while decoding a streamed body and does not expose it, so a response
 * that carries it correctly looks identical to one that omits it. That
 * distinction is the whole point — ffmpeg cannot seek an HTTP source without
 * `Content-Length`, and falls back to reading from byte zero.
 *
 * So this speaks the protocol directly and parses the head itself.
 */

export interface RawResponse {
  status: number;
  /** Lowercased header names, in wire order preserved by last-wins. */
  headers: Map<string, string>;
  body: Uint8Array;
}

const HEAD_END = new TextEncoder().encode("\r\n\r\n");

function indexOfHeadEnd(buffer: Uint8Array): number {
  outer: for (let i = 0; i + HEAD_END.length <= buffer.length; i++) {
    for (let j = 0; j < HEAD_END.length; j++) {
      if (buffer[i + j] !== HEAD_END[j]) continue outer;
    }
    return i;
  }
  return -1;
}

function parseHead(head: string): { status: number; headers: Map<string, string> } {
  const lines = head.split("\r\n");
  const status = Number(lines[0]!.split(" ")[1]);
  const headers = new Map<string, string>();
  for (const line of lines.slice(1)) {
    const colon = line.indexOf(":");
    if (colon > 0) {
      headers.set(line.slice(0, colon).trim().toLowerCase(), line.slice(colon + 1).trim());
    }
  }
  return { status, headers };
}

export async function rawRequest(
  port: number,
  path: string,
  options: {
    method?: string;
    range?: string;
    headers?: Record<string, string>;
    /** A request body. `Content-Length` is stated, as the server requires. */
    body?: string;
  } = {},
): Promise<RawResponse> {
  const method = options.method ?? "GET";
  const lines = [`${method} ${path} HTTP/1.1`, `Host: 127.0.0.1:${port}`];
  if (options.range !== undefined) lines.push(`Range: ${options.range}`);
  for (const [name, value] of Object.entries(options.headers ?? {})) {
    lines.push(`${name}: ${value}`);
  }
  const body = options.body ?? null;
  if (body !== null) {
    lines.push(`Content-Length: ${new TextEncoder().encode(body).byteLength}`);
  }
  lines.push("Connection: close", "", body ?? "");

  let buffer = new Uint8Array(0);
  const append = (chunk: Uint8Array) => {
    const next = new Uint8Array(buffer.length + chunk.length);
    next.set(buffer);
    next.set(chunk, buffer.length);
    buffer = next;
  };

  await new Promise<void>((resolve, reject) => {
    Bun.connect({
      hostname: "127.0.0.1",
      port,
      socket: {
        open: (socket) => void socket.write(lines.join("\r\n")),
        data: (_socket, chunk) => append(new Uint8Array(chunk)),
        close: () => resolve(),
        error: (_socket, error) => reject(error),
      },
    }).catch(reject);
  });

  const end = indexOfHeadEnd(buffer);
  if (end < 0) throw new Error("the response had no header terminator");
  const { status, headers } = parseHead(new TextDecoder().decode(buffer.subarray(0, end)));
  return { status, headers, body: buffer.subarray(end + HEAD_END.length) };
}
