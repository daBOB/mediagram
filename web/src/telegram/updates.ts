/**
 * Which channel updates are worth acting on, and how often.
 *
 * Telegram pushes every change in the library channel to every connected
 * session within milliseconds (measured, `plans/260922-2222-telegram-push-updates`).
 * An update is only a hint: whoever acts on one still runs the ordinary sync
 * or refresh round, which reads the pin list itself. So this decides nothing
 * about data — only whether a round is worth starting.
 *
 * Both halves are pure, and pinned by `test/fixtures/channel-updates/`, which
 * the Android core's port reads too. A case that only one of them passes is a
 * bug in whichever disagrees with the web.
 */

import { INDEX_MARKER, STATE_MARKER, deviceFromCaption } from "./channel-captions";

/**
 * One raw update, reduced to what classifying needs. The MTProto adapter
 * builds these; nothing here knows teleproto's types.
 */
export interface ChannelUpdate {
  kind: "new" | "edit" | "pinned" | "delete" | "other";
  /** Bare channel id (no -100 prefix). */
  channel: number;
  /** The message's text or caption, for `new` and `edit`. */
  caption?: string;
  /** A service message, such as "pinned a message". */
  service?: boolean;
  /** For `pinned`: pinned, or unpinned. */
  pinned?: boolean;
}

export type LibraryEvent = "state" | "index";

export interface ClassifyContext {
  channel: number;
  ownDevice: string;
}

export function classify(update: ChannelUpdate, context: ClassifyContext): LibraryEvent | null {
  if (update.channel !== context.channel) return null;

  if (update.kind === "pinned") {
    // An unpin arrives in the same burst as the pin of its replacement; the
    // pin alone says the library moved.
    return update.pinned === true ? "index" : null;
  }
  if ((update.kind !== "new" && update.kind !== "edit") || update.service) return null;

  const caption = update.caption ?? "";
  if (caption.startsWith(`${STATE_MARKER} `)) {
    const device = deviceFromCaption(caption);
    // This device's own write comes back to it on some libraries; it is not news.
    return device !== null && device !== context.ownDevice ? "state" : null;
  }
  // An index is published by sending a new one, never by editing an old one.
  if (update.kind === "new" && caption.startsWith(INDEX_MARKER)) return "index";
  return null;
}

/** Released in this order when both are due at once, so every port agrees. */
const ORDER: LibraryEvent[] = ["state", "index"];

/**
 * At most one event per kind per window, released at the window's end.
 *
 * The window is timed from the first event, not reset by later ones: an
 * upload that pushes an index every few seconds for an hour would otherwise
 * never let one through.
 */
export class Debouncer {
  private readonly due = new Map<LibraryEvent, number>();

  constructor(private readonly windowMs = 5000) {}

  offer(event: LibraryEvent, now: number): void {
    if (!this.due.has(event)) this.due.set(event, now + this.windowMs);
  }

  /** Events whose window has ended, removed from the pending set. */
  take(now: number): LibraryEvent[] {
    const ready = ORDER.filter((event) => (this.due.get(event) ?? Infinity) <= now);
    for (const event of ready) this.due.delete(event);
    return ready;
  }

  /** When the next event falls due, for arming a timer; `null` when idle. */
  nextDue(): number | null {
    return this.due.size === 0 ? null : Math.min(...this.due.values());
  }
}
