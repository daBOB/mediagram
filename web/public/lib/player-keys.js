/**
 * The keyboard the native controls took with them.
 *
 * `<video controls>` came with one — space, arrows, `f`, `m` — and it is easy
 * to forget it was ever there, because nothing on screen said so. Removing
 * the bar removes that too, and a player that can only be driven by pointer
 * is a worse player than the one this replaced.
 *
 * A key map is a table, so this is a table and nothing else: no element, no
 * playback, no `preventDefault`. The caller decides what to do about the
 * answer, and the table can be proved.
 */

/** Ten, the same ten the buttons skip and the same ten the phone skips. */
const SKIP = 10;

/** A tenth of the way up or down, which is about as fine as a key should be. */
const VOLUME_STEP = 0.1;

/**
 * What this keystroke asks the player to do, or `null` for "not ours".
 *
 * `inControl` is a field that wants its own keys — a text box, a menu, the
 * scrub bar. Everything is left alone there: an arrow pressed on the scrub
 * bar is the scrub bar's arrow, and typing a `c` into a search field must not
 * turn the subtitles on.
 *
 * `onButton` is narrower. A focused button already answers space and Enter by
 * pressing itself, and handling those here as well would pause the film *and*
 * press whatever the viewer had tabbed to. Every other key still works there,
 * because a viewer who has tabbed to Watchlist has not given up the arrows.
 *
 * `Escape` is deliberately absent. The dialog closes on it by itself, and a
 * player that intercepted it would have to remember to do that.
 *
 * @param {{key: string, ctrlKey?: boolean, altKey?: boolean, metaKey?: boolean,
 *          inControl?: boolean, onButton?: boolean}} press
 */
export function keyAction(press = {}) {
  const { key } = press;
  if (typeof key !== "string" || key === "") return null;
  // A modified key belongs to the browser or the window manager: ctrl+F is
  // find, meta+← is back, and neither is a request to skip ten seconds.
  if (press.ctrlKey === true || press.altKey === true || press.metaKey === true) return null;
  if (press.inControl === true) return null;

  const pressing = key === " " || key === "Spacebar" || key === "Enter";
  if (press.onButton === true && pressing) return null;

  switch (key) {
    case " ":
    case "Spacebar":
    case "k":
      return { do: "playPause" };
    case "ArrowLeft":
      return { do: "skip", by: -SKIP };
    case "ArrowRight":
      return { do: "skip", by: SKIP };
    case "ArrowUp":
      return { do: "volume", by: VOLUME_STEP };
    case "ArrowDown":
      return { do: "volume", by: -VOLUME_STEP };
    case "m":
      return { do: "mute" };
    case "f":
      return { do: "fullscreen" };
    case "c":
      return { do: "subtitles" };
    default:
      break;
  }

  // Nought through nine: a tenth of the film each, the way every player that
  // has ever had a keyboard does it. `0` is the start, not a tenth of nothing.
  if (key.length === 1 && key >= "0" && key <= "9") {
    return { do: "seekFraction", by: Number(key) / 10 };
  }
  return null;
}

/**
 * Whether this element wants the keystroke for itself.
 *
 * Kept beside the table because it is the other half of the same decision,
 * and separated from it because this one needs a DOM node and the table does
 * not.
 */
export function wantsKeys(element) {
  if (!element || typeof element.tagName !== "string") return false;
  if (element.isContentEditable === true) return true;
  return ["INPUT", "SELECT", "TEXTAREA", "OPTION"].includes(element.tagName);
}
