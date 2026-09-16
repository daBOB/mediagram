/**
 * Which titles a browser can play as they are.
 *
 * Deciding from the catalog, before anything is fetched, lets the page say
 * "this one needs transcoding" instead of handing a `<video>` element bytes
 * it will refuse and showing a black rectangle.
 *
 * The rules are deliberately conservative: anything not known to work is
 * reported as needing a transcode. Being wrong in that direction costs a
 * transcode that was not required; being wrong the other way costs a viewer
 * staring at a player that never starts.
 */

export interface CodecProfile {
  container: string;
  vcodec: string | null;
  acodec: string | null;
}

export type Playback = { kind: "direct" } | { kind: "transcode"; reason: string };

/** Containers a browser will open. Matroska is not one of them. */
const PLAYABLE_CONTAINERS = new Set(["mp4", "m4v", "webm"]);

/** Video codecs that play essentially everywhere. */
const PLAYABLE_VIDEO = new Set(["h264", "avc", "avc1", "vp8", "vp9", "av1"]);

/** Audio codecs that play essentially everywhere. */
const PLAYABLE_AUDIO = new Set(["aac", "mp4a", "opus", "vorbis", "mp3"]);

/** Names worth reporting back in the viewer's own words. */
const PRETTY: Record<string, string> = {
  hevc: "HEVC",
  h265: "HEVC",
  ac3: "ac3",
  eac3: "eac3",
  dts: "dts",
  truehd: "truehd",
};

function normalize(codec: string | null): string {
  return (codec ?? "").toLowerCase().trim();
}

export function decidePlayback(profile: CodecProfile): Playback {
  const container = normalize(profile.container);
  const video = normalize(profile.vcodec);
  const audio = normalize(profile.acodec);
  const reasons: string[] = [];

  if (!PLAYABLE_CONTAINERS.has(container)) {
    reasons.push(container === "mkv" ? "Matroska container" : `${container} container`);
  }
  if (!PLAYABLE_VIDEO.has(video)) {
    reasons.push(`${PRETTY[video] ?? (video || "unknown")} video`);
  }
  if (!PLAYABLE_AUDIO.has(audio)) {
    reasons.push(`${PRETTY[audio] ?? (audio || "unknown")} audio`);
  }

  return reasons.length === 0
    ? { kind: "direct" }
    : { kind: "transcode", reason: reasons.join(", ") };
}
