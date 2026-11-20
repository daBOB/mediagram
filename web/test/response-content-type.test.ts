import { expect, test } from "bun:test";
import { contentType } from "../src/response";

test("an MP3 is served as audio/mpeg, or a browser will not try direct play", () => {
  expect(contentType("mp3")).toBe("audio/mpeg");
});
