import { describe, expect, test } from "bun:test";
import { PATIENCE_MS, READY_SECONDS, autoplayReady } from "../public/lib/autoplay.js";

const at = (over: Record<string, unknown> = {}) => ({
  ahead: 0,
  remaining: 1200,
  waitedMs: 0,
  ...over,
});

describe("an unattended start", () => {
  test("waits for the minute", () => {
    expect(autoplayReady(at({ ahead: 59 }))).toBe(false);
    expect(autoplayReady(at({ ahead: READY_SECONDS }))).toBe(true);
  });

  test("does not wait for a minute a short title cannot hold", () => {
    // A forty second lesson never buffers sixty seconds ahead of itself, and
    // waiting for it would hang until the patience ran out every time.
    expect(autoplayReady(at({ ahead: 40, remaining: 40 }))).toBe(true);
    expect(autoplayReady(at({ ahead: 20, remaining: 40 }))).toBe(false);
  });

  test("goes anyway once it has waited long enough", () => {
    // A slow encoder delays the next episode; it must not cancel it.
    expect(autoplayReady(at({ ahead: 3, waitedMs: PATIENCE_MS - 1 }))).toBe(false);
    expect(autoplayReady(at({ ahead: 3, waitedMs: PATIENCE_MS }))).toBe(true);
  });

  test("keeps waiting while the runtime is unknown and the buffer is short", () => {
    expect(autoplayReady(at({ ahead: 10, remaining: null }))).toBe(false);
    // The minute still ends the wait, runtime or no runtime.
    expect(autoplayReady(at({ ahead: 61, remaining: null }))).toBe(true);
  });

  test("is not fooled by a remaining of zero into starting on an empty buffer", () => {
    // `remaining` is 0 at the very end of the previous title's clock; it must
    // not read as "everything left is buffered" when nothing is.
    expect(autoplayReady(at({ ahead: 0, remaining: 0 }))).toBe(false);
  });
});
