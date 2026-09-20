/** Covers `thumbs/args`: the command that makes one sprite sheet. */

import { describe, expect, test } from "bun:test";
import { sheetArgs } from "../src/thumbs/args";
import { spritePlan } from "../public/lib/sprite-plan.js";

const argsFor = (duration: number) =>
  sheetArgs({
    input: "http://127.0.0.1:8770/api/sets/01SET/stream",
    output: "/work/01SET.jpg",
    plan: spritePlan(duration)!,
  });

const valueOf = (args: string[], flag: string) => args[args.indexOf(flag) + 1];

describe("reading as little as possible", () => {
  test("decodes keyframes only", () => {
    // A full decode of a 59-minute film to take 118 pictures is almost all
    // wasted work. This is what makes the whole feature affordable.
    expect(valueOf(argsFor(3539), "-skip_frame")).toBe("nokey");
  });

  test("and asks for keyframes before the input, or it is not an input option", () => {
    const args = argsFor(3539);
    expect(args.indexOf("-skip_frame")).toBeLessThan(args.indexOf("-i"));
  });
});

describe("the filter chain", () => {
  test("takes one frame per interval", () => {
    expect(valueOf(argsFor(3539), "-vf")).toContain("fps=1/30");
    expect(valueOf(argsFor(252), "-vf")).toContain("fps=1/3");
  });

  test("letterboxes rather than squashing", () => {
    // A stretched preview does not look like the film, which is worse than a
    // small one that does.
    const filter = valueOf(argsFor(3539), "-vf")!;
    expect(filter).toContain("force_original_aspect_ratio=decrease");
    expect(filter).toContain("pad=160:90");
  });

  test("tiles to the grid the plan decided", () => {
    const plan = spritePlan(3539)!;
    expect(valueOf(argsFor(3539), "-vf")).toContain(`tile=${plan.columns}x${plan.rows}`);
  });

  test("and the order is fps, scale, pad, tile", () => {
    // Tiling before scaling would build one enormous image and then shrink it.
    const filter = valueOf(argsFor(3539), "-vf")!;
    const at = (part: string) => filter.indexOf(part);
    expect(at("fps=")).toBeLessThan(at("scale="));
    expect(at("scale=")).toBeLessThan(at("pad="));
    expect(at("pad=")).toBeLessThan(at("tile="));
  });
});

describe("what comes out", () => {
  test("exactly one image", () => {
    expect(valueOf(argsFor(3539), "-frames:v")).toBe("1");
  });

  test("at the output the caller asked for", () => {
    expect(argsFor(3539).at(-1)).toBe("/work/01SET.jpg");
  });

  test("a short title still makes a valid single-row grid", () => {
    const plan = spritePlan(10)!;
    expect(valueOf(argsFor(10), "-vf")).toContain(`tile=${plan.columns}x1`);
  });
});
