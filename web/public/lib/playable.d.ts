/** Types for the playability rules in `playable.js`. */

export type Playback = { kind: "direct" } | { kind: "transcode"; reason: string };

export function decidePlayback(
  profile: {
    container: string;
    vcodec: string | null;
    acodec: string | null;
    /** The set's size in bytes, for judging whether it fits a remote link. */
    total?: number;
    duration?: number | null;
  },
  link?: {
    /** True when the viewer is not on this machine's own network. */
    remote?: boolean;
    /** What the link is assumed to carry, in bits per second. */
    maxBitrate?: number;
  },
): Playback;

/** The containers, video codecs and audio codecs that direct-play. */
export const CONTAINERS: ReadonlySet<string>;
export const VIDEO: ReadonlySet<string>;
export const AUDIO: ReadonlySet<string>;
