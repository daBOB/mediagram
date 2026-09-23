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
  audioTrack: 0,
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

describe("audio", () => {
  test("the chosen stream is named, and the first one is the default", () => {
    // `-map 0:a` without an ordinal lets ffmpeg pick "best", which on a film
    // means the most channels — routinely a commentary or another language.
    expect(argsFor()).toContain("0:a:0");
    expect(argsFor({ audioTrack: 3 })).toContain("0:a:3");
  });

  test("only ever one audio stream, whichever it is", () => {
    const args = argsFor({ audioTrack: 2 });
    expect(args.filter((arg) => arg.startsWith("0:a:"))).toEqual(["0:a:2"]);
  });

  test("the video mapping is untouched by the audio choice", () => {
    expect(argsFor({ audioTrack: 5 })).toContain("0:v:0");
  });
});

describe("carrying the picture across instead of encoding it", () => {
  test("the video is copied, and no encoder is named", () => {
    const args = argsFor({ copyVideo: true });
    expect(valueOf(args, "-c:v")).toBe("copy");
    expect(args).not.toContain("libx264");
    expect(args).not.toContain("h264_vaapi");
  });

  test("nothing that controls an encode survives", () => {
    // Every one of these is about an encode that is not happening, and
    // `-force_key_frames` in particular cannot apply: there is no encoder
    // left to ask for a keyframe.
    const args = argsFor({ copyVideo: true });
    for (const flag of ["-maxrate", "-bufsize", "-g", "-force_key_frames", "-vf"]) {
      expect(args).not.toContain(flag);
    }
  });

  test("a VAAPI device is not set up for a job that never reaches the GPU", () => {
    const args = argsFor({
      copyVideo: true,
      encoder: { kind: "vaapi", name: "h264_vaapi", device: "/dev/dri/renderD128" },
    });
    expect(args).not.toContain("-vaapi_device");
  });

  test("the audio is still chosen, re-encoded and downmixed", () => {
    // The whole point of a copy is often that the audio had to change.
    const args = argsFor({ copyVideo: true, audioTrack: 2 });
    expect(args).toContain("0:a:2");
    expect(valueOf(args, "-c:a")).toBe("aac");
    expect(valueOf(args, "-ac")).toBe("2");
  });

  test("and it is still segmented the same way", () => {
    const args = argsFor({ copyVideo: true });
    expect(valueOf(args, "-hls_time")).toBe("2");
    expect(valueOf(args, "-hls_playlist_type")).toBe("event");
  });

  test("seeking still happens before the input", () => {
    const args = argsFor({ copyVideo: true, seekSeconds: 900 });
    expect(args.indexOf("-ss")).toBeLessThan(args.indexOf("-i"));
  });

  test("a seek starts the sound on the picture's keyframe, not the exact second", () => {
    // Trimmed to the second, the sound starts after the copied picture and
    // fMP4 says so only in an edit list Firefox on Android ignores: the
    // sound then plays ahead of the lips by the keyframe gap.
    const args = argsFor({ copyVideo: true, seekSeconds: 900 });
    expect(args.indexOf("-noaccurate_seek")).toBeGreaterThanOrEqual(0);
    expect(args.indexOf("-noaccurate_seek")).toBeLessThan(args.indexOf("-i"));
  });

  test("an encode still seeks to the exact second", () => {
    // An encoder can begin anywhere, so both tracks already start together.
    expect(argsFor({ seekSeconds: 900 })).not.toContain("-noaccurate_seek");
    expect(argsFor({ copyVideo: true, seekSeconds: 0 })).not.toContain("-noaccurate_seek");
  });

  test("absent means encode, which is what every older caller meant", () => {
    expect(valueOf(argsFor(), "-c:v")).toBe("libx264");
    expect(valueOf(argsFor({ copyVideo: false }), "-c:v")).toBe("libx264");
  });
});

describe("an HEVC picture carried across", () => {
  const hevc = argsFor({ copyVideo: true, hevcCopy: true });

  test("is written as fMP4, the only form hls.js plays HEVC from", () => {
    expect(valueOf(hevc, "-hls_segment_type")).toBe("fmp4");
    expect(hevc.at(-1)).toBe(base.output);
  });

  test("is tagged hvc1, which Safari and Chrome accept and hev1 is not", () => {
    expect(valueOf(hevc, "-tag:v")).toBe("hvc1");
    expect(valueOf(hevc, "-c:v")).toBe("copy");
  });

  test("every other session keeps the MPEG-TS it has always had", () => {
    for (const args of [argsFor(), argsFor({ copyVideo: true })]) {
      expect(args).not.toContain("-hls_segment_type");
      expect(args).not.toContain("-tag:v");
    }
  });
});
