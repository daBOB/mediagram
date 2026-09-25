/**
 * The Featured reel: films not yet watched, one poster at a time, to help
 * choose what to watch.
 *
 * Dark, like the player, because it is the lobby of the screening room and a
 * poster reads best against black. Each slide is built when it is shown and
 * faded in over the last, which is removed once the fade ends; the motion
 * itself is all in the stylesheet, so reduced motion is one media query.
 *
 * Opening pushes a history entry, so the back button closes the reel rather
 * than leaving the shelf underneath it.
 */

import { el } from "../dom.js";
import { genresOf, scoreLabel } from "./genres.js";
import { stepFrom } from "./featured-picks.js";

const HOLD_MS = 7000;
// Matches the crossfade in the stylesheet, so the old slide goes when it is invisible.
const FADE_MS = 1200;

/** The dialog and the parts of it that change, found once. */
let reel = null;
let films = [];
let index = 0;
let paused = false;
let timer = 0;
let generation = 0;
/** The slide on screen, whose words may still be on their way. */
let current = null;
/** What to do once the reel's history entry is gone: play, or open a page. */
let after = null;
let actions = { play: () => {}, openFilm: () => {} };
const described = new Map();

/** Starts the reel over `picks`; `handlers` play a film or open its page. */
export function openFeatured(picks, handlers) {
  if (picks.length === 0) return;
  reel ??= setUp();
  films = picks;
  actions = handlers;
  index = 0;
  paused = false;
  after = null;
  current = null;
  reel.dialog.classList.remove("paused");
  reel.slides.textContent = "";
  drawDots();
  history.pushState({ featured: true }, "");
  reel.dialog.showModal();
  void show(0);
}

function setUp() {
  const dialog = document.getElementById("featured");
  const slides = el("div", "featured-slides");
  const close = el("button", "featured-close", "✕");
  close.type = "button";
  close.setAttribute("aria-label", "Close");
  close.addEventListener("click", () => dialog.close());
  const dots = el("div", "featured-dots");
  const nav = el("nav", "featured-nav");
  nav.append(navButton("‹", "Previous film", () => step(-1)), dots, navButton("›", "Next film", () => step(1)));
  dialog.append(slides, close, nav);

  dialog.addEventListener("keydown", (event) => {
    if (event.key === "ArrowRight") step(1);
    else if (event.key === "ArrowLeft") step(-1);
    // Space on a button presses it; anywhere else it pauses the reel.
    else if (event.key === " " && event.target?.tagName !== "BUTTON") {
      event.preventDefault();
      paused = !paused;
      dialog.classList.toggle("paused", paused);
      schedule();
    }
  });
  dialog.addEventListener("close", () => {
    clearTimeout(timer);
    generation++;
    if (history.state?.featured) history.back();
    else runAfter();
  });
  window.addEventListener("popstate", () => {
    if (dialog.open) dialog.close();
    else runAfter();
  });
  document.addEventListener("visibilitychange", schedule);
  return { dialog, slides, dots };
}

function navButton(label, name, onClick) {
  const button = el("button", "featured-step", label);
  button.type = "button";
  button.setAttribute("aria-label", name);
  button.addEventListener("click", onClick);
  return button;
}

function runAfter() {
  const pending = after;
  after = null;
  pending?.();
}

function step(by) {
  void show(stepFrom(index, by, films.length));
}

/** Waits the hold time, unless paused or nobody can see the reel. */
function schedule() {
  clearTimeout(timer);
  if (!reel?.dialog.open || paused || document.visibilityState === "hidden") return;
  timer = setTimeout(() => step(1), HOLD_MS);
}

async function show(at) {
  clearTimeout(timer);
  const turn = ++generation;
  index = at;
  const set = films[at];
  const slide = slideFor(set, at);
  // Decoded before the fade, so no poster arrives half drawn.
  await slide.image.decode().catch(() => {});
  if (turn !== generation || !reel.dialog.open) return;

  const leaving = current;
  current = slide;
  reel.slides.append(slide.node);
  requestAnimationFrame(() => slide.node.classList.add("on"));
  if (leaving) {
    leaving.node.classList.remove("on");
    setTimeout(() => leaving.node.remove(), FADE_MS);
  }
  for (const [dot, button] of [...reel.dots.children].entries()) {
    button.setAttribute("aria-current", dot === at ? "true" : "false");
  }
  void describe(set, slide);
  // The next film's words are asked for while this one holds.
  void metaOf(films[stepFrom(at, 1, films.length)]);
  schedule();
}

function slideFor(set, at) {
  const poster = `/api/posters/${encodeURIComponent(set.poster)}.jpg`;
  const node = el("section", "featured-slide");
  // The film's own wide artwork behind the poster where it has one, only
  // softened; otherwise the poster itself, blurred into a wash.
  const blur = el("div", set.backdrop ? "featured-blur backdrop" : "featured-blur");
  const behind = set.backdrop ? `/api/posters/${encodeURIComponent(set.backdrop)}.jpg` : poster;
  blur.style.backgroundImage = `url("${behind}")`;
  const frame = el("figure", "featured-poster");
  const image = el("img");
  image.src = poster;
  image.alt = "";
  frame.append(image);

  const copy = el("div", "featured-copy");
  copy.append(el("p", "featured-count", `Featured · ${at + 1} / ${films.length}`));
  copy.append(el("h2", "featured-title", set.title ?? set.setId));
  const facts = el("p", "featured-facts", [set.year, ...genresOf(set).slice(0, 3)].filter(Boolean).join(" · "));
  const buttons = el("div", "featured-actions");
  buttons.append(
    actionButton("Play", "featured-play", () => actions.play(set)),
    actionButton("Details", "featured-details", () => actions.openFilm(set)),
  );
  copy.append(facts, buttons);
  node.append(blur, frame, copy);
  return { node, image, copy, facts, buttons };
}

/** Closes the reel first, then acts, so the action is not undone by its history entry. */
function actionButton(label, className, action) {
  const button = el("button", className, label);
  button.type = "button";
  button.addEventListener("click", () => {
    after = action;
    reel.dialog.close();
  });
  return button;
}

function drawDots() {
  reel.dots.textContent = "";
  films.forEach((set, at) => {
    const dot = el("button", "featured-dot");
    dot.type = "button";
    dot.setAttribute("aria-label", set.title ?? set.setId);
    dot.addEventListener("click", () => void show(at));
    reel.dots.append(dot);
  });
}

/** The provider's score and tagline, asked for once per film. */
function metaOf(set) {
  if (!set?.showKey) return Promise.resolve(null);
  if (!described.has(set.showKey)) {
    described.set(set.showKey, fetch(`/api/shows/${encodeURIComponent(set.showKey)}`)
      .then((res) => (res.ok ? res.json() : null))
      .catch(() => null));
  }
  return described.get(set.showKey);
}

async function describe(set, slide) {
  const meta = await metaOf(set);
  // Only onto the slide still showing; one stepped past keeps what it had.
  if (!meta || slide !== current) return;
  const score = scoreLabel(meta.rating);
  if (score) slide.facts.textContent = [slide.facts.textContent, score].filter(Boolean).join(" · ");
  if (meta.tagline) slide.buttons.before(el("p", "featured-tagline", meta.tagline));
}
