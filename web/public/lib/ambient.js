/**
 * Settings > Appearance > Artwork > Ambient: rounded cards and a soft glow
 * drifting behind the page, in a colour taken from a poster, as on the
 * Android app's tablet. The glow is one fixed layer of CSS-drawn blobs
 * (`styles/ambient.css`), so scrolling and drifting never redraw the page.
 *
 * Its colour: a title's or show's page glows in its own picture's colour; on
 * Home and the shelves it is the colour of the row scrolled to, its first poster on screen at about a third of the way down the
 * window, easing to the next as it changes. The glow keeps off the artwork of
 * a hero (`keepClearOf`): a paper-coloured veil under each, feathered over 96px.
 *
 * Loaded as its own module by index.html; mounted while `data-backdrop="ambient"`
 * is on <html> (`appearance-boot.js`).
 */

import { el } from "./dom.js";
import { glowOfImage } from "./ambient-colour.js";

/** How far down the window the reading line sits. */
const LINE = 1 / 3;
/** A moment after the last scroll or change, so a fling eases through its rows. */
const SETTLE_MS = 120;
const BLOBS = 4;
/** The heroes' artwork: a title page, a department page, the home cover. */
const ARTWORK = "main :is(.spread-art, .dept-art, .cover)";

let mounted = null;
const watched = new Set();

/** Follows the viewer's Artwork choice: Ambient is mounted while it is the one chosen. */
function startAmbient() {
  const sync = () => {
    const on = document.documentElement.dataset.backdrop === "ambient";
    if (on && !mounted) mounted = mount();
    else if (!on && mounted) { mounted(); mounted = null; }
  };
  addEventListener("mediagram:appearance", sync);
  addEventListener("storage", sync);
  sync();
}

function mount() {
  const glow = el("div", "ambient-glow");
  glow.setAttribute("aria-hidden", "true");
  for (let i = 0; i < BLOBS; i++) glow.append(el("i"));
  const veils = el("div", "ambient-veils");
  veils.setAttribute("aria-hidden", "true");
  document.body.prepend(glow, veils);

  const main = document.getElementById("main");
  const stop = new AbortController();
  const options = { signal: stop.signal };
  let timer = 0, frame = 0;

  const repick = () => {
    const rgb = colourNow(main);
    if (rgb) glow.style.setProperty("--ambient", `rgb(${rgb.join(" ")})`);
  };
  const resettle = () => { clearTimeout(timer); timer = setTimeout(repick, SETTLE_MS); };
  const remeasure = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(() => keepClearOf(veils, resizes)); };
  const resizes = new ResizeObserver(remeasure);
  const changed = () => { resettle(); remeasure(); };

  const mutations = new MutationObserver(changed);
  mutations.observe(main, { childList: true, subtree: true });
  addEventListener("scroll", resettle, { passive: true, ...options });
  addEventListener("resize", changed, options);
  // A poster's colour is read once it has loaded.
  document.addEventListener("load", resettle, { capture: true, ...options });
  changed();

  return () => {
    stop.abort();
    mutations.disconnect();
    resizes.disconnect();
    watched.clear();
    clearTimeout(timer);
    cancelAnimationFrame(frame);
    glow.remove();
    veils.remove();
  };
}

/** A title page holds its own picture's colour; any other page reads the row at the reading line. */
function colourNow(main) {
  if (main.querySelector(".spread")) {
    const art = main.querySelector(".spread-art img");
    return art ? glowOfImage(art) : null;
  }
  const card = cardAtReadingLine(main);
  return card ? glowOfImage(card.img) : null;
}

/**
 * Of the posters on screen and not scrolled off the left edge, the row whose
 * centre lies nearest the reading line, and in that row the left-most. A row
 * is the cards whose centres sit within half a card's height of the nearest.
 */
function cardAtReadingLine(main) {
  const line = innerHeight * LINE;
  const seen = [...main.querySelectorAll(".thumb img")]
    .map((img) => ({ img, box: img.getBoundingClientRect() }))
    .filter(({ img, box }) => img.complete && img.naturalWidth > 0 && box.width > 0 && box.right > 0 && box.bottom > 0 && box.top < innerHeight)
    .map((card) => ({ ...card, centre: (card.box.top + card.box.bottom) / 2 }));
  if (!seen.length) return null;
  const nearest = seen.reduce((a, b) => (Math.abs(b.centre - line) < Math.abs(a.centre - line) ? b : a));
  const half = nearest.box.height / 2;
  return seen
    .filter((card) => Math.abs(card.centre - nearest.centre) <= half)
    .reduce((a, b) => (b.box.left < a.box.left ? b : a));
}

/** One paper-coloured veil under each hero's artwork, in document coordinates, so it scrolls with the page. */
function keepClearOf(veils, resizes) {
  const arts = [...document.querySelectorAll(ARTWORK)];
  // Observing an element reports it once; only new ones are, or this would feed itself.
  for (const art of watched) if (!arts.includes(art)) { resizes.unobserve(art); watched.delete(art); }
  veils.replaceChildren(...arts.map((art) => {
    if (!watched.has(art)) { resizes.observe(art); watched.add(art); }
    const box = art.getBoundingClientRect();
    const veil = el("div", "ambient-veil");
    Object.assign(veil.style, { left: `${box.left + scrollX}px`, top: `${box.top + scrollY}px`, width: `${box.width}px`, height: `${box.height}px` });
    return veil;
  }));
}

startAmbient();
