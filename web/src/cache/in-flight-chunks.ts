/**
 * Chunks a fetch is bringing in right now, so a second reader of the same
 * bytes waits for that fetch instead of starting its own.
 *
 * A demuxer finding its way around a file — ffmpeg reading an index, then
 * seeking — opens a new range request for every seek, and each one landed on
 * the same missing chunks before the first fetch of them had returned. One
 * 4 MiB run was fetched 47 times over in 45 seconds, every copy queued for a
 * download slot and drawing flood waits that slowed the next.
 */
export class InFlightChunks {
  private readonly chunks = new Map<string, Promise<Uint8Array>>();

  /** The fetch already bringing this chunk, if one is running. */
  get(setId: string, partIdx: number, index: number): Promise<Uint8Array> | undefined {
    return this.chunks.get(key(setId, partIdx, index));
  }

  /**
   * Offers `fetched` — a run's chunks, by index — to any reader asking for
   * one of them before it settles. A failure reaches those readers too:
   * their own fetch of the same bytes would be asking the same question.
   */
  share(setId: string, partIdx: number, first: number, last: number, fetched: Promise<Map<number, Uint8Array>>): void {
    const keys: string[] = [];
    for (let index = first; index <= last; index++) {
      const chunk = fetched.then((chunks) => {
        const bytes = chunks.get(index);
        if (!bytes) throw new Error(`chunk ${index} of part ${partIdx} did not arrive`);
        return bytes;
      });
      // Nobody may be waiting on this chunk; its failure is the fetch's own
      // caller's to report, not an unhandled rejection here.
      chunk.catch(() => {});
      keys.push(key(setId, partIdx, index));
      this.chunks.set(keys.at(-1)!, chunk);
    }
    fetched.finally(() => { for (const k of keys) this.chunks.delete(k); }).catch(() => {});
  }
}

function key(setId: string, partIdx: number, index: number): string {
  return `${setId}/${partIdx}/${index}`;
}
