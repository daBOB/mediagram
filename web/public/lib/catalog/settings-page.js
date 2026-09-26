/**
 * Settings, in the player's own language rather than an admin form: the
 * department-sized heading, then tabs. Appearance and Profile for everyone;
 * "Library & Telegram" — the admin-gated account, cache and sessions panel
 * (`lib/settings-view.js`) — only for a viewer on this household's own
 * network, the same rule the System page follows.
 *
 * Appearance is kept in this browser (a screen's property, not a profile's)
 * and applied by `lib/appearance-boot.js`, which this nudges on every change.
 */

import { el } from "../dom.js";
import { tabbed } from "./tabs.js";

const THEMES = [
  ["dark", "Dark", "Cinematic and focused"],
  ["light", "Light", "Clean and bright"],
  ["auto", "Auto", "Follows your device"],
];
const ACCENTS = [
  ["coral", "Coral"], ["blue", "Blue"], ["violet", "Violet"], ["teal", "Teal"],
  ["green", "Green"], ["amber", "Amber"], ["rose", "Rose"],
];
const BACKDROPS = [
  ["default", "Default", "Artwork fades into the page"],
  ["blurred", "Blurred", "Colour and light, softened"],
  ["artwork", "Artwork", "The picture behind the words"],
  ["solid", "Solid", "Plain pages, no artwork"],
];

function read(name, fallback) {
  try { return localStorage.getItem(`mediagram.${name}`) ?? fallback; } catch { return fallback; }
}
function write(name, value) {
  try { localStorage.setItem(`mediagram.${name}`, value); } catch { /* storage refused: nothing to keep it in */ }
  dispatchEvent(new Event("mediagram:appearance"));
}

/**
 * @param {HTMLElement} main
 * @param {{ profile: {name: string, kids?: boolean}|null,
 *   switchProfile: () => void, systemVisible: boolean,
 *   admin: ((container: HTMLElement) => () => void)|null }} on
 * @returns {() => void} stops the admin panel's work, if it was opened
 */
export function renderSettings(main, { profile, switchProfile, systemVisible, admin }) {
  let stopAdmin = null;
  const head = el("header", "dept-hero no-art settings-head");
  const copy = el("div", "dept-copy");
  copy.append(el("h1", "dept-title", "Settings"), el("p", "eyebrow", "Make it yours"));
  head.append(copy);
  main.append(head);
  main.append(tabbed([
    { label: "Appearance", build: appearance },
    { label: "Profile", build: () => profilePanel(profile, switchProfile, systemVisible) },
    ...(admin ? [{ label: "Library & Telegram", build: () => {
      const box = el("div", "settings-panel settings-admin");
      stopAdmin = admin(box);
      return box;
    } }] : []),
  ], "Settings"));
  return () => stopAdmin?.();
}

function appearance() {
  const box = el("div", "settings-panel");
  box.append(
    choice("Theme", "theme", read("theme", "auto"), THEMES, (value) => el("span", `swatch theme-${value}`)),
    choice("Accent colour", "accent", read("accent", "coral"), ACCENTS.map(([v, l]) => [v, l, null]),
      (value) => el("span", `dot accent-${value}`), "accents"),
    choice("Artwork", "backdrop", read("backdrop", "default"), BACKDROPS, (value) => el("span", `swatch backdrop-${value}`)),
  );
  return box;
}

/** A labelled group of native radio buttons, drawn as swatches. */
function choice(legend, name, current, options, preview, variant = "cards") {
  const set = el("fieldset", `setting ${variant}`);
  set.append(el("legend", null, legend));
  for (const [value, label, note] of options) {
    const option = el("label", "option");
    const input = el("input");
    input.type = "radio";
    input.name = name;
    input.value = value;
    input.checked = value === current;
    input.addEventListener("change", () => write(name, value));
    option.append(input, preview(value));
    const words = el("span", "option-words");
    words.append(el("span", "option-label", label));
    if (note) words.append(el("span", "option-note", note));
    option.append(words);
    set.append(option);
  }
  return set;
}

function profilePanel(profile, switchProfile, systemVisible) {
  const box = el("div", "settings-panel");
  const set = el("div", "setting");
  set.append(el("h2", "setting-title", "Who is watching"));
  set.append(el("p", "setting-value", profile ? `${profile.name}${profile.kids ? " · Kids" : ""}` : "Nobody chosen"));
  const change = el("button", "pill pill-line", "Switch profile");
  change.type = "button";
  change.addEventListener("click", switchProfile);
  set.append(change);
  box.append(set);
  if (systemVisible) {
    const system = el("a", "dept-all", "System and playback status →");
    system.href = "#/system";
    box.append(system);
  }
  return box;
}
