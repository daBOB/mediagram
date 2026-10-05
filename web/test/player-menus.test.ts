/**
 * The card's menus: one open at a time, choosing closes, Esc closes only the
 * menu, and a panel opened from a menu closes by the same rules.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { browserEnvironment, Node } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(() => env.restore());

const rows = () => env.node("card-menu").children;
const escape = () => {
  const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
  env.node("player").dispatchEvent(event);
  return event;
};

function mount() {
  let closes = 0;
  const picked: string[] = [];
  const menus = mountPlayerMenus({ onClose: () => { closes++; } });
  const items = () => [
    { value: "1", label: "1×", current: true },
    { value: "1.5", label: "1.5×" },
  ];
  menus.list(env.node("speed"), { items, pick: (value) => picked.push(`speed:${value}`) });
  menus.list(env.node("framing"), { items: () => [{ value: "fit", label: "Fit", current: true }], pick: (value) => picked.push(`framing:${value}`) });
  return { menus, picked, closes: () => closes };
}

describe("a list", () => {
  test("opens above its button with the current value marked, and choosing closes it", () => {
    const { menus, picked, closes } = mount();
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    env.node("speed").fire("click");
    expect(menus.isOpen()).toBe(true);
    expect(env.node("card-menu").hidden).toBe(false);
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("true");
    expect(rows().map((row) => [row.dataset.value, row.textContent, row.getAttribute("aria-pressed")])).toEqual([
      ["1", "1×", "true"], ["1.5", "1.5×", "false"],
    ]);
    rows()[1]!.fire("click");
    expect(picked).toEqual(["speed:1.5"]);
    expect(menus.isOpen()).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    expect(closes()).toBe(1);
  });

  test("only one is open at a time, and pressing the open one's button closes it", () => {
    const { menus } = mount();
    env.node("speed").fire("click");
    env.node("framing").fire("click");
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    expect(env.node("framing").getAttribute("aria-expanded")).toBe("true");
    expect(rows().map((row) => row.dataset.value)).toEqual(["fit"]);
    env.node("framing").fire("click");
    expect(menus.isOpen()).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
  });

  test("Esc closes the menu, takes the key from the dialog and leaves the player open", () => {
    const { menus, picked } = mount();
    const exits: string[] = [];
    Object.assign(env.document, { exitFullscreen: async () => { exits.push("exit"); } });
    env.node("player").open = true;
    env.node("speed").fire("click");
    const event = escape();
    expect(event.defaultPrevented).toBe(true);
    expect(menus.isOpen()).toBe(false);
    expect(picked).toEqual([]);
    expect(env.node("player").open).toBe(true);
    expect(exits).toEqual([]);
  });

  test("Esc with nothing open is left alone, for the dialog to close itself", () => {
    mount();
    expect(escape().defaultPrevented).toBe(false);
  });

  test("a press anywhere else in the player closes it; one on its rows or its button does not", () => {
    const { menus } = mount();
    env.node("speed").fire("click");
    const press = (target: Node) => {
      const event = new Event("pointerdown");
      Object.defineProperty(event, "target", { value: target });
      env.node("player").dispatchEvent(event);
    };
    press(rows()[0]!);
    press(env.node("speed"));
    expect(menus.isOpen()).toBe(true);
    press(env.video);
    expect(menus.isOpen()).toBe(false);
  });
});

describe("a panel", () => {
  test("opens in the list's place, and Esc, its button or another list close it", () => {
    const { menus } = mount();
    const style = env.node("cue-panel");
    style.hidden = true;
    menus.panel(env.node("cc-menu"), style);
    expect(style.hidden).toBe(false);
    expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");
    escape();
    expect(style.hidden).toBe(true);
    menus.panel(env.node("cc-menu"), style);
    env.node("speed").fire("click");
    expect(style.hidden).toBe(true);
    expect(env.node("card-menu").hidden).toBe(false);
    menus.close();
    expect(menus.isOpen()).toBe(false);
  });
});
