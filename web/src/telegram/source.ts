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
import { DownloadGate } from "./download-gate";
import type { ByteSource } from "../http/stream";
import type { Telegram } from "./client";

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
    private readonly telegram: Telegram,
    private readonly reader?: CachedReader,
  ) {}

  /** What has gone wrong upstream since startup. */
  stats(): { failedReads: number } {
    return { failedReads: this.failedReads };
  }

  stream(locations: PartLocation[], steps: Step[], setId: string): ReadableStream<Uint8Array> {
    const bytes = this.bytesOf(setId, locations, steps);
    // Captured rather than `this`: the stream's callbacks are plain functions
    // and are not called with this instance as their receiver.
    const failed = () => {
      this.failedReads += 1;
    };
    // Pull-based, so a slow viewer slows the download rather than filling
    // memory with a set that may be several gigabytes.
    return new ReadableStream<Uint8Array>({
      async pull(controller) {
        try {
          const { done, value } = await bytes.next();
          if (done) controller.close();
          else controller.enqueue(value);
        } catch (error) {
          failed();
          controller.error(error);
        }
      },
      cancel() {
        // The viewer seeked or closed the tab: stop paying for bytes nobody
        // will read.
        void bytes.return(undefined);
      },
    });
  }

  private async *bytesOf(
    setId: string,
    locations: PartLocation[],
    steps: Step[],
  ): AsyncGenerator<Uint8Array, void, unknown> {
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
          fetch: partFetcher(this.telegram, location.messageId),
        });
        continue;
      }

      const media = await this.telegram.partMedia(location.messageId);
      let headDrop = step.headDrop;
      let owed = step.take;

      for await (const chunk of this.telegram.client.iterDownload(media as never, {
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
        yield piece;
        if (owed === 0) break;
      }

      // The response already promised a length. Serving fewer bytes silently
      // would hand the viewer a truncated file that looks complete.
      if (owed > 0) {
        throw new Error(`download ended with ${owed} bytes of part ${step.partIdx} still owed`);
      }
    }
  }
}

/**
 * Fetches one aligned range of a part straight from Telegram.
 *
 * Handed to the cache as its way of filling a miss, so the cache knows
 * nothing about MTProto and this file stays the only place that does.
 */
export function partFetcher(telegram: Telegram, messageId: number) {
  return (offset: number, length: number): Promise<Uint8Array> =>
    downloads.run(() => fetchPart(telegram, messageId, offset, length));
}

/**
 * One gate for the whole process: the limit is Telegram's, per account, so
 * every reader of every title shares it.
 */
const downloads = new DownloadGate();

async function fetchPart(
  telegram: Telegram,
  messageId: number,
  offset: number,
  length: number,
): Promise<Uint8Array> {
  const media = await telegram.partMedia(messageId);
  const out: Uint8Array[] = [];
  let got = 0;
  for await (const chunk of telegram.client.iterDownload(media as never, {
    offset: helpers.returnBigInt(offset) as never,
    requestSize: requestSizeFor(length),
  })) {
    out.push(chunk);
    got += chunk.length;
    if (got >= length) break;
  }
  const joined = new Uint8Array(got);
  let at = 0;
  for (const piece of out) {
    joined.set(piece, at);
    at += piece.length;
  }
  return joined.subarray(0, Math.min(length, got));
}
