/**
 * Which codecs beyond the everywhere-list this browser decodes.
 *
 * `playable.js` names the codecs that play in every browser; this names the
 * ones that play in *this* one. HEVC is the reason it exists: Safari, Edge,
 * Chrome with a hardware decoder and Firefox on a Linux with VA-API all
 * decode it, and for them re-encoding an HEVC title to H.264 is GPU time
 * spent making a second-generation copy of a picture they could have shown.
 *
 * A codec counts only when **both** ways of playing accept it:
 * `canPlayType` for a file handed straight to `<video>`, and
 * `MediaSource.isTypeSupported` for a conversion fed through hls.js. A
 * browser that could do one and not the other would be promised a stream it
 * then refuses, depending on a fact about the file the viewer cannot see.
 *
 * Asked by exact codec string rather than by name, because a browser's
 * answer to a bare `video/mp4; codecs="hvc1"` is not reliable. Main profile,
 * level 4 is what this library's HEVC actually is.
 */
const PROBES = {
  hevc: 'video/mp4; codecs="hvc1.1.6.L120.90"',
};

/**
 * @param {{canPlayType?: (type: string) => string,
 *          isTypeSupported?: (type: string) => boolean}} [engine]
 *   the browser's two answers; defaults to the real ones, and is a parameter
 *   so the rule can be tested without a browser.
 * @returns {string[]} codec names, as the index spells them
 */
export function browserDecodes(engine = realEngine()) {
  const found = [];
  for (const [name, type] of Object.entries(PROBES)) {
    try {
      const direct = engine.canPlayType?.(type) ?? "";
      const streamed = engine.isTypeSupported?.(type) === true;
      // "maybe" is a browser declining to commit, and a promise built on it
      // is the black rectangle this whole policy exists to avoid.
      if (direct === "probably" && streamed) found.push(name);
    } catch {
      // An engine that throws on the question cannot be relied on for it.
    }
  }
  return found;
}

function realEngine() {
  if (typeof document === "undefined") return {};
  const video = document.createElement("video");
  return {
    canPlayType: (type) => video.canPlayType(type),
    isTypeSupported: (type) => globalThis.MediaSource?.isTypeSupported(type) === true,
  };
}
