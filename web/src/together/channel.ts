/**
 * The library channel, as watch together needs it (`docs/mlib-spec.md` §7b).
 * `telegram/together-channel.ts` is the real one; the hub's tests use a map.
 */

export type EditOutcome = "ok" | "gone" | { wait: number };

/** Every call but `available` throws while Telegram is unreachable. */
export interface TogetherChannel {
  /** Signed in and connected. */
  available(): boolean;
  /** Session messages among the newest in the channel. */
  recent(): Promise<{ id: number; text: string }[]>;
  /** A message's current text; `null` once it is deleted. */
  read(id: number): Promise<string | null>;
  send(text: string): Promise<number>;
  edit(id: number, text: string): Promise<EditOutcome>;
  remove(id: number): Promise<void>;
}
