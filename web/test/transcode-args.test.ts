/**
 * The ffmpeg command line.
 *
 * Pure and tested because every mistake here is silent. `-g` counts frames,
 * not seconds: `-g 2` at 24 fps makes nearly every frame a keyframe and the
 * bitrate explodes, which is the opposite of what a transcode is for. A
 * segment boundary that misses a keyframe makes seeking stall. A missing
 * bitrate cap saturates the uplink. None of these fail loudly.
 */

import { describe, expect, test } from "bun:test";
import { transcodeArgs, type TranscodeRequest } from "../src/transcode/args";

const base: TranscodeRequest = {
  input: "http://127.0.0.1:8770/api/sets/01SET/stream",
  output: "/work/session/index.m3u8",
  encoder: { kind: "software", name: "libx264" },
  seekSeconds: 0,
  maxrateBits: 8_000_000,
  segmentSeconds: 2,
  frameRate: 24,
};

const argsFor = (over: Partial<TranscodeRequest> = {}) => transcodeArgs({ ...base, ...over });

/** The value following a flag, so tests read as the command does. */
function valueOf(args: string[], flag: string): string | undefined {
  const at = args.indexOf(flag);
  return at === -1 ? undefined : args[at + 1];
}

describe("keyframes", () => {
  test("-g is a frame count, derived from the frame rate", () => {
    // 2-second GOP at 24 fps is 48 frames, not 2.
    expect(valueOf(argsFor(), "-g")).toBe("48");
    expect(valueOf(argsFor({ frameRate: 25 }), "-g")).toBe("50");
    expect(valueOf(argsFor({ frameRate: 60 }), "-g")).toBe("120");
  });

  test("a frame rate we could not read does not produce a nonsense GOP", () => {
    const g = Number(valueOf(argsFor({ frameRate: null }), "-g"));

    expect(g).toBeGreaterThanOrEqual(24);
    expect(g).toBeLessThanOrEqual(300);
  });

  /** Belt and braces: the expression holds even if the frame rate was wrong. */
  test("keyframes are also forced on the segment interval", () => {
    expect(valueOf(argsFor(), "-force_key_frames")).toBe("expr:gte(t,n_forced*2)");
    expect(valueOf(argsFor({ segmentSeconds: 4 }), "-force_key_frames")).toBe(
      "expr:gte(t,n_forced*4)",
    );
  });
});

describe("the bitrate cap", () => {
  test("is stated, because an uncapped transcode saturates the uplink", () => {
    expect(valueOf(argsFor(), "-maxrate")).toBe("8000000");
    expect(valueOf(argsFor(), "-bufsize")).toBe("16000000");
  });

  test("the buffer is twice the cap, so the rate is held over time", () => {
    const args = argsFor({ maxrateBits: 4_000_000 });

    expect(Number(valueOf(args, "-bufsize"))).toBe(2 * Number(valueOf(args, "-maxrate")));
  });
});

describe("seeking", () => {
  /** Before -i, ffmpeg seeks the input; after, it decodes and discards. */
  test("the seek comes before the input", () => {
    const args = argsFor({ seekSeconds: 300 });

    expect(args.indexOf("-ss")).toBeLessThan(args.indexOf("-i"));
    expect(valueOf(args, "-ss")).toBe("300");
  });

  test("no seek means no -ss at all", () => {
    expect(argsFor({ seekSeconds: 0 })).not.toContain("-ss");
  });
});

describe("audio", () => {
  /** A 5.1 AC3 source downmixed badly makes dialogue inaudible on stereo. */
  test("is downmixed to stereo aac deliberately", () => {
    const args = argsFor();

    expect(valueOf(args, "-c:a")).toBe("aac");
    expect(valueOf(args, "-ac")).toBe("2");
  });
});

describe("the HLS output", () => {
  test("segments are the requested length and the playlist keeps them all", () => {
    const args = argsFor();

    expect(valueOf(args, "-hls_time")).toBe("2");
    expect(valueOf(args, "-hls_list_size")).toBe("0");
  });

  /** `vod` is for a finished file; this playlist grows while encoding. */
  test("the playlist is an event, not a finished vod", () => {
    expect(valueOf(argsFor(), "-hls_playlist_type")).toBe("event");
  });

  test("the output path is last", () => {
    expect(argsFor().at(-1)).toBe("/work/session/index.m3u8");
  });
});

describe("encoders", () => {
  test("software encoding names libx264 and nothing hardware-specific", () => {
    const args = argsFor();

    expect(valueOf(args, "-c:v")).toBe("libx264");
    expect(args).not.toContain("-vaapi_device");
  });

  test("vaapi names its device and uploads frames to it", () => {
    const args = argsFor({
      encoder: { kind: "vaapi", name: "h264_vaapi", device: "/dev/dri/renderD128" },
    });

    expect(valueOf(args, "-c:v")).toBe("h264_vaapi");
    expect(valueOf(args, "-vaapi_device")).toBe("/dev/dri/renderD128");
    expect(valueOf(args, "-vf")).toContain("hwupload");
    // The device must be set up before the input it applies to.
    expect(args.indexOf("-vaapi_device")).toBeLessThan(args.indexOf("-i"));
  });
});

describe("what must always be true", () => {
  test("the input is the range server, and it is named once", () => {
    const args = argsFor();

    expect(valueOf(args, "-i")).toBe(base.input);
    expect(args.filter((a) => a === "-i")).toHaveLength(1);
  });

  /** ffmpeg's flags that take no value; everything else must have one, or
   *  the next flag is silently consumed as its argument. */
  const BOOLEAN_FLAGS = new Set(["-y", "-hide_banner", "-sn", "-dn"]);

  test("every flag that takes a value has one", () => {
    const args = argsFor({ seekSeconds: 60 });

    for (const [index, arg] of args.entries()) {
      if (arg.startsWith("-") && !BOOLEAN_FLAGS.has(arg)) {
        expect(args[index + 1]).toBeDefined();
        expect(args[index + 1]!.startsWith("-")).toBe(false);
      }
    }
  });

  test("overwriting is explicit, so a rerun does not hang on a prompt", () => {
    expect(argsFor()).toContain("-y");
  });
});

describe("which streams are transcoded", () => {
  /** Every `-map` value, in order. */
  const mapsOf = (args: string[]) =>
    args.flatMap((arg, index) => (arg === "-map" ? [args[index + 1]!] : []));

  test("exactly one video and one audio stream are chosen", () => {
    // Left to ffmpeg, "best" audio is whichever track has the most channels,
    // which on a film is routinely a commentary or another language.
    expect(mapsOf(argsFor())).toEqual(["0:v:0", "0:a:0"]);
  });

  test("subtitles are not muxed into the segments", () => {
    // A film with a forced subtitle track carries eight cues across two
    // hours. The HLS muxer interleaves by timestamp, so it holds the video
    // back waiting for a subtitle packet that will not arrive for minutes,
    // and the encode stops dead. Subtitles reach the player by their own
    // route, so nothing is lost by dropping them here.
    expect(argsFor()).toContain("-sn");
  });

  test("data streams are dropped too", () => {
    // Attachments and timecode tracks have nowhere to go in an MPEG-TS
    // segment, and refusing them beats failing on them.
    expect(argsFor()).toContain("-dn");
  });

  test("the mapping comes after the input it refers to", () => {
    const args = argsFor();

    expect(args.indexOf("-map")).toBeGreaterThan(args.indexOf("-i"));
  });
});
