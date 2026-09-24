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
export function chooseProfile(root, { canCancel = false } = {}) {
  return new Promise((resolve) => {
    const screen = el("div", "who");
    const card = el("div", "who-card");
    card.append(el("h1", null, "Who's watching?"));

    const tiles = el("div", "who-tiles");
    const draw = () => {
      tiles.textContent = "";
      for (const entry of state.profiles()) {
        const tile = el("button", "who-tile");
        tile.append(el("span", "who-initial", initialOf(entry.name)));
        tile.append(el("span", "who-name", entry.name));
        tile.addEventListener("click", () => {
          screen.remove();
          void state.useProfile(entry.id).then(() => resolve(entry.id));
        });
        tiles.append(tile);
      }

      const add = el("button", "who-tile who-add");
      add.append(el("span", "who-initial", "＋"));
      add.append(el("span", "who-name", "New profile"));
      add.addEventListener("click", () => {
        const name = window.prompt("Name for this profile");
        if (name === null) return;
        void state.createProfile(name).then((made) => {
          if (made) draw();
          else window.alert("Could not create the profile. Please try again.");
        });
      });
      tiles.append(add);
    };

    draw();
    card.append(tiles);

    if (state.profiles().length > 0) {
      const manage = el("button", "quiet", "Rename or remove…");
      manage.addEventListener("click", () => {
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
      card.append(manage);
    }

    if (canCancel) {
      const back = el("button", "quiet", "Stay as I am");
      back.addEventListener("click", () => {
        screen.remove();
        resolve(state.profileId());
      });
      card.append(back);
    }

    // Said plainly rather than implied: somebody will otherwise assume a
    // profile is a login, and it is not one.
    card.append(
      el(
        "p",
        "who-note",
        state.remembers()
          ? "Profiles keep your places and lists apart. They are not a login — anyone who can reach this player can pick any of them."
          : "This player cannot save anything, so nothing here will be kept.",
      ),
    );

    screen.append(card);
    root.append(screen);
  });
}
