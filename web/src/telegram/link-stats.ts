/**
 * Per-DC request counts, latency and flood waits, kept and read separately.
 *
 * Pure and IO-free: `measured-client.ts` is the only caller, and it decides
 * what a request, a failure or a reconnect *is*. This only remembers how
 * many of each happened and how long they took.
 */

import { errors } from "teleproto";

/** One data centre's figures, ready for the snapshot. */
export interface DcStats {
  dc: number;
  requests: number;
  errors: number;
  bytes: number;
  /** Nearest-rank percentiles over the last 256 requests, or `null` with none yet. */
  p50Ms: number | null;
  p95Ms: number | null;
}

export interface LinkSnapshot {
  dcs: DcStats[];
  flood: { count: number; totalSeconds: number };
  /** Main-connection `connected` events after the first one. */
  reconnects: number;
}

/** How many recent latencies each DC keeps, for the percentiles. */
const RING_SIZE = 256;

interface DcAccumulator {
  requests: number;
  errors: number;
  bytes: number;
  latenciesMs: Float64Array;
  /** How many of `latenciesMs` are populated so far, capped at `RING_SIZE`. */
  filled: number;
  nextSlot: number;
}

function newAccumulator(): DcAccumulator {
  return { requests: 0, errors: 0, bytes: 0, latenciesMs: new Float64Array(RING_SIZE), filled: 0, nextSlot: 0 };
}

/** The nearest-rank percentile of `values`, which must already be sorted. */
function nearestRank(sorted: Float64Array, percentile: number): number {
  const rank = Math.min(sorted.length, Math.max(1, Math.ceil((percentile / 100) * sorted.length)));
  return sorted[rank - 1]!;
}

function percentilesOf(acc: DcAccumulator): { p50Ms: number | null; p95Ms: number | null } {
  if (acc.filled === 0) return { p50Ms: null, p95Ms: null };
  const sorted = acc.latenciesMs.slice(0, acc.filled).sort();
  return { p50Ms: nearestRank(sorted, 50), p95Ms: nearestRank(sorted, 95) };
}

/** Whether `error` is a flood wait Telegram itself asked for, and for how long. */
function floodWaitSeconds(error: unknown): number | null {
  return error instanceof errors.FloodWaitError || error instanceof errors.FloodTestPhoneWaitError
    ? error.seconds
    : null;
}

export class LinkStats {
  private readonly byDc = new Map<number, DcAccumulator>();
  private floodCount = 0;
  private floodSeconds = 0;
  private reconnectCount = 0;

  private accumulatorFor(dc: number): DcAccumulator {
    let acc = this.byDc.get(dc);
    if (!acc) {
      acc = newAccumulator();
      this.byDc.set(dc, acc);
    }
    return acc;
  }

  /** One request that answered, however it answered. */
  request(dc: number, ms: number, bytes: number): void {
    const acc = this.accumulatorFor(dc);
    acc.requests += 1;
    acc.bytes += bytes;
    acc.latenciesMs[acc.nextSlot] = ms;
    acc.nextSlot = (acc.nextSlot + 1) % RING_SIZE;
    acc.filled = Math.min(acc.filled + 1, RING_SIZE);
  }

  /** One request that ended in an error rather than a result. */
  failed(dc: number, error: unknown): void {
    this.accumulatorFor(dc).errors += 1;
    const seconds = floodWaitSeconds(error);
    if (seconds !== null) this.flood(seconds);
  }

  /** A flood wait Telegram asked for, whether or not it grew into a thrown error. */
  flood(seconds: number): void {
    this.floodCount += 1;
    this.floodSeconds += seconds;
  }

  /** The main MTProto connection coming back after having been up before. */
  reconnect(): void {
    this.reconnectCount += 1;
  }

  snapshot(): LinkSnapshot {
    const dcs = [...this.byDc.entries()]
      .map(([dc, acc]) => ({ dc, requests: acc.requests, errors: acc.errors, bytes: acc.bytes, ...percentilesOf(acc) }))
      .sort((a, b) => a.dc - b.dc);
    return { dcs, flood: { count: this.floodCount, totalSeconds: this.floodSeconds }, reconnects: this.reconnectCount };
  }
}
