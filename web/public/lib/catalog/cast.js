/**
 * The Cast tab and a person's page, from the credits schema v9 carries.
 *
 * The tab is added only once credits arrive and only when there is a cast:
 * an index from before v9, or a title TMDB lists nobody for, simply has no
 * Cast tab rather than an empty one.
 */

import { el } from "../dom.js";
import { firstItemOf } from "../library.js";
import { artworkUrl, initialsOf } from "./plate.js";
import { collectionGrid, heading, movieGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";

/** Fetches `key`'s credits and, if anyone is listed, adds a Cast tab to `tabs` at `at`. */
export function offerCast(tabs, key, at = 1) {
  if (!key) return;
  fetch(`/api/shows/${encodeURIComponent(key)}/credits`)
    .then((res) => (res.ok ? res.json() : null))
    .then((credits) => {
      if (credits?.cast?.length > 0 && tabs.isConnected !== false) {
        tabs.addTab({ label: "Cast", build: () => castPanel(credits) }, at);
      }
    })
    .catch(() => {});
}

/** A person's round portrait, or their initials when no image is held. */
function faceOf({ name, portrait }) {
  const face = el("span", "person-face");
  if (portrait) {
    const image = el("img");
    image.src = artworkUrl(portrait);
    image.alt = "";
    image.loading = "lazy";
    image.decoding = "async";
    face.append(image);
  } else {
    face.append(el("span", "person-initials", initialsOf(name)));
  }
  return face;
}

/** A portrait with a name under it, linking to the person's page. */
export function personCard(person) {
  const { personId, name, role } = person;
  const card = el("a", "person");
  card.href = `#/person/${personId}`;
  card.append(faceOf(person), el("span", "person-name", name));
  if (role) card.append(el("span", "person-role", role));
  return card;
}

function castPanel({ cast, crew }) {
  const box = el("div", "cast-panel");
  if (crew.length > 0) {
    const line = el("p", "cast-crew");
    line.append(...crew.flatMap((person, at) => {
      const link = el("a", null, person.name);
      link.href = `#/person/${person.personId}`;
      return [at > 0 ? ", " : `${person.role === "Creator" ? "Created by" : "Directed by"} `, link];
    }));
    box.append(line);
  }
  const row = el("div", "people");
  row.append(...cast.map(personCard));
  box.append(row);
  return box;
}

/** People answers already fetched, so a redraw of the page draws at once. */
const known = new Map();

/**
 * A person's page: their portrait and name, then the titles in *this*
 * library they appear in — resolved against the rows this profile can see.
 * Someone with no title this profile can see is not shown at all, so a kids
 * profile never learns who is in a film it cannot open.
 *
 * @param {{ byKey: (key: string) => { films: any[], shows: any[] }, openFilm: (set: any) => void,
 *   openShow: (section: string, name: string) => void }} on
 */
export async function renderPerson(main, personId, { byKey, openFilm, openShow }, stillHere) {
  if (!known.has(personId)) {
    const res = await fetch(`/api/people/${encodeURIComponent(personId)}`).catch(() => null);
    const person = res?.ok ? await res.json().catch(() => null) : null;
    if (person) known.set(personId, person);
    if (!stillHere()) return;
  }
  const person = known.get(personId) ?? null;
  const { films, shows } = (person?.titles ?? []).map(byKey).reduce(
    (all, found) => ({ films: [...all.films, ...found.films], shows: [...all.shows, ...found.shows] }),
    { films: [], shows: [] },
  );
  if (!person || films.length + shows.length === 0) {
    heading(main, "Person");
    main.append(el("p", "empty", "Nobody by that number is credited on anything in your library."));
    return;
  }
  heading(main, person.name, `${films.length + shows.length} in your library`);
  main.firstElementChild?.prepend(faceOf(person));
  if (films.length > 0) main.append(el("h2", "shelf-sub", "Films"), movieGrid(films, openFilm, { mode: GRID }));
  if (shows.length > 0) {
    main.append(el("h2", "shelf-sub", "Series"), collectionGrid("series", shows, (name) => openShow("series", name), { mode: GRID }));
  }
}

/**
 * Search's people, narrowed to those credited on a title this profile can
 * see, each counted by the titles it can see.
 */
export function visiblePeople(people, byKey) {
  return people
    .map((person) => ({
      ...person,
      titles: person.titles.filter((key) => {
        const found = byKey(key);
        return found.films.length + found.shows.length > 0;
      }).length,
    }))
    .filter((person) => person.titles > 0);
}

/** Resolves a title key to the films and shows this profile's library holds under it. */
export function titlesByKey(library) {
  return (key) => ({
    films: library.movies.filter((set) => set.showKey === key),
    shows: library.series.filter((show) => firstItemOf(show.divisions)?.showKey === key),
  });
}
