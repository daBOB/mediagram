/**
 * Turning planned reads into bytes.
 *
 * Telegram's `upload.getFile` will not accept an offset that is not 4 KiB
 * aligned (`OFFSET_INVALID`), nor a limit that is not a 4 KiB multiple
 * dividing 1 MiB (`LIMIT_INVALID`) — both verified against the live API,
 * both contradicting teleproto's own type documentation. So a read starts at
 * the aligned offset the plan chose and discards the head.
 */

import type { PartLocation } from "../catalog";
import { requestSizeFor, type Step } from "../range";
import type { ByteSource } from "../routes";
import type { Telegram } from "./client";

export class TelegramSource implements ByteSource {
  constructor(private readonly telegram: Telegram) {}

  stream(locations: PartLocation[], steps: Step[]): ReadableStream<Uint8Array> {
    const bytes = this.bytesOf(locations, steps);
    // Pull-based, so a slow viewer slows the download rather than filling
    // memory with a set that may be several gigabytes.
    return new ReadableStream<Uint8Array>({
      async pull(controller) {
        try {
          const { done, value } = await bytes.next();
          if (done) controller.close();
          else controller.enqueue(value);
        } catch (error) {
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
    locations: PartLocation[],
    steps: Step[],
  ): AsyncGenerator<Uint8Array, void, unknown> {
    for (const step of steps) {
      const location = locations.find((l) => l.span.idx === step.partIdx);
      if (!location) throw new Error(`part ${step.partIdx} has no message`);

      const media = await this.telegram.partMedia(location.messageId);
      let headDrop = step.headDrop;
      let owed = step.take;

      for await (const chunk of this.telegram.client.iterDownload(media as never, {
        offset: BigInt(step.offset) as never,
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
