/**
 * Who is watching.
 *
 * Profiles keep a household's places and lists apart. A kid's tile opens at
 * once; a grown-up's asks their PIN — or, for one from before PINs, has them
 * set one first — so a child cannot tap into a parent's profile. That is all
 * a PIN does here. It is not a login: the kids filter runs in this browser and
 * the state API has no sessions, so developer tools or `curl` get past it.
 * What the server does check is every change to who there is, made through
 * Manage profiles (`profile-manage.js`).
 *
 * The answer is kept on the device rather than on the server, so a television
 * stays on the television's profile and a shared laptop asks again; a
 * profile remembered at start-up is not asked for its PIN.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import * as api from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { askGrownUp, askPin, refusalText } from "./pin-prompt.js";
import { addForm, openManage } from "./profile-manage.js";
import { waitingForHousehold } from "./household-waiting.js";

/** Said plainly, so nobody takes a PIN for a login. */
const NOTE = "Profiles keep your places and lists apart. A grown-up’s PIN keeps children out of it; it is not a login, and someone who knows their way around a browser can get past it.";

/** The letter on a profile's tile. */
export const initialOf = (name) => (name ?? "?").trim().charAt(0).toUpperCase() || "?";

/**
 * Shows the chooser and resolves once somebody has been chosen.
 *
 * Resolves rather than calling back, because everything downstream — the
 * catalog render, the shelves, the player — is waiting on the answer and
 * reads better as one await than as a continuation.
 */
export function chooseProfile(root, { canCancel = false, discoveryFailed = false, stateFailed = false } = {}) {
  return new Promise((resolve) => {
    const screen = el("div", "who");
    const card = el("div", "who-card");
    card.append(el("h1", null, "Who's watching?"));

    const choices = el("div");
    card.append(choices);
    let closed = false;
    let selecting = false;
    /** A refusal to say above the tiles, until the viewer tries something else. */
    let notice = null;
    const selection = new AbortController();
    const back = canCancel ? el("button", "quiet", "Stay as I am") : null;

    function close(id) {
      closed = true;
      document.removeEventListener("keydown", onKey);
      screen.remove();
      resolve(id);
    }

    const stay = () => { selection.abort(); close(state.profileId()); };

    // Escape on a reopened picker is "Stay as I am", as Back is on Android —
    // unless a PIN prompt or Manage profiles over it (a second child of the
    // screen) takes that Escape; and never with nobody to stay as.
    function onKey(event) {
      if (event.key === "Escape" && back && !back.hidden && !closed && screen.childElementCount === 1) stay();
    }

    /** Who is here, read again — after a refusal, which is usually news from another player. */
    function reread() {
      void state.loadProfiles().then((read) => { if (read && !closed && !selecting) draw(); });
    }

    /** A new PIN refused ends its dialog; what went wrong is said here, over who is here now. */
    const tell = (outcome) => { notice = refusalText(outcome); reread(); };

    /** A grown-up proves who they are first; a kid goes straight in. */
    async function enter(entry) {
      if (closed || selecting) return;
      notice = null;
      if (!entry.kids && (await askGrownUp(screen, entry, (pin) => api.prove(entry, pin), tell)) === null) return;
      if (closed) return;
      selecting = true;
      stateFailed = false;
      draw();
      const applied = await state.useProfile(entry.id, selection.signal);
      if (closed) return;
      selecting = false;
      if (!applied) {
        stateFailed = true;
        draw();
        return;
      }
      close(entry.id);
    }

    /**
     * No grown-up here yet — a new player, or one that only knows kids: the
     * first grown-up made runs the household. Whether it was made or refused
     * (one arrived from another player meanwhile), draw who is here now.
     */
    function firstProfile() {
      const box = el("div", "who-ask");
      box.append(el("h2", null, "Create the first profile — it runs this household"),
        addForm("Create", [], async (name) => {
          notice = null;
          const made = await askPin(screen, { title: `A PIN for ${name}`, confirm: true, refused: tell,
            send: (pin) => api.createFirst(name, pin) });
          if (made !== null && !closed) draw();
        }));
      return box;
    }

    /** Asked while grown-ups exist but nobody runs this household. Only a grown-up can; one with no PIN sets it here. */
    function householdQuestion(grownUps) {
      const ask = el("div", "who-ask");
      ask.append(el("h2", null, "Who runs this household?"));
      for (const entry of grownUps) {
        const pick = el("button", "pill pill-line", entry.name);
        pick.type = "button";
        pick.disabled = selecting;
        pick.addEventListener("click", async () => {
          if (closed || selecting) return;
          notice = null;
          const pin = await askGrownUp(screen, entry, (given) => api.claimAdmin(entry.id, given), tell);
          if (pin !== null && !closed) draw();
        });
        ask.append(pick);
      }
      return ask;
    }

    function tiles() {
      const row = el("div", "who-tiles");
      for (const entry of state.profiles()) {
        const tile = el("button", "who-tile");
        tile.append(el("span", "who-initial", initialOf(entry.name)));
        tile.append(el("span", "who-name", entry.name));
        if (entry.kids) tile.append(el("span", "who-kids", `Kids · FSK ${kidsLimitOf(entry)}`));
        tile.disabled = selecting;
        tile.addEventListener("click", () => void enter(entry));
        row.append(tile);
      }
      return row;
    }

    const draw = () => {
      choices.textContent = "";
      // Nothing to stay as once this device's own profile is gone.
      if (back) back.hidden = state.profile() === null;
      if (discoveryFailed) {
        choices.append(el("p", "error", "Could not load profiles. Please try again."));
        const retry = el("button", "quiet", "Retry profiles");
        retry.addEventListener("click", async () => {
          if (retry.disabled) return;
          retry.disabled = true;
          discoveryFailed = !(await state.loadProfiles());
          if (!closed) draw();
        });
        choices.append(retry);
        return;
      }
      if (stateFailed) choices.append(el("p", "error", "Could not load this profile. Choose it again to retry."));
      if (notice) choices.append(el("p", "error", notice));
      // The three ways a household can stand: nobody grown-up yet, grown-ups
      // with nobody running things, or an admin — and only once there is a
      // grown-up is there anyone who could manage.
      const grownUps = state.profiles().filter((entry) => !entry.kids);
      if (grownUps.length === 0) choices.append(state.heard() ? firstProfile() : waitingForHousehold(reread));
      else if (!grownUps.some((entry) => entry.admin)) choices.append(householdQuestion(grownUps));
      choices.append(tiles());
      if (grownUps.length > 0) {
        const manage = el("button", "quiet", "Manage profiles");
        manage.disabled = selecting;
        manage.addEventListener("click", () => {
          if (!closed && !selecting) openManage(screen, () => { if (!closed) draw(); });
        });
        choices.append(manage);
      }
      choices.append(el("p", "who-note", state.remembers() ? NOTE : "This player cannot save anything, so nothing here will be kept."));
    };
    draw();
    // Opened on a page that has been up a while: another player may have added
    // a kid, set a PIN or named the admin since, and the tiles should say so.
    if (canCancel && !discoveryFailed) reread();

    if (back) {
      back.addEventListener("click", stay);
      card.append(back);
      document.addEventListener("keydown", onKey);
    }

    screen.append(card);
    root.append(screen);
  });
}
