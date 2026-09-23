/**
 * How many part downloads may talk to Telegram at once.
 *
 * Starting a film opens several readers together: the browser's own range
 * requests, the audio-track probe and readahead behind each of them. Every
 * cache miss among them became its own `upload.getFile` stream, and a dozen
 * arriving in the same moment is what Telegram answers with FLOOD_WAIT —
 * which then stalls all of them, the one the viewer is waiting on included.
 * Holding the rest in a queue costs a few milliseconds each; a flood wait
 * costs every reader whole seconds, and grows if it is met again.
 *
 * First come, first served. The waits are short because each holder is one
 * bounded run of a part, never a whole film.
 */

/** Enough to overlap round trips; well under the burst Telegram refuses. */
export const MAX_DOWNLOADS = 4;

export class DownloadGate {
  private running = 0;
  private readonly waiting: Array<() => void> = [];

  constructor(private readonly limit = MAX_DOWNLOADS) {}

  /** Runs `task` once a slot is free, and frees it however `task` ends. */
  async run<T>(task: () => Promise<T>): Promise<T> {
    if (this.running >= this.limit) {
      await new Promise<void>((resolve) => this.waiting.push(resolve));
    } else {
      this.running += 1;
    }
    try {
      return await task();
    } finally {
      // The slot passes straight to the next in line rather than being freed
      // and re-taken, so a newcomer cannot slip in ahead of the queue.
      const next = this.waiting.shift();
      if (next) next();
      else this.running -= 1;
    }
  }

  /** Downloads holding a slot now. For tests. */
  inFlight(): number {
    return this.running;
  }
}
