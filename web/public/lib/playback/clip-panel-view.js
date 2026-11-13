/** The clip panel's nodes, kept apart from what it decides. */

import { el } from "../dom.js";
import * as state from "../watch-state.js";

const fileUrl = (clip) => `/api/clips/${encodeURIComponent(clip.id)}/file?profile=${encodeURIComponent(state.profileId())}`;

/** One of the two ends: its name, its time and the three ways to move it. */
function endRow(name, nudge) {
  const row = el("div", "cue-row clip-row");
  const time = el("span", "cue-at");
  const buttons = el("span", "clip-nudges");
  const make = (label, by, spoken) => {
    const button = el("button", "quiet", label);
    button.type = "button";
    // Both ends have the same three buttons; the name says which end this one moves.
    button.setAttribute("aria-label", `${name}: ${spoken}`);
    button.addEventListener("click", () => nudge(by));
    return button;
  };
  const nudges = [make("−1 s", -1, "back 1 second"), make("+1 s", 1, "forward 1 second")];
  // `null` is "Set to now".
  buttons.append(make("Set to now", null, "set to now"), ...nudges);
  row.append(el("span", null, name), time, buttons);
  return { row, time, nudges };
}

/**
 * The panel in the card's dock, shut. `move(which, by)` hears every nudge;
 * `pickAgain` drops both ends so the viewer can seek anywhere and set them anew.
 */
export function buildClipPanel({ move, pickAgain }) {
  const panel = el("div", "cue-panel clip-panel");
  panel.hidden = true;
  panel.tabIndex = -1;
  panel.setAttribute("role", "group");
  panel.setAttribute("aria-label", "Clip");
  const start = endRow("Start", (by) => move("from", by));
  const end = endRow("End", (by) => move("to", by));
  const length = el("p", "clip-length");
  const pick = el("button", "quiet clip-pick", "Pick again");
  pick.type = "button";
  pick.addEventListener("click", pickAgain);
  const lengthRow = el("div", "cue-row clip-row");
  lengthRow.append(length, pick);
  const exportButton = el("button", "quiet clip-export", "Export MP4");
  exportButton.type = "button";
  // Announces what changed; the percentage beside it is not live, or it would be read out every second.
  const said = el("p", "clip-said");
  said.setAttribute("role", "status");
  const progress = el("p", "clip-progress");
  const ready = el("div", "clip-ready");
  panel.append(el("span", "clip-head", "Clip"), start.row, end.row, lengthRow, exportButton, said, progress, ready);
  document.getElementById("card-dock").append(panel);
  return { panel, start, end, length, exportButton, said, progress, ready, card: document.getElementById("control-card") };
}

/** A finished clip as a link, for a viewer whose browser did not save it. */
export function clipLink(clip, onClick) {
  const link = el("a", "clip-link", `Download ${clip.name}`);
  link.href = fileUrl(clip);
  link.download = clip.name;
  link.addEventListener("click", onClick);
  return link;
}

/** The file arrives by navigating a hidden link, which every browser treats as a download. */
export function downloadClip(clip) {
  const link = el("a");
  link.href = fileUrl(clip);
  link.download = clip.name;
  link.hidden = true;
  document.body.append(link);
  link.click();
  link.remove();
}
