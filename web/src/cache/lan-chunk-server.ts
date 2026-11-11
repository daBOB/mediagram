/**
 * The household's LAN chunk server (`mediagram_cache`), asked before Telegram.
 *
 * It holds a set as 1 MiB chunks numbered over the whole set, the parts laid
 * end to end, which is exactly how the Android app reads and writes it, so a
 * chunk either player fetched once serves both. A read needs nothing; a write
 * is signed with the pairing token (docs/running-the-player.md, "Pairing a
 * device"). The server is optional: when it cannot be reached, reads go to
 * Telegram as if it did not exist, and it is left alone for a while rather
 * than costing every read a timeout.
 */

import { createHash, createHmac } from "node:crypto";

import type { PartSpan } from "../range";
import type { FetchRange } from "./reader";

/** The server's chunk size (`crates/mediagram-cache/src/rules.rs`, `CHUNK`). */
export const LAN_CHUNK = 1 << 20;

const GET_TIMEOUT_MS = 2_000;
/** A stalled server must not hold a pending write's 1 MiB for ever. */
const PUT_TIMEOUT_MS = 30_000;
/** How long an unreachable server is skipped before it is tried again. */
const RETRY_AFTER_MS = 30_000;

export interface LanChunkServerConfig {
  /** e.g. `http://127.0.0.1:7788`, no trailing slash. */
  url: string;
  /** The pairing token; without it the server is only read, never written. */
  token: string | null;
}

/**
 * The `MGC1` signature of a PUT. Keyed on the token's 64 hex characters as
 * text, never on the bytes they spell: decoding first yields a different,
 * silently wrong signature.
 */
export function lanSignature(token: string, path: string, total: number, body: Uint8Array): string {
  const bodyHash = createHash("sha256").update(body).digest("hex");
  return createHmac("sha256", token).update(`PUT\n${path}\n${total}\n${bodyHash}`).digest("hex");
}

export class LanChunkServer {
  /** One Telegram fetch per chunk, however many reads want it at once. */
  private readonly inFlight = new Map<string, Promise<Uint8Array>>();
  /** PUTs still on their way, so tests and shutdown can wait for them. */
  private readonly writes = new Set<Promise<void>>();
  private skipUntil = 0;

  constructor(
    private readonly config: LanChunkServerConfig,
    private readonly now: () => number = Date.now,
    private readonly putTimeoutMs = PUT_TIMEOUT_MS,
  ) {}

  /**
   * A `FetchRange` for one part that reads through the server. `fetchPart`
   * gives Telegram's fetch for any part of the set, for a chunk whose bytes lie
   * in a part other than the one asked about.
   */
  fetcherFor(setId: string, spans: PartSpan[], partIdx: number, fetchPart: (partIdx: number) => FetchRange): FetchRange {
    const span = spans.find((s) => s.idx === partIdx);
    if (!span) throw new Error(`part ${partIdx} is not in set ${setId}`);
    const total = Math.max(...spans.map((s) => s.off + s.len));
    return async (offset, length) => {
      const start = span.off + offset;
      const end = start + length;
      const out = new Uint8Array(length);
      for (let n = Math.floor(start / LAN_CHUNK); n * LAN_CHUNK < end; n++) {
        const chunk = await this.chunk(setId, total, spans, n, fetchPart);
        const from = Math.max(start, n * LAN_CHUNK);
        const to = Math.min(end, n * LAN_CHUNK + chunk.length);
        out.set(chunk.subarray(from - n * LAN_CHUNK, to - n * LAN_CHUNK), from - start);
      }
      return out;
    };
  }

  /** Waits for every PUT started so far. */
  async settle(): Promise<void> {
    await Promise.all([...this.writes]);
  }

  private chunk(setId: string, total: number, spans: PartSpan[], n: number, fetchPart: (partIdx: number) => FetchRange) {
    const key = `${setId}/${n}`;
    const pending = this.inFlight.get(key);
    if (pending) return pending;
    const loading = this.load(setId, total, spans, n, fetchPart).finally(() => this.inFlight.delete(key));
    this.inFlight.set(key, loading);
    return loading;
  }

  private async load(setId: string, total: number, spans: PartSpan[], n: number, fetchPart: (partIdx: number) => FetchRange) {
    const path = `/v1/sets/${setId}/chunks/${n}`;
    const length = Math.min(LAN_CHUNK, total - n * LAN_CHUNK);
    const held = await this.get(path);
    // A chunk of the wrong length belongs to some other idea of this set; Telegram decides.
    if (held && held.length === length) return held;
    const bytes = await readSetRange(spans, n * LAN_CHUNK, length, fetchPart);
    this.put(path, total, bytes);
    return bytes;
  }

  private async get(path: string): Promise<Uint8Array | null> {
    if (this.now() < this.skipUntil) return null;
    try {
      const response = await fetch(this.config.url + path, { signal: AbortSignal.timeout(GET_TIMEOUT_MS) });
      if (response.ok) return new Uint8Array(await response.arrayBuffer());
      await response.body?.cancel();
      return null;
    } catch {
      this.skipUntil = this.now() + RETRY_AFTER_MS;
      return null;
    }
  }

  private put(path: string, total: number, body: Uint8Array): void {
    const token = this.config.token;
    if (!token || this.now() < this.skipUntil) return;
    const write = fetch(this.config.url + path, {
      method: "PUT",
      body,
      signal: AbortSignal.timeout(this.putTimeoutMs),
      headers: { "X-Set-Total": String(total), Authorization: `MGC1 ${lanSignature(token, path, total, body)}` },
    })
      .then((response) => response.body?.cancel())
      // A lost write costs only a Telegram fetch the next time; the viewer already has the bytes.
      .catch(() => undefined)
      .finally(() => this.writes.delete(write));
    this.writes.add(write);
  }
}

/** `length` bytes at set offset `start`, taken from whichever parts hold them. */
async function readSetRange(spans: PartSpan[], start: number, length: number, fetchPart: (partIdx: number) => FetchRange) {
  const out = new Uint8Array(length);
  const end = start + length;
  for (const span of spans) {
    const from = Math.max(start, span.off);
    const to = Math.min(end, span.off + span.len);
    if (from >= to) continue;
    const bytes = await fetchPart(span.idx)(from - span.off, to - from);
    if (bytes.length !== to - from) throw new Error(`part ${span.idx} returned ${bytes.length} of ${to - from} bytes`);
    out.set(bytes, from - start);
  }
  return out;
}
