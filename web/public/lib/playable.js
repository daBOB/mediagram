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
 */

/** Containers a browser will open. Matroska is not one of them. */
const CONTAINERS = new Set(["mp4", "m4v", "webm"]);

/** Video codecs that play essentially everywhere. */
const VIDEO = new Set(["h264", "avc", "avc1", "vp8", "vp9", "av1"]);

/** Audio codecs that play essentially everywhere. */
const AUDIO = new Set(["aac", "mp4a", "opus", "vorbis", "mp3"]);

/** Names worth reporting back in the viewer's own words. */
const PRETTY = { hevc: "HEVC", h265: "HEVC" };

const normalize = (codec) => (codec ?? "").toLowerCase().trim();

/**
 * @param {{container: string, vcodec: string|null, acodec: string|null}} profile
 * @returns {{kind: "direct"} | {kind: "transcode", reason: string}}
 */
export function decidePlayback(profile) {
  const container = normalize(profile.container);
  const video = normalize(profile.vcodec);
  const audio = normalize(profile.acodec);
  const reasons = [];

  if (!CONTAINERS.has(container)) {
    reasons.push(container === "mkv" ? "Matroska container" : `${container || "unknown"} container`);
  }
  if (!VIDEO.has(video)) reasons.push(`${PRETTY[video] ?? (video || "unknown")} video`);
  if (!AUDIO.has(audio)) reasons.push(`${PRETTY[audio] ?? (audio || "unknown")} audio`);

  return reasons.length === 0
    ? { kind: "direct" }
    : { kind: "transcode", reason: reasons.join(", ") };
}
