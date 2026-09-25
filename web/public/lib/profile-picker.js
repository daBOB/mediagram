/**
 * Who is watching.
 *
 * The player has no authentication and is not pretending to have any: this
 * asks who you are, it does not check. What it buys is that two people
 * sharing a library do not share a half-watched film or each other's lists —
 * which is a convenience, and is worth being clear is only a convenience.
 *
 * The answer is kept on the device rather than on the server, so a television
 * stays on the television's profile and a shared laptop asks again.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";

/** The letter on a profile's tile. */
const initialOf = (name) => (name ?? "?").trim().charAt(0).toUpperCase() || "?";

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
    let naming = false;
    let createFailed = false;
    const selection = new AbortController();
    const draw = () => {
      choices.textContent = "";
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
      const tiles = el("div", "who-tiles");
      for (const entry of state.profiles()) {
        const tile = el("button", "who-tile");
        tile.append(el("span", "who-initial", initialOf(entry.name)));
        tile.append(el("span", "who-name", entry.name));
        if (entry.kids) tile.append(el("span", "who-kids", "Kids"));
        tile.disabled = selecting;
        tile.addEventListener("click", async () => {
          if (closed || selecting) return;
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
          closed = true;
          screen.remove();
          resolve(entry.id);
        });
        tiles.append(tile);
      }

      const add = el("button", "who-tile who-add");
      add.disabled = selecting;
      add.append(el("span", "who-initial", "＋"));
      add.append(el("span", "who-name", "New profile"));
      add.addEventListener("click", () => {
        if (closed || selecting) return;
        naming = true;
        createFailed = false;
        draw();
      });
      tiles.append(add);
      choices.append(tiles);

      if (naming) {
        const form = el("form", "who-new");
        const name = el("input");
        name.required = true;
        name.maxLength = 120;
        name.placeholder = "Name";
        name.setAttribute("aria-label", "Name for this profile");
        const kidsChoice = el("label", "who-kids-choice");
        const kids = el("input");
        kids.type = "checkbox";
        kidsChoice.append(kids, " Kids profile — only FSK 12 and under");
        const create = el("button", "who-create", "Create");
        create.type = "submit";
        const cancel = el("button", "quiet", "Cancel");
        cancel.type = "button";
        cancel.addEventListener("click", () => {
          naming = false;
          draw();
        });
        form.addEventListener("submit", (event) => {
          event.preventDefault();
          void state.createProfile(name.value, kids.checked).then((made) => {
            if (closed) return;
            naming = made === null;
            createFailed = made === null;
            draw();
          });
        });
        form.append(name, kidsChoice, create, cancel);
        if (createFailed) form.append(el("p", "error", "Could not create the profile. Please try again."));
        choices.append(form);
        name.focus();
      }

      if (state.profiles().length > 0) {
        const manage = el("button", "quiet", "Rename or remove…");
        manage.disabled = selecting;
        manage.addEventListener("click", () => {
          if (closed || selecting) return;
          const name = window.prompt(
            "Name of the profile to remove, exactly. Everything of theirs goes with it.",
          );
          if (name === null) return;
          const found = state.profiles().find((entry) => entry.name === name);
          if (!found) {
            window.alert("No profile by that name.");
            return;
          }
          if (!window.confirm(`Remove "${found.name}" and everything they have watched?`)) return;
          void state.deleteProfile(found.id).then((ok) => {
            if (ok) draw();
            else window.alert("Could not remove the profile. Please try again.");
          });
        });
        choices.append(manage);
      }

      // Said plainly rather than implied: somebody will otherwise assume a
      // profile is a login, and it is not one.
      choices.append(
        el(
          "p",
          "who-note",
          state.remembers()
            ? "Profiles keep your places and lists apart. They are not a login — anyone who can reach this player can pick any of them."
            : "This player cannot save anything, so nothing here will be kept.",
        ),
      );
    };
    draw();

    if (canCancel) {
      const back = el("button", "quiet", "Stay as I am");
      back.addEventListener("click", () => {
        closed = true;
        selection.abort();
        screen.remove();
        resolve(state.profileId());
      });
      card.append(back);
    }

    screen.append(card);
    root.append(screen);
  });
}
