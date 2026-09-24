/** Covers the caption: whose document a state message is. */

import { describe, expect, test } from "bun:test";
import { deviceFromCaption, stateCaption, STATE_MARKER } from "../src/telegram/channel-captions";

describe("a caption this player wrote", () => {
  test("names the device, and reads back as it", () => {
    const caption = stateCaption("b398013d-986b");
    expect(caption.startsWith(STATE_MARKER)).toBe(true);
    expect(deviceFromCaption(caption)).toBe("b398013d-986b");
  });
});

describe("a caption that is not one of ours", () => {
  test("the index's is not a state document", () => {
    // A search matches on words, so the index's caption can come back from a
    // search for this marker. Reading it as a device called nothing would
    // attribute somebody's history to a file full of tables.
    expect(deviceFromCaption("#mlib-index v=2 sets=566")).toBeNull();
  });

  test("nor is a marker with no device on it", () => {
    expect(deviceFromCaption("#mlib-state v=1")).toBeNull();
  });

  test("nor a caption that merely mentions the marker", () => {
    expect(deviceFromCaption("talking about #mlib-state device=x")).toBeNull();
  });

  test("nor nothing at all", () => {
    for (const caption of [undefined, "", "   "]) {
      expect(deviceFromCaption(caption as never)).toBeNull();
    }
  });
});

describe("a device id with something odd in it", () => {
  test("stops at whitespace rather than swallowing the rest", () => {
    expect(deviceFromCaption("#mlib-state v=1 device=laptop and more")).toBe("laptop");
  });

  test("and is found wherever it sits in the caption", () => {
    expect(deviceFromCaption("#mlib-state device=laptop v=1")).toBe("laptop");
  });
});
