/**
 * The new-achievement dot on the rail's Stats link.
 *
 * Which achievements this browser has shown is kept here, per profile, in
 * `localStorage` — never on the server, never synced: having seen a badge is
 * a fact about a screen, not about the viewer, and one earned on the TV is
 * still news on the laptop. Opening the Stats page marks everything it shows
 * as seen ({@link markSeen}); anything earned since lights the dot. Never a
 * pop-up.
 */

const PREFIX = "mediagram.stats-seen.";
/** The dot inside the rail's Stats link (`index.html`). */
const DOT = "stats-dot";

/**
 * How long the page waits after a change before reading the stats again —
 * longer than the player's ten-second save tick, so a title playing does not
 * re-read them on every position it saves. The rail is behind the player
 * then anyway. A profile switch reads at once.
 */
export const SETTLE_MS = 15_000;

/** The ids this browser has shown `profileId`; none for a missing or unreadable entry. */
function seenIds(profileId) {
  try {
    const stored = JSON.parse(window.localStorage.getItem(PREFIX + profileId) ?? "[]");
    return new Set(Array.isArray(stored) ? stored.filter((id) => typeof id === "string") : []);
  } catch {
    return new Set();
  }
}

/** Whether anything in `earned` is missing from what this browser has shown `profileId`. */
function hasUnseen(profileId, earned) {
  const seen = seenIds(profileId);
  return earned.some((entry) => !seen.has(entry.id));
}

function showDot(on) {
  const dot = document.getElementById(DOT);
  if (dot) dot.hidden = !on;
}

/** What the Stats page is showing becomes what this browser has shown; the dot goes out. */
export function markSeen(profileId, earned) {
  try {
    window.localStorage.setItem(PREFIX + profileId, JSON.stringify(earned.map((entry) => entry.id)));
  } catch {
    // Storage refused: the dot comes back on the next read, nothing worse.
  }
  showDot(false);
}

/**
 * Keeps the dot in step with `state` — the page's own `watch-state.js` — for
 * as long as the page lives: a shelf-affecting change (a write here, another
 * device's rows pulled in, a profile switch) re-reads this profile's
 * achievements once things settle; a different profile reads at once, its
 * predecessor's dot gone before the answer arrives.
 *
 * @param {{ profileId: () => string | null, subscribeChanges: (listener: () => void) => unknown }} state
 */
export function watchStatsDot(state) {
  let readFor = null;
  let timer = null;
  const read = async () => {
    const asked = state.profileId();
    readFor = asked;
    if (asked === null) return showDot(false);
    try {
      const response = await fetch(`/api/profiles/${encodeURIComponent(asked)}/stats`);
      if (!response.ok) return;
      const { achievements } = await response.json();
      // Another profile chosen while this was in flight: its dot is not this.
      if (state.profileId() === asked) showDot(hasUnseen(asked, achievements?.earned ?? []));
    } catch {
      // Unreachable for now: the dot waits for the next change.
    }
  };
  state.subscribeChanges(() => {
    clearTimeout(timer);
    if (state.profileId() === readFor) {
      timer = setTimeout(read, SETTLE_MS);
      return;
    }
    showDot(false);
    void read();
  });
  void read();
}
