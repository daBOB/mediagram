/** Types for the playability rules in `playable.js`. */

export type Playback = { kind: "direct" } | { kind: "transcode"; reason: string };

export function decidePlayback(
  profile: {
    container: string;
    vcodec: string | null;
    acodec: string | null;
    /** `720p`, `1080p`, `2160p`…; with `hdr`, what a negotiated codec is trusted for. */
    quality?: string | null;
    /** `SDR`, `HDR10`, `DV`. */
    hdr?: string | null;
    /** The set's size in bytes, for judging whether it fits a remote link. */
    total?: number;
    duration?: number | null;
  },
  link?: {
    /** True when the viewer is not on this machine's own network. */
    remote?: boolean;
    /** What the link is assumed to carry, in bits per second. */
    maxBitrate?: number;
    /** Video codecs this browser decodes beyond `VIDEO`; see `codec-support.js`. */
    decodes?: Iterable<string>;
  },
): Playback & {
  blocking: { container: boolean; video: boolean; audio: boolean; bitrate: boolean };
  /** How the picture passes: everywhere, only because this browser said so, or not at all. */
  picture: "everywhere" | "negotiated" | false;
};

/** The containers, video codecs and audio codecs that direct-play. */
export const CONTAINERS: ReadonlySet<string>;
export const VIDEO: ReadonlySet<string>;
export const AUDIO: ReadonlySet<string>;
/** Video codecs a browser may claim to decode; nothing outside it is negotiated. */
export const NEGOTIABLE: ReadonlySet<string>;
