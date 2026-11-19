import { describe, expect, test } from "bun:test";
import { gpuDecodes } from "../src/transcode/vaapi-decode";

describe("which sources the GPU decodes", () => {
  test("HEVC, H.264 and the rest go to the GPU, which falls back to software on its own", () => {
    for (const codec of ["hevc", "h264", "vp9", "mpeg2video", "vc1", "HEVC"]) expect(gpuDecodes(codec)).toBe(true);
  });

  test("AV1 stays on the CPU: with -hwaccel ffmpeg picks an AV1 decoder that has no software path", () => {
    expect(gpuDecodes("av1")).toBe(false);
    expect(gpuDecodes("AV1")).toBe(false);
  });

  test("an unknown codec might be AV1, so it stays on the CPU too", () => {
    for (const codec of [null, undefined, ""]) expect(gpuDecodes(codec)).toBe(false);
  });
});
