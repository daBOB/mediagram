/**
 * The one seam that sees every request: a subclass overriding `invoke`.
 *
 * `iterDownload` and `partMedia` both call `client.invoke` on the instance,
 * so this is where a byte, a latency or a failure is counted without a
 * second copy of the reasoning that decides what a request *is* — that stays
 * in `source.ts` and `client.ts`.
 */

import { Api, TelegramClient, events } from "teleproto";
import { Logger } from "teleproto/extensions";
import { UpdateConnectionState } from "teleproto/network";
import { LinkStats } from "./link-stats";

const FLOOD_LOG = /^Sleeping for (\d+)s on flood wait/;

/**
 * The default `Logger` print, reproduced exactly (`extensions/Logger.ts`'s
 * own `log`), because setting `handler` replaces it rather than adding to it.
 */
const COLOR: Record<string, string> = { error: "\x1b[31m", warn: "\x1b[35m", info: "\x1b[33m", debug: "\x1b[36m" };
const RESET = "\x1b[0m";

/** A logger whose default terminal output is unchanged, but that also feeds `stats`. */
export function countingLogger(stats: Pick<LinkStats, "flood">): Logger {
  const logger = new Logger("info" as never);
  logger.handler = (record) => {
    const match = FLOOD_LOG.exec(record.message);
    if (match) stats.flood(Number(match[1]));
    console.log((COLOR[record.level] ?? "") + logger.format(record.message, record.level) + RESET);
    if (record.error !== undefined) console.error(record.error);
  };
  return logger;
}

export class MeasuredClient extends TelegramClient {
  readonly stats: LinkStats;

  /**
   * Every argument passed through unchanged, so a mismatch with the base
   * constructor's own contract is the typecheck's to catch. `baseLogger` is
   * added only when the caller left it out: this is the one place the
   * subclass's own reasoning belongs, and a caller supplying its own logger
   * — as `login/setup.ts` does — keeps it.
   */
  constructor(...args: ConstructorParameters<typeof TelegramClient>) {
    const stats = new LinkStats();
    const [session, apiId, apiHash, options] = args;
    super(session, apiId, apiHash, { baseLogger: countingLogger(stats), ...options });
    this.stats = stats;
  }

  override async invoke<R extends Api.AnyRequest>(request: R, dcId?: number): Promise<R["__response"]> {
    // Undefined means the client's own, home DC — the same label
    // `Telegram.connected` already keeps for its own reasons.
    const dc = dcId ?? this.session.dcId;
    const startedAt = performance.now();
    try {
      const result = await super.invoke(request, dcId);
      const bytes = result instanceof Api.upload.File ? result.bytes.length : 0;
      this.stats.request(dc, performance.now() - startedAt, bytes);
      return result;
    } catch (error) {
      this.stats.failed(dc, error);
      throw error;
    }
  }

  /** Counts a reconnect on the main connection; the first `connected` does not count as one. */
  watchReconnects(): void {
    let seenFirstConnect = false;
    this.addEventHandler((update: UpdateConnectionState) => {
      if (update.state !== UpdateConnectionState.connected) return;
      if (seenFirstConnect) this.stats.reconnect();
      seenFirstConnect = true;
    }, new events.Raw({ types: [UpdateConnectionState] }));
  }
}
