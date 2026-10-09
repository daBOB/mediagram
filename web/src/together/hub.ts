/**
 * The web server's side of watch together (`docs/mlib-spec.md` §7b).
 */

export type EditOutcome = "ok" | "gone" | { wait: number };

/** The library channel, as watch together needs it. Every call throws while Telegram is unreachable. */
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
