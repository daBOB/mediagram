/**
 * Which titles a browser can play as they are.
 *
 * Deciding from the catalog, before anything is fetched, lets the page say
 * "this needs transcoding" instead of handing a `<video>` element bytes it
 * will refuse and showing a black rectangle.
 *
 * The rules are deliberately conservative: anything not known to work is
 * reported as needing a transcode. Being wrong that way costs a transcode
 * that was not required; being wrong the other way costs a viewer staring at
 * a player that never starts.
 *
 * Codecs are not the only reason to convert. Direct play hands over the
 * original file, and a film at 13.9 Mbit/s does not fit a household uplink.
 * On the LAN that is free; from outside it is a stall, so the link matters as
 * much as the container.
 *
 * The three sets below are the policy, and the only copy of it. The table in
 * `docs/running-the-player.md` is checked against them by a test, so prose
 * and behaviour cannot drift apart.
 */

/** Containers a browser will open. Matroska is not one of them. */
export const CONTAINERS = new Set(["mp4", "m4v", "webm"]);

/** Video codecs that play essentially everywhere. */
export const VIDEO = new Set(["h264", "avc", "avc1", "vp8", "vp9", "av1"]);

/** Audio codecs that play essentially everywhere. */
export const AUDIO = new Set(["aac", "mp4a", "opus", "vorbis", "mp3"]);

/** Names worth reporting back in the viewer's own words. */
const PRETTY = { hevc: "HEVC" };

/** Other spellings the index may carry for one codec, by canonical name. */
const ALIASES = { h265: "hevc" };

const normalize = (codec) => {
  const name = (codec ?? "").toLowerCase().trim();
  return ALIASES[name] ?? name;
};

/**
 * Video codecs a browser may decode beyond `VIDEO`, and the only ones a page
 * may claim. The server reads the same list when a transcode request names
 * them, so a request cannot talk it into copying anything outside it.
 */
export const NEGOTIABLE = new Set(["hevc"]);

/**
 * The pictures a browser's "yes" actually covers.
 *
 * `codec-support.js` asks about 8-bit Main at level 4, which is what 1080p
 * SDR is. HDR is Main 10 and Dolby Vision more besides, and 2160p is level 5:
 * a browser can decode the one and not the others, and its answer about the
 * first says nothing about them. Those stay converted — an encode that plays
 * is better than a copy that dies in the decoder.
 */
const NEGOTIATED_QUALITIES = new Set(["480p", "576p", "720p", "1080p"]);

function withinProbe(profile) {
  return (profile.hdr ?? "").toUpperCase() === "SDR" && NEGOTIATED_QUALITIES.has(profile.quality ?? "");
}

/**
 * Whether the picture plays as it is: `"everywhere"`, `"negotiated"` for a
 * codec this browser said it decodes, or `false`.
 *
 * `VIDEO` plays everywhere; `decodes` is what this particular browser said it
 * also plays — see `codec-support.js`. Only `NEGOTIABLE` names count, so a
 * list that arrived from outside cannot widen the policy past what it knows.
 */
function decodable(video, profile, decodes) {
  if (VIDEO.has(video)) return "everywhere";
  if (!NEGOTIABLE.has(video) || !withinProbe(profile)) return false;
  for (const name of decodes ?? []) {
    if (normalize(name) === video) return "negotiated";
  }
  return false;
}

/** The set's average bitrate in bits per second, or `null` if unmeasurable. */
function bitrateOf(profile) {
  const bytes = Number(profile.total);
  const seconds = Number(profile.duration);
  if (!Number.isFinite(bytes) || !Number.isFinite(seconds) || seconds <= 0) return null;
  return (bytes * 8) / seconds;
}

/**
 * @param {{container: string, vcodec: string|null, acodec: string|null,
 *          quality?: string|null, hdr?: string|null,
 *          total?: number, duration?: number|null}} profile
 * @param {{remote?: boolean, maxBitrate?: number, decodes?: Iterable<string>}} [link]
 *   how it will travel, and what the browser at the far end decodes
 * @returns {import("./playable.js").PlaybackDecision}
 */
export function decidePlayback(profile, link = {}) {
  const container = normalize(profile.container);
  const video = normalize(profile.vcodec);
  const audio = normalize(profile.acodec);
  const reasons = [];
  /**
   * Which of the four is at fault, beside the sentence saying so.
   *
   * The sentence is for the viewer and the flags are for the encoder, and
   * they are produced together here so there is still only one copy of this
   * policy. A transcode that exists because of the container or the audio
   * does not have to touch the video — see `video-copy.ts`, which reads
   * these and nothing else.
   */
  const blocking = { container: false, video: false, audio: false, bitrate: false };

  if (!CONTAINERS.has(container)) {
    blocking.container = true;
    reasons.push(container === "mkv" ? "Matroska container" : `${container || "unknown"} container`);
  }
  const picture = decodable(video, profile, link.decodes);
  if (picture === false) {
    blocking.video = true;
    reasons.push(`${PRETTY[video] ?? (video || "unknown")} video`);
  } else if (picture === "negotiated" && !blocking.container) {
    /**
     * Never handed over as it is, only repackaged.
     *
     * An mp4 of HEVC may be tagged `hev1`, which Safari and Chrome refuse,
     * and the index cannot say which tag a file carries. Repackaging retags
     * it `hvc1` and costs a remux, not an encode; direct play of the wrong
     * tag costs a black picture with nothing to fall back to. So the box is
     * what is blamed, and the picture is still carried across untouched.
     */
    blocking.container = true;
    reasons.push(`${PRETTY[video]} video`);
  }
  if (!AUDIO.has(audio)) {
    blocking.audio = true;
    reasons.push(`${PRETTY[audio] ?? (audio || "unknown")} audio`);
  }

  // A set whose duration is unknown cannot be measured, and refusing it on a
  // guess would convert things that were fine.
  const bitrate = link.remote && link.maxBitrate ? bitrateOf(profile) : null;
  if (bitrate !== null && bitrate > link.maxBitrate) {
    blocking.bitrate = true;
    reasons.push(`${(bitrate / 1e6).toFixed(1)} Mbit/s over a remote connection`);
  }

  // `picture` says how the video passed, so a caller that has to treat a
  // negotiated copy differently — its own segment format — asks rather than
  // re-deriving it from the codec name.
  return reasons.length === 0
    ? { kind: "direct", blocking, picture }
    : { kind: "transcode", reason: reasons.join(", "), blocking, picture };
}

/**
 * How the page explains a conversion to the person waiting for it.
 *
 * Here rather than in the player because the sentence and the decision it
 * describes are the same thing, and a wording kept somewhere else drifts
 * away from the rule it is about.
 *
 * Two sentences, because there are two quite different things happening.
 * Re-encoding builds a new picture and takes real time to do it. Re-wrapping
 * moves the original picture into a box the browser will open — the file is
 * untouched, nothing is lost, and it runs many times faster. Calling both
 * "converting" told a viewer to expect the slow one.
 */
export function conversionNote(reason, copied = false) {
  if (!reason) return null;
  return copied
    ? `Repackaging as you watch: ${reason} The picture is untouched.`
    : `Converting as you watch: ${reason}`;
}
