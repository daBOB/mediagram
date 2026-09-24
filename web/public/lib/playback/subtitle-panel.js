/**
 * The three controls that make a subtitle usable.
 *
 * A panel rather than four more things on the bar: these are set once for a
 * series and then never touched again, and a control that is adjusted once a
 * month does not earn permanent space beside play and pause.
 *
 * Everything here is a node or an event. The decisions — what a size means,
 * where an offset puts a cue — are in `subtitle-style.js`, which can be proved
 * without a browser, and which records why there is no position control.
 */

import { el } from "../dom.js";
import { cueStyle } from "./subtitle-style.js";

/** How far a nudge moves the text along the clock. */
const NUDGE_SECONDS = 0.1;

/** As far as a subtitle file is ever wrong by. Beyond this it is the wrong file. */
const FURTHEST = 30;

/**
 * Four steps that are all usable.
 *
 * The browser's own size is already generous, so these climb gently: 140%
 * put a long German line on four rows and buried the transport bar under it.
 */
const SIZES = [
  { value: "80", label: "Small" },
  { value: "100", label: "Normal" },
  { value: "115", label: "Large" },
  { value: "135", label: "Larger" },
];

const BACKINGS = [
  { value: "shadow", label: "Shadow" },
  { value: "box", label: "Box" },
  { value: "none", label: "None" },
];

/** The one stylesheet `::cue` rules are written to, made when first needed. */
let sheet = null;
function writeStyle(rule) {
  if (sheet === null) {
    sheet = document.createElement("style");
    document.head.append(sheet);
  }
  sheet.textContent = rule;
}

function chooser(id, label, options, onPick) {
  const wrap = el("label", "cue-row");
  wrap.append(el("span", null, label));
  const select = el("select");
  select.id = id;
  for (const { value, label: name } of options) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = name;
    select.append(option);
  }
  select.addEventListener("change", () => onPick(select.value));
  wrap.append(select);
  return { wrap, select };
}

/**
 * Builds the panel and returns the handle the player drives it by.
 *
 * `recall`/`remember` are the show's memory, passed in for the same reason the
 * transport takes them: what "this show" means is the player's question.
 *
 * `onPlacement` is called whenever the offset or the position changes, because
 * those are not CSS — they are written onto each cue, and only the player
 * knows which tracks are attached right now.
 */
export function subtitlePanel({ recall, remember, onPlacement }) {
  const panel = el("div", "cue-panel");
  panel.hidden = true;

  const trigger = el("button", "tsp", "");
  trigger.id = "cue-settings";
  trigger.setAttribute("aria-label", "Subtitle appearance");
  trigger.setAttribute("aria-expanded", "false");
  trigger.textContent = "Aa";

  let held = { size: "100", backing: "shadow", offset: 0 };

  const size = chooser("cue-size", "Size", SIZES, (value) => set({ size: value }));
  const backing = chooser("cue-backing", "Backing", BACKINGS, (value) => set({ backing: value }));

  // The one that earns the panel. A file half a second out is unwatchable and,
  // until now, unfixable.
  const syncRow = el("div", "cue-row cue-sync");
  syncRow.append(el("span", null, "Sync"));
  const earlier = el("button", "quiet", "−");
  earlier.setAttribute("aria-label", "Subtitles earlier");
  const at = el("span", "cue-at", "0.0s");
  const later = el("button", "quiet", "+");
  later.setAttribute("aria-label", "Subtitles later");
  const reset = el("button", "quiet", "Reset");
  earlier.addEventListener("click", () => nudge(-NUDGE_SECONDS));
  later.addEventListener("click", () => nudge(NUDGE_SECONDS));
  reset.addEventListener("click", () => set({ offset: 0 }));
  syncRow.append(earlier, at, later, reset);

  panel.append(size.wrap, backing.wrap, syncRow);

  trigger.addEventListener("click", () => show(panel.hidden));

  function show(open) {
    panel.hidden = !open;
    trigger.setAttribute("aria-expanded", String(open));
  }

  function nudge(by) {
    // Rounded, or floating point turns four taps of 0.1 into 0.30000000000004
    // and the readout says so.
    set({ offset: Math.round(clamp(held.offset + by, FURTHEST) * 10) / 10 });
  }

  /** Applies a change, shows it, and remembers it. */
  function set(change) {
    held = { ...held, ...change };
    draw();
    for (const [name, value] of Object.entries(change)) {
      remember?.(`cue-${name}`, String(value));
    }
    if ("offset" in change) onPlacement?.(placement());
  }

  /** What the panel currently reads, in the shape `placeCues` wants. */
  function placement() {
    return { offset: held.offset };
  }

  function draw() {
    writeStyle(cueStyle({ size: Number(held.size), backing: held.backing }));
    size.select.value = held.size;
    backing.select.value = held.backing;
    // Signed on purpose: "−0.4s" and "0.4s" are different instructions, and a
    // viewer nudging past nought needs to see it happen.
    at.textContent = `${held.offset > 0 ? "+" : ""}${held.offset.toFixed(1)}s`;
  }

  /**
   * Loads this show's choices. Called as a title opens, before its cues are.
   *
   * Every value is checked against what this panel offers rather than trusted:
   * the row is text somebody else's code wrote, and a `size` of `"enormous"`
   * should be ignored, not turned into a stylesheet.
   */
  function recallFor() {
    held = {
      size: oneOf(SIZES, recall?.("cue-size"), "100"),
      backing: oneOf(BACKINGS, recall?.("cue-backing"), "shadow"),
      offset: clamp(Number(recall?.("cue-offset") ?? 0), FURTHEST) || 0,
    };
    show(false);
    draw();
    return placement();
  }

  return { trigger, panel, recallFor, placement };
}

/** The remembered value, if it is one of the ones on offer. */
function oneOf(options, value, fallback) {
  return options.some((option) => option.value === value) ? value : fallback;
}

function clamp(value, furthest) {
  if (!Number.isFinite(value)) return 0;
  return Math.min(furthest, Math.max(-furthest, value));
}
