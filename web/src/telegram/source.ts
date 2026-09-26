/**
 * Turning planned reads into bytes.
 *
 * Telegram's `upload.getFile` will not accept an offset that is not 4 KiB
 * aligned (`OFFSET_INVALID`), nor a limit that is not a 4 KiB multiple
 * dividing 1 MiB (`LIMIT_INVALID`) — both verified against the live API,
 * both contradicting teleproto's own type documentation. So a read starts at
 * the aligned offset the plan chose and discards the head.
 *
 * Offsets go in through `returnBigInt` rather than as a native `bigint`: the
 * client advances the offset between requests with big-integer arithmetic,
 * so a native one serves the first request and then throws. Anything larger
 * than one request size takes several, which is nearly everything.
 */

import { helpers } from "teleproto";

import type { CachedReader } from "../cache/reader";
import type { PartLocation } from "../catalog";
import { requestSizeFor, type Step } from "../range";
import type { ByteSource } from "../http/stream";
import type { TelegramConnection } from "./connection";
import { connectionFetcher, isFileReferenceExpired } from "./part-fetch";

export { partFetcher, connectionFetcher } from "./part-fetch";

export class TelegramSource implements ByteSource {
  /**
   * Reads that ended in an error rather than in bytes.
   *
   * Counted because this is the one failure a viewer feels and cannot see:
   * a stream that errors mid-film looks to the browser like the file ended,
   * and to the viewer like the player stopped for no reason. A cancel is not
   * one of these — a seek cancels a read every time, and that is the system
   * working.
   */
  private failedReads = 0;

  /**
   * `reader` is optional: without it every byte comes from Telegram, which is
   * correct but pays for the same bytes on every replay and every seek back.
   */
  constructor(
    private readonly connection: TelegramConnection,
    private readonly reader?: CachedReader,
  ) {}

  /** Byte streams that failed since startup, including unavailable cached bytes. */
  stats(): { failedReads: number } {
    return { failedReads: this.failedReads };
  }

  stream(locations: PartLocation[], steps: Step[], setId: string): ReadableStream<Uint8Array> {
    return this.streamBytes(this.bytesOf(setId, locations, steps, false));
  }

  /** Disk-only delivery: neither a cache miss nor sequential reads contact Telegram. */
  streamCached(locations: PartLocation[], steps: Step[], setId: string): ReadableStream<Uint8Array> {
    return this.streamBytes(this.bytesOf(setId, locations, steps, true));
  }

  private streamBytes(bytes: AsyncGenerator<Uint8Array, void, unknown>): ReadableStream<Uint8Array> {
    let cancelled = false;
    let pulling: Promise<IteratorResult<Uint8Array, void>> | undefined;
    // Captured rather than `this`: the stream's callbacks are plain functions
    // and are not called with this instance as their receiver.
    const failed = () => {
      this.failedReads += 1;
    };
    // Pull-based, so a slow viewer slows the download rather than filling
    // memory with a set that may be several gigabytes.
    return new ReadableStream<Uint8Array>({
      async pull(controller) {
        const next = bytes.next();
        pulling = next;
        try {
          const { done, value } = await next;
          if (cancelled) return;
          if (done) controller.close();
          else controller.enqueue(value);
        } catch (error) {
          if (cancelled) return; // Cancellation owns this result and its cleanup.
          failed();
          controller.error(error);
        } finally {
          pulling = undefined;
        }
      },
      async cancel() {
        cancelled = true;
        // return() queues behind next(). That pull may already be unwinding
        // upstream cleanup, so await both and retain either failure.
        const outcomes = await Promise.allSettled([pulling, bytes.return(undefined)]);
        for (const outcome of outcomes) {
          if (outcome.status === "rejected") throw outcome.reason;
        }
      },
    });
  }

  private async *bytesOf(
    setId: string,
    locations: PartLocation[],
    steps: Step[],
    cacheOnly: boolean,
  ): AsyncGenerator<Uint8Array, void, unknown> {
    if (cacheOnly && !this.reader) throw new Error("cached streaming is unavailable");
    for (const step of steps) {
      const location = locations.find((l) => l.span.idx === step.partIdx);
      if (!location) throw new Error(`part ${step.partIdx} has no message`);

      if (this.reader) {
        // Through the cache: it fetches what it lacks and keeps what it
        // fetches, so a second pass over the same bytes costs nothing.
        // Streamed, not collected: the response has already promised a
        // length and must start sending immediately, and a whole-file request
        // would otherwise be held in memory.
        yield* this.reader.readStream({
          setId,
          partIdx: step.partIdx,
          start: step.offset + step.headDrop,
          length: step.take,
          partLength: location.span.len,
          fetch: cacheOnly ? undefined : connectionFetcher(this.connection, location.messageId),
        });
        continue;
      }

      // Resolved once per step rather than once per `bytesOf` call: a step
      // that starts while a restart is already parked at the gate should see
      // the client it settles on, not fail for asking too early.
      const startGeneration = this.connection.generation;
      let telegram = await this.connection.ready();
      if (!telegram) throw new Error("this part is not cached, and the player is signed out");
      let media = await telegram.partMedia(location.messageId);
      let headDrop = step.headDrop;
      let owed = step.take;
      let yieldedAny = false;
      let retried = false;

      for (;;) {
        try {
          for await (const chunk of telegram.client.iterDownload(media as never, {
            offset: helpers.returnBigInt(step.offset) as never,
            requestSize: requestSizeFor(headDrop + step.take),
          })) {
            let piece: Uint8Array = chunk;

            if (headDrop > 0) {
              const dropped = Math.min(headDrop, piece.length);
              piece = piece.subarray(dropped);
              headDrop -= dropped;
            }
            if (piece.length === 0) continue;
            if (piece.length > owed) piece = piece.subarray(0, owed);

            owed -= piece.length;
            yieldedAny = true;
            yield piece;
            if (owed === 0) break;
          }
          break;
        } catch (error) {
          // A reference that expired before this step sent anything can be
          // retried with a fresh one, and so can a step caught mid-restart —
          // the client it started with is gone, not merely stale. Once bytes
          // are already on the wire a retry would duplicate or skip them, so
          // only the failure can surface from there.
          const swapped = this.connection.generation !== startGeneration;
          if (yieldedAny || retried || !(isFileReferenceExpired(error) || swapped)) throw error;
          retried = true;
          if (swapped) {
            telegram = await this.connection.ready();
            if (!telegram) throw new Error("this part is not cached, and the player is signed out");
          } else {
            telegram.forgetPartMedia(location.messageId);
          }
          media = await telegram.partMedia(location.messageId);
          headDrop = step.headDrop;
          owed = step.take;
        }
      }

      // The response already promised a length. Serving fewer bytes silently
      // would hand the viewer a truncated file that looks complete.
      if (owed > 0) {
        throw new Error(`download ended with ${owed} bytes of part ${step.partIdx} still owed`);
      }
    }
  }
}

