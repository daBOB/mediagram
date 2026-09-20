/**
 * The ffmpeg command line for one transcode.
 *
 * Pure and tested because every mistake here is silent. `-g` counts frames,
 * not seconds: `-g 2` at 24 fps makes nearly every frame a keyframe and the
 * bitrate explodes, which is the opposite of what a transcode is for. A
 * segment boundary that misses a keyframe makes seeking stall. A missing
 * bitrate cap saturates the uplink. None of these announce themselves.
 */

/** Which encoder to use, and what it needs to work. */
export type Encoder =
  | { kind: "software"; name: "libx264" }
  | { kind: "vaapi"; name: "h264_vaapi"; device: string };

export interface TranscodeRequest {
  /** The Range server's URL for this set. */
  input: string;
  /** Where the playlist goes; segments land beside it. */
  output: string;
  encoder: Encoder;
  /** Where to start. 0 means the beginning. */
  seekSeconds: number;
  /** Ceiling for the output, in bits per second. */
  maxrateBits: number;
  /**
   * Which audio stream to take, as ffmpeg's `0:a:N` ordinal.
   *
   * Chosen by the viewer, defaulting to the first. It stays a single explicit
   * stream for the reason spelled out at the `-map` below; what changed is
   * only who decides which one.
   */
  audioTrack: number;
  segmentSeconds: number;
  /** The source's frame rate, or `null` when it could not be read. */
  frameRate: number | null;
  /**
   * Carry the video across untouched instead of encoding it.
   *
   * `video-copy.ts` decides, from the same policy the player uses to decide
   * whether a title needs converting at all. Absent means encode, which is
   * what every caller written before this did.
   */
  copyVideo?: boolean;
}

/**
 * A GOP length when the source frame rate is unknown.
 *
 * Two seconds at 25 fps: wrong for an odd frame rate, but wrong by a little,
 * and `-force_key_frames` below enforces the real boundary regardless.
 */
const ASSUMED_FRAME_RATE = 25;

export function transcodeArgs(request: TranscodeRequest): string[] {
  const fps = request.frameRate ?? ASSUMED_FRAME_RATE;
  // Frames, not seconds. This is the line the plan got wrong.
  const gop = Math.round(fps * request.segmentSeconds);

  const args: string[] = ["-hide_banner", "-loglevel", "error", "-y"];

  // The device has to exist before the input that will be uploaded to it —
  // and is pointless for a copy, which never reaches the GPU.
  if (request.encoder.kind === "vaapi" && request.copyVideo !== true) {
    args.push("-vaapi_device", request.encoder.device);
  }

  if (request.seekSeconds > 0) {
    // Before -i: ffmpeg seeks the input rather than decoding and discarding
    // everything up to that point, which for a film is the whole difference.
    args.push("-ss", String(request.seekSeconds));
  }

  args.push("-i", request.input);

  // Spelled out rather than left to ffmpeg's default selection, for two
  // reasons. "Best" audio means the track with the most channels, which on a
  // film is routinely a commentary or another language, so the stream is
  // always named — either the first or the one the viewer chose. And a
  // subtitle track muxed alongside stalls the encode outright: a forced track
  // carries a handful of cues across two hours, and the HLS muxer holds the
  // video back waiting for the next one. Subtitles reach the player by their
  // own route.
  args.push("-map", "0:v:0", "-map", `0:a:${request.audioTrack}`, "-sn", "-dn");

  if (request.copyVideo === true) {
    /**
     * The picture goes across as it is.
     *
     * Everything the encoding branch does below is about controlling an
     * encode that is not happening: the VAAPI upload filter, the bitrate cap
     * and its buffer, the GOP length, the forced keyframes. None of them
     * apply to bytes being moved, and `-force_key_frames` in particular
     * cannot apply — there is no encoder to ask for a keyframe.
     *
     * So segments break at the keyframes the file already has, which makes
     * them uneven. hls.js does not mind; `-hls_time` becomes a target rather
     * than a promise. `-ss` before `-i` lands on the nearest preceding
     * keyframe for the same reason, which is a second or so early at worst.
     */
    args.push("-c:v", "copy");
  } else {
    if (request.encoder.kind === "vaapi") {
      args.push("-vf", "format=nv12,hwupload");
    }
    args.push(
      "-c:v",
      request.encoder.name,
      "-maxrate",
      String(request.maxrateBits),
      // Twice the cap, so the rate is held across a couple of seconds rather
      // than instant by instant.
      "-bufsize",
      String(request.maxrateBits * 2),
      "-g",
      String(gop),
      // Belt and braces: even if the frame rate was misread, segments still
      // begin on keyframes, and a segment that does not is one that stalls.
      "-force_key_frames",
      `expr:gte(t,n_forced*${request.segmentSeconds})`,
    );
  }

  args.push(
    // Downmixed on purpose: an AC3 5.1 source folded badly leaves the centre
    // channel dominant and dialogue hard to hear on stereo speakers.
    "-c:a",
    "aac",
    "-b:a",
    "160k",
    "-ac",
    "2",
    "-hls_time",
    String(request.segmentSeconds),
    "-hls_list_size",
    "0",
    // `event`, not `vod`: the playlist grows while encoding continues.
    "-hls_playlist_type",
    "event",
    request.output,
  );
  return args;
}
