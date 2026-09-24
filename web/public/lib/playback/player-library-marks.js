/** Watchlist, kids and collection controls for the title currently open. */
import * as state from "../watch-state.js";
import { ageLabel, kidsVerdict } from "../age-rating.js";

export function mountPlayerLibraryMarks() {
  const watchlist = document.getElementById("watchlist");
  const kids = document.getElementById("kids");
  const addTo = document.getElementById("add-to");
  let title = null;

  function refreshWatchlist() {
    const listed = title !== null && state.isWatchlisted(title.setId);
    watchlist.setAttribute("aria-pressed", String(listed));
    watchlist.textContent = listed ? "On the list" : "Watchlist";
  }

  function refreshKids() {
    // A child does not approve titles for themselves; marking is for the
    // grown-ups' profiles.
    kids.hidden = state.profile()?.kids === true;
    const verdict = title === null ? "unrated" : kidsVerdict(title);
    const rating = title === null ? null : ageLabel(title);
    const forKids =
      verdict === "safe" || (verdict === "unrated" && title !== null && state.isKids(title.setId));
    kids.setAttribute("aria-pressed", String(forKids));
    kids.disabled = verdict !== "unrated";
    kids.textContent =
      verdict === "safe"
        ? `For kids · ${rating}`
        : verdict === "unsafe"
          ? `${rating} · not for kids`
          : forKids
            ? "For kids"
            : "Kids";
    kids.title = verdict === "unrated" ? "" : "Decided by the title's age rating, not by a mark";
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
  kids.addEventListener("click", () => {
    if (!title || kids.hidden || kidsVerdict(title) !== "unrated") return;
    state.setKids(title.setId, !state.isKids(title.setId));
    refreshKids();
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
