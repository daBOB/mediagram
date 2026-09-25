/**
 * How loud this room is, remembered between titles.
 *
 * Deliberately not in watch state, which lives on the server and is shared by
 * every device pointed at it. A position belongs to the title — start an
 * episode on the laptop and the phone should know where you got to. A volume
 * belongs to the room the speaker is in, and carrying it from a desk to a
 * handset would be carrying the wrong thing.
 *
 * Every access is wrapped. `localStorage` is not merely empty in a private
 * window or with site data blocked: reading it *throws*, and an uncaught
 * throw here would take the player's controls down with it.
 */

const KEY = "mediagram.volume";

/** Full, and unmuted, when nothing has been remembered or the store refuses. */
const DEFAULT = { volume: 1, muted: false };

export function readVolume() {
  try {
    const saved = window.localStorage.getItem(KEY);
    if (saved === null) return { ...DEFAULT };
    const held = JSON.parse(saved);
    // `?? NaN` before the coercion: `Number(null)` is 0, and 0 is silence.
    // Without it a store holding no volume at all would open the next title
    // muted, and the viewer would have nothing to blame for it.
    const volume = Number(held?.volume ?? Number.NaN);
    return {
      // A remembered volume outside nought-to-one is a store someone has been
      // editing, not an instruction.
      volume: Number.isFinite(volume) && volume >= 0 && volume <= 1 ? volume : DEFAULT.volume,
      muted: held?.muted === true,
    };
  } catch {
    return { ...DEFAULT };
  }
}

export function writeVolume({ volume, muted }) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify({ volume, muted: muted === true }));
  } catch {
    /* A volume that cannot be remembered is still a volume that works now. */
  }
}
