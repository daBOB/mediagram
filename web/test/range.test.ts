/**
 * Range maths for the player's virtual file.
 *
 * A port of `crates/mediagram/src/serve/range.rs`'s tests, because the two
 * implementations must agree byte for byte: the Rust server is the oracle the
 * differential test compares against. Same fixture — the real film uploaded
 * and verified against the channel, two parts of 3,758,096,384 and
 * 3,253,467,079 bytes.
 *
 * What differs from Rust is the seek unit. grammers skips whole 512 KiB
 * chunks; teleproto takes a byte offset that Telegram requires to be 4 KiB
 * aligned. So a step carries an aligned offset and the head to discard, and
 * the waste is at most 4 KiB rather than 512 KiB.
 */

import { describe, expect, test } from "bun:test";
import {
  ALIGN,
  planReads,
  parseRange,
  rangeLength,
  requestSizeFor,
  totalSize,
  type ByteRange,
  type PartSpan,
  type Step,
} from "../src/range";

const P0 = 3_758_096_384;
const P1 = 3_253_467_079;
const TOTAL = P0 + P1;

const film = (): PartSpan[] => [
  { idx: 0, off: 0, len: P0 },
  { idx: 1, off: P0, len: P1 },
];

/** parseRange returns a discriminated result; these unwrap it in tests. */
function ok(header: string, total = TOTAL): ByteRange {
  const result = parseRange(header, total);
  if (!result.ok) throw new Error(`expected a range from "${header}", got ${result.error}`);
  return result.range;
}
function err(header: string, total = TOTAL): string {
  const result = parseRange(header, total);
  if (result.ok) throw new Error(`expected an error from "${header}"`);
  return result.error;
}

describe("the virtual file", () => {
  test("is the sum of its parts", () => {
    expect(totalSize(film())).toBe(TOTAL);
    expect(TOTAL).toBe(7_011_563_463);
  });

  test("is smaller than the precision of a JS number", () => {
    // Byte offsets are plain numbers rather than BigInt. That is only safe
    // while a set cannot reach 2^53 bytes, which is 9 petabytes.
    expect(TOTAL).toBeLessThan(Number.MAX_SAFE_INTEGER);
  });
});

describe("parsing a Range header", () => {
  test("an open-ended range runs to the end", () => {
    expect(ok("bytes=0-")).toEqual({ start: 0, end: TOTAL - 1 });
  });

  test("a closed range is inclusive as HTTP defines it", () => {
    const range = ok("bytes=0-499");
    expect(range).toEqual({ start: 0, end: 499 });
    expect(rangeLength(range)).toBe(500);
  });

  test("a suffix range counts back from the end", () => {
    expect(ok("bytes=-500")).toEqual({ start: TOTAL - 500, end: TOTAL - 1 });
  });

  test("a suffix larger than the file clamps to the whole file", () => {
    expect(ok(`bytes=-${TOTAL + 10}`).start).toBe(0);
  });

  test("an end past the file clamps rather than failing", () => {
    expect(ok(`bytes=0-${TOTAL + 1000}`).end).toBe(TOTAL - 1);
  });

  test("a start past the end of the file is unsatisfiable", () => {
    expect(err("bytes=99999999999-")).toBe("unsatisfiable");
    expect(err(`bytes=${TOTAL}-`)).toBe("unsatisfiable");
  });

  test("a backwards range is unsatisfiable", () => {
    expect(err("bytes=500-100")).toBe("unsatisfiable");
  });

  test("a zero-length suffix is unsatisfiable", () => {
    expect(err("bytes=-0")).toBe("unsatisfiable");
  });

  test("malformed headers are malformed, not unsatisfiable", () => {
    for (const bad of ["", "bytes=", "bytes=abc-def", "items=0-1", "bytes=-", "0-100"]) {
      expect(err(bad)).toBe("malformed");
    }
  });

  test("a multi-range request is refused", () => {
    expect(err("bytes=0-99,200-299")).toBe("multi-range");
  });

  test("an empty file satisfies nothing", () => {
    expect(err("bytes=0-", 0)).toBe("unsatisfiable");
  });
});

