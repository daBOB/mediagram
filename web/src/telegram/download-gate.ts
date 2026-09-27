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
 * First come, first served among equals. The waits are short because each
 * holder is one bounded run of a part, never a whole film — except the
 * series preload's own whole-episode fill, which runs `background: true`:
 * it never delays a slot a foreground read (playback, readahead, probe,
 * transcode) wants, and never starts a fresh one while any foreground read
 * is running or already waiting, so a viewer never queues behind a preload
 * they cannot see.
 */

/** Enough to overlap round trips; well under the burst Telegram refuses. */
export const MAX_DOWNLOADS = 4;

export interface RunOptions {
  /** True for the series preload; false (the default) for everything a viewer is waiting on. */
  background?: boolean;
}

export class DownloadGate {
  private running = 0;
  /** Foreground tasks currently holding a slot, whether started fresh or handed one. */
  private foregroundActive = 0;
  private readonly foregroundWaiting: Array<() => void> = [];
  private readonly backgroundWaiting: Array<() => void> = [];

  constructor(private readonly limit = MAX_DOWNLOADS) {}

  /** Runs `task` once a slot is free, and frees it however `task` ends. */
  async run<T>(task: () => Promise<T>, options: RunOptions = {}): Promise<T> {
    const background = options.background ?? false;
    if (this.canStart(background)) {
      this.running += 1;
      if (!background) this.foregroundActive += 1;
    } else {
      const waiting = background ? this.backgroundWaiting : this.foregroundWaiting;
      await new Promise<void>((resolve) => waiting.push(resolve));
    }
    try {
      return await task();
    } finally {
      if (!background) this.foregroundActive -= 1;
      // The slot passes straight to the next in line rather than being freed
      // and re-taken, so a newcomer cannot slip in ahead of the queue — and a
      // foreground waiter always takes it before a background one gets a look.
      const nextForeground = this.foregroundWaiting.shift();
      if (nextForeground) {
        this.foregroundActive += 1;
        nextForeground();
      } else {
        const nextBackground = this.foregroundActive === 0 ? this.backgroundWaiting.shift() : undefined;
        if (nextBackground) nextBackground();
        else this.running -= 1;
      }
    }
  }

  /** Whether a fresh (not yet queued) task of this kind may take a slot right now. */
  private canStart(background: boolean): boolean {
    if (this.running >= this.limit) return false;
    if (!background) return true;
    return this.foregroundActive === 0 && this.foregroundWaiting.length === 0;
  }

  /** Downloads holding a slot now. For tests. */
  inFlight(): number {
    return this.running;
  }
}
