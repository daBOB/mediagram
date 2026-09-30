/** Covers `settings-page.js`'s Profile panel: the subtitle language row. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { descendants } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
});
afterEach(async () => {
  await settle();
  env.restore();
});

/** Renders Settings and switches to the Profile tab, where the row lives. */
function subtitleSelect(profile: { name: string; kids?: boolean } | null) {
  const main = env.node("main");
  renderSettings(main, { profile, switchProfile: () => {}, systemVisible: false, admin: null });
  descendants(main).find((node) => node.tagName === "BUTTON" && node.textContent === "Profile")!.fire("click");
  return descendants(main).find((node) => node.tagName === "SELECT")!;
}

describe("the Subtitles row", () => {
  test("is disabled and says so without a chosen profile", () => {
    const select = subtitleSelect(null);
    expect(select.disabled).toBe(true);
  });

  test("shows Off by default and persists a change once a profile is chosen", async () => {
    env.respondWith(async () => Response.json({}));
    await state.useProfile("viewer");
    const select = subtitleSelect({ name: "Viewer" });
    expect(select.disabled).toBe(false);
    expect(select.value).toBe("off");

    select.value = "de";
    select.fire("change");
    expect(state.preferenceOf("profile", "subtitle")).toBe("de");
  });

  test("reads back an already-stored preference", async () => {
    env.respondWith(async () => Response.json({}));
    await state.useProfile("viewer");
    state.setPreference("profile", "subtitle", "en");
    const select = subtitleSelect({ name: "Viewer" });
    expect(select.value).toBe("en");
  });
});
