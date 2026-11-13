/**
 * The ffmpeg command line for a clip.
 *
 * Pure and tested because a wrong flag here yields a file that looks fine
 * and posts badly: a seek after `-i` decodes the whole film first, an HDR
 * picture left untouched turns grey on a phone, an odd width is refused by
 * Instagram's encoder.
 */

import { describe, expect, test } from "bun:test";
import { CLIP_MAX_SECONDS, clipArgs, type ClipRequest } from "../src/transcode/clip-args";
import { READ_BURST_SECONDS, READ_RATE } from "../src/transcode/args";

const base: ClipRequest = {
  input: "http://127.0.0.1:8770/api/sets/01SET/stream",
  output: "/work/clip.mp4",
  from: 3600,
  to: 3630,
  audioTrack: 0,
  hdr: null,
  pacedReads: false,
};

const argsFor = (over: Partial<ClipRequest> = {}) => clipArgs({ ...base, ...over });
const valueOf = (args: string[], flag: string) => args[args.indexOf(flag) + 1];

describe("clipArgs", () => {
  test("seeks before the input and cuts the exact length", () => {
    const args = argsFor();
    expect(valueOf(args, "-ss")).toBe("3600");
    expect(args.indexOf("-ss")).toBeLessThan(args.indexOf("-i"));
    expect(valueOf(args, "-t")).toBe("30");
    expect(args.indexOf("-t")).toBeGreaterThan(args.indexOf("-i"));
  });

  test("maps the first video and the chosen audio", () => {
    const args = argsFor({ audioTrack: 2 });
    expect(args).toContain("0:v:0");
    expect(args).toContain("0:a:2");
    expect(args).toContain("-sn");
    expect(args).toContain("-dn");
  });

  test("encodes H.264 and stereo AAC into a fast-start MP4", () => {
    const args = argsFor();
    expect(valueOf(args, "-c:v")).toBe("libx264");
    expect(valueOf(args, "-crf")).toBe("18");
    expect(valueOf(args, "-preset")).toBe("medium");
    expect(valueOf(args, "-pix_fmt")).toBe("yuv420p");
    expect(valueOf(args, "-c:a")).toBe("aac");
    expect(valueOf(args, "-b:a")).toBe("160k");
    expect(valueOf(args, "-ac")).toBe("2");
    expect(valueOf(args, "-movflags")).toBe("+faststart");
    expect(args[args.length - 1]).toBe("/work/clip.mp4");
  });

  test("tags the output as BT.709", () => {
    const args = argsFor();
    expect(valueOf(args, "-colorspace")).toBe("bt709");
    expect(valueOf(args, "-color_primaries")).toBe("bt709");
    expect(valueOf(args, "-color_trc")).toBe("bt709");
  });

  test("fits within 1080p and never scales up", () => {
    const vf = valueOf(argsFor(), "-vf");
    expect(vf).toContain("scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease");
    expect(vf).toContain("force_divisible_by=2");
    expect(vf).toContain("format=yuv420p");
  });

  test("leaves SDR untouched by any tone mapping", () => {
    const vf = valueOf(argsFor({ hdr: "SDR" }), "-vf");
    expect(vf).not.toContain("tonemap");
    expect(vf).not.toContain("libplacebo");
  });

  test("widens anamorphic pixels to square before the fit, keeping the width even", () => {
    for (const hdr of [null, "HDR10", "DV"] as const) {
      const vf = valueOf(argsFor({ hdr }), "-vf")!;
      expect(vf.startsWith("scale=trunc(iw*sar/2)*2:ih,setsar=1,")).toBe(true);
      expect(vf.indexOf("setsar=1")).toBeLessThan(vf.indexOf("scale=w="));
    }
  });

  test.each(["HDR10", "HLG"] as const)("scales %s to fit before it tone maps, then converts to 8-bit BT.709", (hdr) => {
    const vf = valueOf(argsFor({ hdr }), "-vf")!;
    const order = ["scale=w=", "zscale=t=linear", "format=gbrpf32le", "tonemap=tonemap=hable", "zscale=t=bt709:m=bt709:p=bt709", "format=yuv420p"];
    const at = order.map((step) => vf.indexOf(step));
    expect(at.every((index) => index >= 0)).toBe(true);
    expect([...at].sort((a, b) => a - b)).toEqual(at);
    // Nothing between the fit and the linearize drops the source's 10 bits.
    expect(vf.slice(at[0]!, at[1]!)).not.toContain("format=");
  });

  test("sends Dolby Vision through libplacebo", () => {
    const vf = valueOf(argsFor({ hdr: "DV" }), "-vf")!;
    expect(vf).toContain("libplacebo=");
    expect(vf).toContain("colorspace=bt709:color_primaries=bt709:color_trc=bt709");
    expect(vf).not.toContain("tonemap=");
  });

  test("paces reads only when the probe allowed it", () => {
    const paced = argsFor({ pacedReads: true });
    expect(valueOf(paced, "-readrate")).toBe(String(READ_RATE));
    expect(valueOf(paced, "-readrate_initial_burst")).toBe(String(READ_BURST_SECONDS));
    expect(paced.indexOf("-readrate")).toBeLessThan(paced.indexOf("-i"));
    expect(argsFor()).not.toContain("-readrate");
  });

  test("a clip is capped at three minutes", () => {
    expect(CLIP_MAX_SECONDS).toBe(180);
  });
});
