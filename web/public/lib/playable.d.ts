/** Types for the playability rules in `playable.js`. */

export type Playback = { kind: "direct" } | { kind: "transcode"; reason: string };

export function decidePlayback(profile: {
  container: string;
  vcodec: string | null;
  acodec: string | null;
}): Playback;
