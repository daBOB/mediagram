/**
 * The Framing button: its label follows the picture whether the menu or `z`
 * changed it, the choice is remembered for the show, and a remembered value
 * this player does not offer falls back to Fit.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { mountFramingControl } from "../public/lib/playback/framing-control.js";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { browserEnvironment, chooseInMenu, markedInMenu } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(() => env.restore());

function mount(remembered: string | null = null) {
  const kept: string[] = [];
  const framing = mountFramingControl({
    video: env.video,
    button: env.node("framing"),
    menus: mountPlayerMenus(),
    recall: () => remembered,
    remember: (_name: string, value: string) => kept.push(value),
  });
  return { framing, kept };
}

test("z steps the picture round the cycle and the label follows it", () => {
  const { framing, kept } = mount();
  framing.recall();
  expect(env.node("framing").textContent).toBe("Fit");
  framing.cycle();
  expect(env.node("framing").textContent).toBe("Fill");
  expect(env.video.style.objectFit).toBe("cover");
  expect(kept).toEqual(["fill"]);
});

test("the menu offers every framing with the current one marked, and choosing applies and remembers it", () => {
  const { framing, kept } = mount("fill");
  framing.recall();
  env.node("framing").fire("click");
  expect(env.node("card-menu").children.map((row) => row.textContent)).toEqual(["Fit", "Fill", "16:9", "4:3"]);
  env.node("framing").fire("click");
  expect(markedInMenu(env.node, "framing")).toBe("fill");
  chooseInMenu(env.node, "framing", "4:3");
  expect(env.node("framing").textContent).toBe("4:3");
  expect(env.video.style.aspectRatio).toBe(String(4 / 3));
  expect(kept).toEqual(["4:3"]);
});

test("a remembered framing this player does not offer opens as Fit", () => {
  const { framing } = mount("sideways");
  framing.recall();
  expect(env.node("framing").textContent).toBe("Fit");
  expect(markedInMenu(env.node, "framing")).toBe("fit");
});
