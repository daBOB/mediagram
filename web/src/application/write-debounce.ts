/**
 * Collapses a burst of local state writes into one sync round, fired a few
 * seconds after the last of them.
 *
 * Trailing-edge, unlike `telegram/updates.ts`'s `Debouncer`: that one opens
 * a fixed window at the *first* event of a burst, which is right for a push
 * arriving mid-burst from another device — the round should not wait for
 * every last update to stop arriving. A local write is different: the
 * viewer is still clicking, and a round fired at the first click would just
 * run again for the second. Waiting for quiet instead means one round per
 * burst, not one per pause long enough to look like the end of one.
 *
 * Runs through `StateSync.once()` and nothing else — `touch()` only decides
 * *when* to ask for a round, never how one runs; the no-overlap guard and
 * the "nothing changed, send nothing" rule stay exactly where they already
 * are.
 */

export interface DebounceClock {
  setTimeout(run: () => void, ms: number): unknown;
  clearTimeout(timer: unknown): void;
}

const realClock: DebounceClock = {
  setTimeout: (run, ms) => setTimeout(run, ms),
  clearTimeout: (timer) => clearTimeout(timer as ReturnType<typeof setTimeout>),
};

export class WriteDebounce {
  private timer: unknown = null;
  /** Latched, not just cleared: a write can still land between `stop()` and
   * the resources it was guarding actually closing (`lifecycle.ts`), and
   * that write must not re-arm a timer that would run `sync` against them
   * mid- or post-close. There is no way back from this — a fresh instance is
   * what the next start makes. */
  private stopped = false;

  constructor(
    private readonly run: () => void,
    private readonly delayMs: number,
    private readonly clock: DebounceClock = realClock,
  ) {}

  /** Called after a local write; (re)arms the timer at the full delay. A
   * no-op once `stop()` has been called. */
  touch(): void {
    if (this.stopped) return;
    if (this.timer !== null) this.clock.clearTimeout(this.timer);
    this.timer = this.clock.setTimeout(() => {
      this.timer = null;
      this.run();
    }, this.delayMs);
  }

  /** Cancels a pending round without running it, and refuses every `touch()`
   * after. */
  stop(): void {
    this.stopped = true;
    if (this.timer !== null) this.clock.clearTimeout(this.timer);
    this.timer = null;
  }
}
