/**
 * Where a kid's daily limit meets the page: whether a title may open, and
 * the one warning when little is left.
 *
 * The server is the counter. This asks it (`GET /api/profiles/{id}/today`)
 * before a title opens and hears from it in the answer to every position
 * write while one plays; it never adds up time itself. A household rule, not
 * a lock: a server that cannot be asked lets the title open.
 *
 * A title already playing is never stopped. What is refused is opening — a
 * shelf, a resume, Next up, a joined session and the next episode alike,
 * because they all open through `app.js`'s one `openTitle`.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import { DAILY_LIMIT_WORDS, dailyLimitState } from "./daily-limit.js";

/** The latest `{ limitMinutes, watchedSeconds }` the server told, for the profile in use. */
let latest = null;
/** Whether this open title has had its warning. */
let warned = false;
let pageNotice = null;

/** How long the player's status line holds the words: longer than watch together's two seconds, for a child to read. */
const HOLD_MS = 8000;
/** As long as `refreshState` waits: an open never hangs on a slow server, which lets the title open. */
const TODAY_TIMEOUT_MS = 1500;

/**
 * Tells the viewer where they are: over the picture while the player is open
 * — the status line watch together's notices use, not the control card's
 * note, which hides with the controls and would swallow a warning that
 * arrives mid-title — else a line on the page.
 */
function tell(words) {
  const status = document.getElementById("together-note");
  if (document.getElementById("player")?.open && status) {
    status.textContent = words;
    setTimeout(() => { if (status.textContent === words) status.textContent = ""; }, HOLD_MS);
    return;
  }
  pageNotice?.remove();
  pageNotice = el("p", "error daily-limit-notice", words);
  pageNotice.setAttribute("role", "status");
  document.getElementById("main")?.append(pageNotice);
  setTimeout(() => pageNotice?.remove(), 10_000);
}

const standing = () => (latest === null ? { state: "free" } : dailyLimitState(latest.limitMinutes, latest.watchedSeconds));

function warnOnce() {
  if (warned || standing().state !== "warn") return;
  warned = true;
  tell(DAILY_LIMIT_WORDS.warning);
}

/** Whether a title may open now. Says why, where the viewer is, when it may not. */
export async function mayOpen() {
  const id = state.profileId();
  latest = null;
  if (id === null || state.profile()?.kids !== true) return true;
  try {
    const response = await fetch(`/api/profiles/${encodeURIComponent(id)}/today`, { signal: AbortSignal.timeout(TODAY_TIMEOUT_MS) });
    if (response.ok) latest = await response.json();
  } catch {
    // Unreachable: the rule is a household's, not a lock.
  }
  if (standing().state !== "out") return true;
  tell(DAILY_LIMIT_WORDS.out);
  return false;
}

/** A title has opened: its warning is due again. `settled` is when its own first notes are done (`freshResume`). */
export function opened(settled) {
  warned = false;
  void Promise.resolve(settled).catch(() => null).then(warnOnce);
}

state.onToday((today) => {
  latest = today;
  warnOnce();
});
