/**
 * Library changes as Telegram pushes them: the MTProto half of `updates.ts`.
 *
 * Every other session of the account learns of a message sent, edited or
 * pinned in the library channel within milliseconds — measured in
 * `plans/260922-2222-telegram-push-updates/reports/`. This turns those raw
 * updates into the classifier's shape and hands on what survives the
 * debouncer. It never syncs anything itself: the caller runs its ordinary
 * round, so a missed or dropped update costs exactly what it did before this
 * existed — waiting for the timer.
 *
 * Nothing replays what arrived while the connection was down: a
 * `StringSession` keeps no update state to catch up from, and the spike found
 * grammers' catch-up replays no channel messages either. So whoever starts
 * listening runs one ordinary round straight after.
 */

import { Api } from "teleproto";
import { Raw } from "teleproto/events";
import { failureMessage } from "../failure-message";

import { Debouncer, classify, type ChannelUpdate, type ClassifyContext, type LibraryEvent } from "./updates";

/** A bare channel id, whichever integer type teleproto handed over. */
function idOf(value: unknown): number {
  return Number(String(value));
}

function channelOf(peer: unknown): number | null {
  return peer instanceof Api.PeerChannel ? idOf(peer.channelId) : null;
}

/** A raw teleproto update, reduced to what classifying needs; `null` for any other kind. */
export function toChannelUpdate(update: unknown): ChannelUpdate | null {
  if (update instanceof Api.UpdateNewChannelMessage || update instanceof Api.UpdateEditChannelMessage) {
    const kind = update instanceof Api.UpdateNewChannelMessage ? "new" : "edit";
    const message: unknown = update.message;
    if (message instanceof Api.MessageService) {
      const channel = channelOf(message.peerId);
      return channel === null ? null : { kind, channel, service: true };
    }
    if (message instanceof Api.Message) {
      const channel = channelOf(message.peerId);
      return channel === null ? null : { kind, channel, caption: message.message };
    }
    return null;
  }
  if (update instanceof Api.UpdatePinnedChannelMessages) {
    return { kind: "pinned", channel: idOf(update.channelId), pinned: update.pinned === true };
  }
  if (update instanceof Api.UpdateDeleteChannelMessages) {
    return { kind: "delete", channel: idOf(update.channelId) };
  }
  return null;
}

/** The part of a teleproto client this needs, so a test can stand in for it. */
export interface UpdateSource {
  addEventHandler(handler: (update: unknown) => void, filter: Raw): void;
  removeEventHandler(handler: (update: unknown) => void, filter: Raw): void;
}

export interface Clock {
  now(): number;
  setTimeout(run: () => void, ms: number): unknown;
  clearTimeout(timer: unknown): void;
}

const realClock: Clock = {
  now: () => Date.now(),
  setTimeout: (run, ms) => setTimeout(run, ms),
  clearTimeout: (timer) => clearTimeout(timer as ReturnType<typeof setTimeout>),
};

/**
 * Calls `onEvent` for each library change worth a round, at most once per
 * kind per window. Answers the function that stops listening.
 */
export function listenForLibraryEvents(
  source: UpdateSource,
  context: ClassifyContext,
  onEvent: (event: LibraryEvent) => void,
  clock: Clock = realClock,
  windowMs?: number,
): () => void {
  const debouncer = new Debouncer(windowMs);
  let timer: unknown = null;

  // Armed for the earliest due event only. A later offer can never fall due
  // sooner, because each kind's window starts at its first event.
  const arm = () => {
    const due = debouncer.nextDue();
    if (timer !== null || due === null) return;
    timer = clock.setTimeout(release, Math.max(0, due - clock.now()));
  };
  const release = () => {
    timer = null;
    for (const event of debouncer.take(clock.now())) {
      try {
        onEvent(event);
      } catch (error) {
        // A hint that failed to act is the same as one never received.
        console.warn(`updates: acting on ${event} failed: ${failureMessage(error)}`);
      }
    }
    arm();
  };
  const handler = (raw: unknown) => {
    const update = toChannelUpdate(raw);
    const event = update && classify(update, context);
    if (!event) return;
    debouncer.offer(event, clock.now());
    arm();
  };

  const filter = new Raw({});
  source.addEventHandler(handler, filter);
  return () => {
    source.removeEventHandler(handler, filter);
    if (timer !== null) clock.clearTimeout(timer);
    timer = null;
  };
}
