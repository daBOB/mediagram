/**
 * The cover story: one film at a time across the full width of the page,
 * its backdrop behind a headline the size a magazine gives its cover line.
 *
 * Every word on it is the film's own — its title, its tagline, its year and
 * rating and genres. The only thing the page adds is the rotation, which
 * holds each film long enough to read, pauses while the viewer's pointer or
 * focus is on it, and never runs for a viewer who asked for less motion.
 */

import { el } from "../dom.js";
import { humanDuration } from "../format.js";
import { ageLabel } from "../age-rating.js";
import { genresOf, scoreLabel } from "./genres.js";
import { artworkUrl } from "./plate.js";
import { playPill } from "./title-spread.js";
import { listToggle } from "./list-toggle.js";

const HOLD_MS = 9000;
/** Past this many characters a title is set a size smaller, so it still fits in three lines. */
const LONG_TITLE = 18;


/**
 * @param {import("../library.js").CatalogSet[]} films the cover's films, each with a backdrop
 * @param {{play: (set: any) => void}} on
 */
export function coverStory(films, { play }) {
  const section = el("section", "cover");
  section.setAttribute("aria-roledescription", "carousel");
  section.setAttribute("aria-label", "Cover story");
  const stage = el("div", "cover-stage");
  section.append(stage);

  // A redraw of the same page (a sync from another device, say) rebuilds this
  // node; carrying on from the film already showing keeps it from jumping.
  const lineup = films.map((set) => set.setId).join(" ");
  let index = lineup === last.lineup ? Math.min(last.index, films.length - 1) : 0;
  last.lineup = lineup;
  let timer = 0;
  // Held while the pointer is over it or focus is in it — tracked apart, so
  // a mouse leaving cannot restart it under a keyboard viewer's focus — or
  // for good once the viewer pressed pause.
  let pointerIn = false;
  let focusIn = false;
  let stopped = false;
  const rotates = films.length > 1 && !lessMotion();
  const dots = films.length > 1 ? pager(films.length, (next) => show(next)) : null;
  const toggle = rotates ? pauseButton(() => {
    stopped = !stopped;
    toggle.mark(stopped);
    schedule();
  }) : null;

  function show(next, turned = true) {
    index = next;
    last.index = next;
    const shown = slide(films[index], index, films.length, play);
    // A turn of the cover fades even on a settled page; see `redraw.js`.
    if (turned) shown.classList.add("turned");
    stage.replaceChildren(shown);
    dots?.mark(index);
    schedule();
  }

  function schedule() {
    clearTimeout(timer);
    if (!rotates || stopped || pointerIn || focusIn) return;
    timer = setTimeout(() => {
      // The page redraws by replacing `main`'s children, which leaves this
      // node detached; a detached cover has nothing left to rotate.
      if (section.isConnected) show((index + 1) % films.length);
    }, HOLD_MS);
  }

  section.addEventListener("pointerenter", () => { pointerIn = true; schedule(); });
  section.addEventListener("pointerleave", () => { pointerIn = false; schedule(); });
  section.addEventListener("focusin", () => { focusIn = true; schedule(); });
  section.addEventListener("focusout", () => { focusIn = false; schedule(); });

  show(index, false);
  if (dots) {
    if (toggle) dots.node.prepend(toggle.node);
    section.append(dots.node);
  }
  return section;
}

/** The last cover drawn, so a redraw resumes it rather than starting over. */
const last = { lineup: "", index: 0 };

/** Pauses the rotation for good, and starts it again. */
function pauseButton(onToggle) {
  const node = el("button", "cover-pause");
  node.type = "button";
  node.addEventListener("click", onToggle);
  const mark = (stopped) => {
    node.textContent = stopped ? "\u25b6" : "\u2016";
    node.setAttribute("aria-label", stopped ? "Play the cover stories" : "Pause the cover stories");
  };
  mark(false);
  return { node, mark };
}

function lessMotion() {
  return globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

/** One film's cover; the first is the image the page waits on, so it is asked for first. */
function slide(set, at, count, play) {
  const first = at === 0;
  const article = el("article", "cover-slide");
  article.setAttribute("role", "group");
  article.setAttribute("aria-roledescription", "slide");
  article.setAttribute("aria-label", `${at + 1} of ${count}`);
  const image = el("img", "cover-image");
  image.src = artworkUrl(set.backdrop);
  // The headline beside it says what the picture is of.
  image.alt = "";
  image.decoding = "async";
  if (first) image.setAttribute("fetchpriority", "high");
  article.append(image, el("div", "cover-scrim"));

  const copy = el("div", "cover-copy");
  const genres = genresOf(set);
  copy.append(el("p", "eyebrow cover-eyebrow", // "Today" because it is: the cover's films are chosen by the day.
    ["Featured today", genres[0]].filter(Boolean).join(" · ")));
  const title = set.title ?? set.setId;
  const headline = el("h2", "cover-title", title);
  if (title.length > LONG_TITLE) headline.classList.add("long");
  copy.append(headline);
  if (set.tagline) copy.append(el("p", "cover-deck", set.tagline));
  const facts = [set.year, humanDuration(set.duration), ageLabel(set), scoreLabel(set.rating)].filter(Boolean);
  if (facts.length > 0) copy.append(el("p", "cover-meta", facts.join(" · ")));

  const actions = el("div", "cover-actions");
  const watch = playPill("Watch now", () => play(set));
  const details = el("a", "cover-details", "Details");
  details.href = `#/film/${encodeURIComponent(set.setId)}`;
  details.setAttribute("aria-label", `Details: ${title}`);
  actions.append(watch, listToggle(set.setId), details);
  copy.append(actions);
  article.append(copy);

  // The film's own name and genres, set small and spaced down the right,
  // where a cover prints its secondary lines. Repeats the headline, so it is
  // hidden from a screen reader.
  const side = el("div", "cover-side");
  side.setAttribute("aria-hidden", "true");
  side.append(el("p", "cover-side-title", title));
  if (genres.length > 0) {
    side.append(el("span", "cover-side-rule"));
    side.append(el("p", "cover-side-lines", genres.slice(0, 3).join("\n")));
  }
  article.append(side);
  return article;
}

/** The dots: one button per film, the current one marked. */
function pager(count, onPick) {
  const node = el("div", "cover-pager");
  node.setAttribute("role", "group");
  node.setAttribute("aria-label", "Cover stories");
  const buttons = [];
  for (let at = 0; at < count; at++) {
    const dot = el("button", "cover-dot");
    dot.type = "button";
    dot.setAttribute("aria-label", `Cover story ${at + 1} of ${count}`);
    dot.addEventListener("click", () => onPick(at));
    buttons.push(dot);
    node.append(dot);
  }
  return {
    node,
    mark(current) {
      buttons.forEach((dot, at) => {
        dot.classList.toggle("on", at === current);
        dot.setAttribute("aria-pressed", String(at === current));
      });
    },
  };
}
