// The avatar this phone asks for when it joins: the one its player picked
// themselves, so a favourite that was taken once is asked for again next time
// rather than replaced by the random one the host handed out meanwhile.

import { memory } from "./app-bridge";

const KEY = "spieltisch.avatar";

export function rememberedAvatar(): string | undefined {
  return memory.get(KEY) ?? undefined;
}

/** `onlyIfNone`: keep an existing choice (for the avatar the host assigned on a first join). */
export function rememberAvatar(id: string, onlyIfNone = false) {
  if (!onlyIfNone || !memory.get(KEY)) memory.set(KEY, id);
}
