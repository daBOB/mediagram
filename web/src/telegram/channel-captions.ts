/** Caption conventions shared by pure channel policy and Telegram adapters. */
export const INDEX_MARKER = "#mlib-index";

/** What a search looks for, and what every state caption begins with. */
export const STATE_MARKER = "#mlib-state";
const STATE_VERSION = 1;

/** `#mlib-state v=1 device=…`, which is also how a reader knows whose it is. */
export function stateCaption(device: string): string {
  return `${STATE_MARKER} v=${STATE_VERSION} device=${device}`;
}

/**
 * Whose document a caption says this is.
 *
 * Returns `null` for anything that is not a state caption, including the
 * index's — a search for a marker matches on words, and being strict here is
 * what stops a malformed caption being read as a device called nothing.
 */
export function deviceFromCaption(caption: string | undefined): string | null {
  if (typeof caption !== "string" || !caption.startsWith(`${STATE_MARKER} `)) return null;
  const found = /\bdevice=(\S+)/.exec(caption);
  return found ? found[1]! : null;
}
