/**
 * How fast part downloads are arriving, for the player's statistics: the
 * same "network" and "next" rows the Android player shows, measured here
 * because a browser never sees a Telegram fetch.
 *
 * Kept apart from the gate, which decides who may fetch; this only counts
 * what arrived.
 */

/** Seconds a rate is averaged over, plus the one still filling. */
const BUCKETS = 5;

/**
 * Bytes that arrived, as a rate over the last few whole seconds.
 *
 * The second still in progress is left out: it is partly counted, and a
 * readout that dipped at the start of every second would flicker. So the
 * rate lags by up to a second, which a number read by eye never notices.
 */
export class RateWindow {
  private readonly buckets = new Array<number>(BUCKETS).fill(0);
  private newest = 0;

  constructor(private readonly now: () => number = () => Date.now()) {}

  add(bytes: number): void {
    this.roll();
    this.buckets[this.newest % BUCKETS]! += bytes;
  }

  bitsPerSecond(): number {
    this.roll();
    const whole = this.buckets.reduce((sum, b) => sum + b, 0) - this.buckets[this.newest % BUCKETS]!;
    return Math.round((whole * 8) / (BUCKETS - 1));
  }

  /** Empties every bucket whose second has passed, up to the current one. */
  private roll(): void {
    const second = Math.floor(this.now() / 1000);
    if (second <= this.newest) return;
    const passed = Math.min(second - this.newest, BUCKETS);
    for (let step = 1; step <= passed; step++) this.buckets[(this.newest + step) % BUCKETS] = 0;
    this.newest = second;
  }
}

/** What the part downloads are doing right now. */
export interface TrafficSnapshot {
  foregroundBitsPerSecond: number;
  foregroundInFlight: number;
  backgroundBitsPerSecond: number;
  /** A preload fetch is queued behind a viewer's. */
  backgroundWaiting: boolean;
}