describe("planning the reads", () => {
  test("a range inside the first part reads only that part", () => {
    const steps = planReads(film(), { start: 1_000_000, end: 1_999_999 });

    expect(steps).toHaveLength(1);
    expect(steps[0]!.partIdx).toBe(0);
    expect(steps[0]!.take).toBe(1_000_000);
    // 1,000,000 rounded down to a 4 KiB boundary is 999,424.
    expect(steps[0]!.offset).toBe(999_424);
    expect(steps[0]!.headDrop).toBe(576);
  });

  test("a range inside the second part offsets from that part's start", () => {
    const start = P0 + 8192; // exactly two alignment units into part 1
    const steps = planReads(film(), { start, end: start + 999 });

    expect(steps).toHaveLength(1);
    expect(steps[0]!.partIdx).toBe(1);
    expect(steps[0]!.offset).toBe(8192);
    expect(steps[0]!.headDrop).toBe(0);
    expect(steps[0]!.take).toBe(1000);
  });

  /** The case that only exists because files are split across messages. */
  test("a range spanning the part boundary reads both parts", () => {
    const range: ByteRange = { start: P0 - 100, end: P0 + 99 };
    const steps = planReads(film(), range);

    expect(steps).toHaveLength(2);
    expect(steps[0]!.partIdx).toBe(0);
    expect(steps[0]!.take).toBe(100);
    expect(steps[1]!.partIdx).toBe(1);
    expect(steps[1]!.offset).toBe(0);
    expect(steps[1]!.headDrop).toBe(0);
    expect(steps[1]!.take).toBe(100);
    expect(steps.reduce((n, s) => n + s.take, 0)).toBe(rangeLength(range));
  });

  test("a whole-file request reads every part completely", () => {
    const steps = planReads(film(), { start: 0, end: TOTAL - 1 });

    expect(steps).toHaveLength(2);
    expect(steps[0]!.take).toBe(P0);
    expect(steps[1]!.take).toBe(P1);
  });

  test("the last byte of the file is reachable", () => {
    const steps = planReads(film(), { start: TOTAL - 1, end: TOTAL - 1 });

    expect(steps).toHaveLength(1);
    expect(steps[0]!.partIdx).toBe(1);
    expect(steps[0]!.take).toBe(1);
  });

  test("a single-part set works like a plain file", () => {
    const parts: PartSpan[] = [{ idx: 0, off: 0, len: 69_136_013 }];
    const steps = planReads(parts, ok("bytes=0-", 69_136_013));

    expect(steps).toHaveLength(1);
    expect(steps[0]!.take).toBe(69_136_013);
  });

  test("an empty set plans nothing", () => {
    expect(totalSize([])).toBe(0);
    expect(planReads([], { start: 0, end: 0 })).toEqual([]);
  });

  /**
   * Whatever the range, the planned reads must cover it exactly and stay
   * legal. A wrong byte here is a corrupt video, not an error message.
   */
  test("planned reads cover the request exactly and stay within the parts", () => {
    const parts = film();
    const cases: [number, number][] = [
      [0, 0],
      [0, ALIGN - 1],
      [ALIGN - 1, ALIGN],
      [P0 - 1, P0],
      [P0, P0],
      [P0 - ALIGN, P0 + ALIGN],
      [12_345_678, 87_654_321],
      [TOTAL - 2, TOTAL - 1],
    ];

    for (const [start, end] of cases) {
      const range: ByteRange = { start, end };
      const steps = planReads(parts, range);
      const covered = steps.reduce((n, s) => n + s.take, 0);
      expect(covered).toBe(rangeLength(range));

      for (const step of steps) {
        const part = parts.find((p) => p.idx === step.partIdx)!;
        expect(step.offset % ALIGN).toBe(0);
        expect(step.headDrop).toBeLessThan(ALIGN);
        expect(step.offset + step.headDrop + step.take).toBeLessThanOrEqual(part.len);
      }
    }
  });
});

describe("choosing a request size", () => {
  /**
   * Telegram rejects a limit that is not a 4 KiB multiple dividing 1 MiB.
   * Found by running it: the type definitions claim otherwise.
   */
  test("every legal size is a 4 KiB multiple that divides 1 MiB", () => {
    for (const bytes of [1, 4096, 4097, 100_000, 524_288, 999_999]) {
      const size = requestSizeFor(bytes);
      expect(size % ALIGN).toBe(0);
      expect(1_048_576 % size).toBe(0);
      expect(size).toBeLessThanOrEqual(524_288);
    }
  });

  test("a small read costs one alignment unit, not half a megabyte", () => {
    expect(requestSizeFor(1)).toBe(4096);
    expect(requestSizeFor(4096)).toBe(4096);
    expect(requestSizeFor(4097)).toBe(8192);
  });

  test("a large read uses the biggest legal chunk", () => {
    expect(requestSizeFor(1_000_000)).toBe(524_288);
  });
});
