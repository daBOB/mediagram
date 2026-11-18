/**
 * A show's seasons as a row of cards over its episode list, the television's
 * season row (`TvSeasonPicker.kt`) in place of the select that stood here:
 * every season in sight, each its own cover where the library holds one,
 * else the show's poster with the season's number set over it so the cards
 * still tell apart. The watched share is the plate's rule; under it the
 * season, how many episodes it holds and how many of them are watched (a
 * plate's name over its meta, where the television sets them on one line);
 * and the season showing is marked as a showing tab is.
 *
 * A card chooses through the URL (`openSeason`), as the select did, so a
 * season can be linked and the back button steps out of it; the page is
 * drawn again, and the card just pressed takes focus back.
 */

import { el, focusWhenAttached } from "../dom.js";
import { countOf } from "../format.js";
import { firstItemOf } from "../library.js";
import { isWatched } from "../watch-state.js";
import { plate } from "./plate.js";

/** The show whose season row was just used: its rebuilt row gives focus back. */
let pickedOn = null;

/** Every title under a division, its nested folders' too. */
const setsOf = (division) => [...division.items, ...division.children.flatMap(setsOf)];

/**
 * @param {import("../library.js").Collection} collection
 * @param {import("../library.js").Division} shown the season in view
 * @param {(title: string) => void} openSeason
 */
export function seasonCards(collection, shown, openSeason) {
  const row = el("div", "grid plates strip season-cards");
  row.setAttribute("role", "group");
  row.setAttribute("aria-label", "Seasons");
  const showPoster = firstItemOf(collection.divisions)?.poster ?? null;
  for (const division of collection.divisions) {
    const sets = setsOf(division);
    const watched = sets.filter((set) => isWatched(set.setId)).length;
    const own = sets.find((set) => set.seasonPoster)?.seasonPoster ?? null;
    const number = String(division.season ?? division.title);
    const thumb = plate({
      poster: own ?? showPoster,
      name: division.title,
      // With no art at all the initials stand in, and they are the number.
      initials: number,
      progress: watched > 0 && watched < sets.length ? watched / sets.length : null,
      watched: sets.length > 0 && watched === sets.length,
    });
    // The show's poster standing in for every season: the number over it tells them apart.
    if (!own && showPoster) thumb.append(el("span", "season-number", number));

    const card = el("button", division === shown ? "card on" : "card");
    card.type = "button";
    if (division === shown) card.setAttribute("aria-current", "true");
    const body = el("div", "body");
    body.append(el("div", "name", division.title), el("div", "meta", countOf(sets.length, "episode")));
    if (watched > 0) body.append(el("div", "meta", `${watched} of ${sets.length} watched`));
    card.append(thumb, body);
    card.addEventListener("click", () => {
      pickedOn = collection.name;
      openSeason(division.title);
    });
    if (division === shown && pickedOn === collection.name) {
      pickedOn = null;
      focusWhenAttached(card);
    }
    row.append(card);
  }
  return row;
}
