// The avatar this phone asks for when it joins: the one its player picked
// themselves, so a favourite that was taken once is asked for again next time
// rather than replaced by the random one the host handed out meanwhile.

const KEY = "spieltisch.avatar";

export function rememberedAvatar(): string | undefined {
  try {
    return localStorage.getItem(KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

/** `onlyIfNone`: keep an existing choice (for the avatar the host assigned on a first join). */
export function rememberAvatar(id: string, onlyIfNone = false) {
  try {
    if (!onlyIfNone || !localStorage.getItem(KEY)) localStorage.setItem(KEY, id);
  } catch {
    // no storage: the phone just gets a free avatar next time
  }
}
