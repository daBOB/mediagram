/**
 * A short-lived cache of Telegram's per-message document media.
 *
 * `channels.GetMessages` used to run again for every fetched run — chunk
 * writes, readahead, the audio-track probe — purely to relearn a file
 * reference this process asked for a moment ago. The account is
 * flood-limited process-wide (`download-gate.ts`), so an extra request per
 * run was never free even when it succeeded, and several readers of the same
 * part at once asked for the identical answer concurrently.
 *
 * A file reference itself expires, though — that is the whole reason the
 * lookup used to be fetched fresh every time — so this is not a cache of the
 * truth, only of a recent answer. Bounded in size and time, and dropped the
 * moment a download reports the reference stale, so the next attempt asks
 * again rather than retrying forever against a cached lie.
 */

/** Answers held at once. Comfortably more than a title's part count. */
const MAX_ENTRIES = 256;

/** How long an answer is trusted before it is asked for again regardless. */
const TTL_MS = 30 * 60 * 1000;

interface Entry<T> {
  value: T;
  at: number;
}

export class MediaCache<T> {
  private readonly entries = new Map<number, Entry<T>>();
  /** Concurrent lookups of the same message share one request rather than each starting their own. */
  private readonly inFlight = new Map<number, Promise<T>>();

  constructor(
    private readonly fetch: (messageId: number) => Promise<T>,
    private readonly ttlMs = TTL_MS,
    private readonly maxEntries = MAX_ENTRIES,
  ) {}

  async get(messageId: number): Promise<T> {
    const found = this.entries.get(messageId);
    if (found && Date.now() - found.at < this.ttlMs) return found.value;

    const pending = this.inFlight.get(messageId);
    if (pending) return pending;

    const request = this.fetch(messageId)
      .then((value) => {
        this.store(messageId, value);
        return value;
      })
      .finally(() => this.inFlight.delete(messageId));
    this.inFlight.set(messageId, request);
    return request;
  }

  /**
   * Drops a stale answer so the next `get` asks Telegram again.
   *
   * Called when a download reports its file reference has expired — the one
   * signal that a cached answer, however fresh by the clock, is already
   * wrong.
   */
  invalidate(messageId: number): void {
    this.entries.delete(messageId);
  }

  private store(messageId: number, value: T): void {
    // Insertion order stands in for recency, the same trade `ReadaheadTracker`
    // makes: good enough for a cache this small, at the cost of nothing more
    // than a Map already pays for.
    if (!this.entries.has(messageId) && this.entries.size >= this.maxEntries) {
      const oldest = this.entries.keys().next().value;
      if (oldest !== undefined) this.entries.delete(oldest);
    }
    this.entries.delete(messageId);
    this.entries.set(messageId, { value, at: Date.now() });
  }
}
