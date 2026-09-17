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
const PRETTY = { hevc: "HEVC", h265: "HEVC" };

const normalize = (codec) => (codec ?? "").toLowerCase().trim();

/** The set's average bitrate in bits per second, or `null` if unmeasurable. */
function bitrateOf(profile) {
  const bytes = Number(profile.total);
  const seconds = Number(profile.duration);
  if (!Number.isFinite(bytes) || !Number.isFinite(seconds) || seconds <= 0) return null;
  return (bytes * 8) / seconds;
}

/**
 * @param {{container: string, vcodec: string|null, acodec: string|null,
 *          total?: number, duration?: number|null}} profile
 * @param {{remote?: boolean, maxBitrate?: number}} [link] how it will travel
 * @returns {{kind: "direct"} | {kind: "transcode", reason: string}}
 */
export function decidePlayback(profile, link = {}) {
  const container = normalize(profile.container);
  const video = normalize(profile.vcodec);
  const audio = normalize(profile.acodec);
  const reasons = [];

  if (!CONTAINERS.has(container)) {
    reasons.push(container === "mkv" ? "Matroska container" : `${container || "unknown"} container`);
  }
  if (!VIDEO.has(video)) reasons.push(`${PRETTY[video] ?? (video || "unknown")} video`);
  if (!AUDIO.has(audio)) reasons.push(`${PRETTY[audio] ?? (audio || "unknown")} audio`);

  // A set whose duration is unknown cannot be measured, and refusing it on a
  // guess would convert things that were fine.
  const bitrate = link.remote && link.maxBitrate ? bitrateOf(profile) : null;
  if (bitrate !== null && bitrate > link.maxBitrate) {
    reasons.push(`${(bitrate / 1e6).toFixed(1)} Mbit/s over a remote connection`);
  }

  return reasons.length === 0
    ? { kind: "direct" }
    : { kind: "transcode", reason: reasons.join(", ") };
}
