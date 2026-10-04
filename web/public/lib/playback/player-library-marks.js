/** My List, kids and collection controls for the title currently open. */
import * as state from "../watch-state.js";
import { ageLabel, kidsVerdict } from "../age-rating.js";

/**
 * The two limits a kid can have. A rating at or under the younger is for every
 * kid; one above it but at or under the older only for a kid at 12; above
 * that, for none — which is what the locked button says.
 */
const YOUNGEST_KIDS_LIMIT = 6;
const OLDEST_KIDS_LIMIT = 12;

export function mountPlayerLibraryMarks() {
  const watchlist = document.getElementById("watchlist");
  const kids = document.getElementById("kids");
  const kidsAge = document.getElementById("kids-age");
  const addTo = document.getElementById("add-to");
  let title = null;
  // Only ever shows what a rating decided; the choice itself is `kidsAge`.
  kids.disabled = true;
  kids.title = "Decided by the title's age rating, not by a mark";

  function refreshWatchlist() {
    const listed = title !== null && state.isWatchlisted(title.setId);
    watchlist.setAttribute("aria-pressed", String(listed));
    watchlist.textContent = listed ? "On My List" : "My List";
  }

  function refreshKids() {
    // A child does not approve titles for themselves; marking is for the
    // grown-ups' profiles. A rated title shows its verdict, locked; an
    // unrated one offers the age it is for kids from, or none.
    const child = state.profile()?.kids === true;
    const verdict = title === null ? "unrated" : kidsVerdict(title, OLDEST_KIDS_LIMIT);
    kids.hidden = child || verdict === "unrated";
    kidsAge.hidden = child || verdict !== "unrated";
    kidsAge.value = title === null ? "" : String(state.kidsAge(title.setId) ?? "");
    const rating = title === null ? null : ageLabel(title);
    kids.setAttribute("aria-pressed", String(verdict === "safe"));
    const everyKid = verdict === "safe" && kidsVerdict(title, YOUNGEST_KIDS_LIMIT) === "safe";
    kids.textContent = verdict !== "safe" ? `${rating} · not for kids`
      : everyKid ? `For kids · ${rating}` : `For kids from ${OLDEST_KIDS_LIMIT} · ${rating}`;
  }

  function open(set) {
    title = set;
    refreshWatchlist();
    refreshKids();
  }

  watchlist.addEventListener("click", () => {
    if (!title) return;
    state.setWatchlisted(title.setId, !state.isWatchlisted(title.setId));
    refreshWatchlist();
  });
  kidsAge.addEventListener("change", () => {
    if (!title || kidsAge.hidden) return;
    state.setKids(title.setId, kidsAge.value === "" ? null : Number(kidsAge.value));
  });
  addTo.addEventListener("click", () => {
    if (!title) return;
    const lists = state.collections();
    if (lists.length === 0) {
      window.alert("No lists yet. Make one on the Collections shelf.");
      return;
    }
    const names = lists.map((list, index) => `${index + 1}. ${list.name}`).join("\n");
    const answer = window.prompt(`Add to which list?\n\n${names}\n\nNumber:`);
    if (answer === null) return;
    const chosen = lists[Number(answer) - 1];
    if (chosen) state.setInCollection(chosen.id, title.setId, true);
  });

  return { open, clear: () => open(null) };
}
