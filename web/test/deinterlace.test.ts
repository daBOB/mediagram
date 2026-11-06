/**
 * The deinterlace switch: the canvas is shown only while on, takes the video's
 * layout, follows frames, and draws cues; the framing menu carries the row and
 * the show remembers it. GL stays behind a fake renderer.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { mountDeinterlace } from "../public/lib/playback/deinterlace.js";
import { mountFramingControl } from "../public/lib/playback/framing-control.js";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { Node, browserEnvironment, chooseInMenu } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(() => env.restore());

function mount(options: { renderer?: boolean; remembered?: Record<string, string> } = {}) {
  const video = env.video as any;
  video.videoWidth = 720;
  video.videoHeight = 576;
  video.readyState = 4;
  const callbacks: Array<() => void> = [];
  video.requestVideoFrameCallback = (run: () => void) => callbacks.push(run);
  video.cancelVideoFrameCallback = (id: number) => { callbacks[id - 1] = () => {}; };
  const canvas = new Node("CANVAS") as any;
  canvas.hidden = true;
  const cues = new Node("P") as any;
  const drawn: number[] = [];
  let cleared = 0;
  const deinterlace = mountDeinterlace({
    video, canvas, cues,
    createRenderer: () => options.renderer === false ? null : { draw: () => { drawn.push(canvas.height); }, clear: () => { cleared++; } },
  });
  const kept: Array<[string, string]> = [];
  const framing = mountFramingControl({
    video, button: env.node("framing"), menus: mountPlayerMenus(), deinterlace,
    recall: (name: string) => options.remembered?.[name] ?? null,
    remember: (name: string, value: string) => kept.push([name, value]),
  });
  /** One presented video frame. */
  const present = () => callbacks.splice(0).forEach((run) => run());
  return { deinterlace, framing, canvas, cues, drawn, kept, present, video, cleared: () => cleared };
}

const rows = () => env.node("card-menu").children.map((row) => row.textContent);

test("the canvas is hidden and idle until switched on, sized to the picture, and drawn per frame", () => {
  const { deinterlace, canvas, drawn, present } = mount();
  expect(canvas.hidden).toBe(true);
  present();
  expect(drawn).toEqual([]);
  deinterlace.set(true);
  expect(canvas.hidden).toBe(false);
  expect([canvas.width, canvas.height]).toEqual([720, 576]);
  expect(drawn).toHaveLength(1);
  present();
  present();
  expect(drawn).toHaveLength(3);
  deinterlace.set(false);
  expect(canvas.hidden).toBe(true);
  present();
  expect(drawn).toHaveLength(3);
});

test("a seek, a size change and a source change redraw or clear", () => {
  const { deinterlace, canvas, drawn, video, cleared } = mount();
  deinterlace.set(true);
  video.fire("seeked");
  expect(drawn).toHaveLength(2);
  video.videoWidth = 1024;
  video.videoHeight = 480;
  video.fire("resize");
  expect([canvas.width, canvas.height]).toEqual([1024, 480]);
  video.fire("emptied");
  expect(cleared()).toBe(1);
});

test("without a renderer nothing is offered and nothing turns on", () => {
  const { framing, deinterlace, canvas } = mount({ renderer: false, remembered: { deinterlace: "on" } });
  framing.recall();
  expect(deinterlace.supported).toBe(false);
  expect(canvas.hidden).toBe(true);
  env.node("framing").fire("click");
  expect(rows()).toEqual(["Fit", "Fill", "16:9", "4:3"]);
});

test("the Framing menu ends in a Deinterlace row that toggles, shows the state and is remembered or forgotten", () => {
  const { framing, kept, canvas } = mount();
  framing.recall();
  env.node("framing").fire("click");
  expect(rows()).toEqual(["Fit", "Fill", "16:9", "4:3", "Deinterlace: Off"]);
  env.node("framing").fire("click");
  chooseInMenu(env.node, "framing", "deinterlace");
  expect(canvas.hidden).toBe(false);
  expect(kept).toEqual([["deinterlace", "on"]]);
  env.node("framing").fire("click");
  expect(rows()[4]).toBe("Deinterlace: On");
  expect(env.node("card-menu").children[4]!.getAttribute("aria-pressed")).toBe("true");
  env.node("framing").fire("click");
  chooseInMenu(env.node, "framing", "deinterlace");
  expect(canvas.hidden).toBe(true);
  expect(kept[1]).toEqual(["deinterlace", ""]);
});

test("a remembered show opens deinterlaced, but not an HDR title, which keeps its choice", () => {
  const { framing, canvas, kept } = mount({ remembered: { deinterlace: "on" } });
  framing.recall();
  expect(canvas.hidden).toBe(false);
  framing.recall({ hdr: true });
  expect(canvas.hidden).toBe(true);
  env.node("framing").fire("click");
  expect(rows()).toEqual(["Fit", "Fill", "16:9", "4:3"]);
  expect(kept).toEqual([]);
});

test("the canvas copies the video's framing styles, and the next title starts from its own", () => {
  const { framing, canvas } = mount();
  (env.video as any).parentElement = Object.assign(new Node(), { clientWidth: 1600, clientHeight: 900 });
  framing.recall();
  framing.cycle();
  expect(canvas.style.objectFit).toBe("cover");
  chooseInMenu(env.node, "framing", "4:3");
  expect(canvas.style.aspectRatio).toBe(String(4 / 3));
  expect(canvas.style.width).toBe(env.video.style.width);
  expect(canvas.style.width).not.toBe("");
  chooseInMenu(env.node, "framing", "fit");
  expect([canvas.style.aspectRatio, canvas.style.width]).toEqual(["", ""]);
});

test("while on, the showing tracks' active cues are drawn above the canvas", () => {
  const { deinterlace, cues, video } = mount();
  const track = Object.assign(new EventTarget(), { mode: "showing", activeCues: [{ text: "Hello" }] });
  video.textTracks.push(track);
  deinterlace.set(true);
  expect(cues.children.map((line: any) => line.textContent)).toEqual(["Hello"]);
  expect(cues.hidden).toBe(false);
  track.activeCues = [];
  track.dispatchEvent(new Event("cuechange"));
  expect(cues.hidden).toBe(true);
  track.mode = "disabled";
  track.activeCues = [{ text: "Hidden" }];
  track.dispatchEvent(new Event("cuechange"));
  expect(cues.hidden).toBe(true);
});
