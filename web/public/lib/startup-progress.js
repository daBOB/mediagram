/**
 * The words under the film leader while the page starts: which wait it is
 * in, and for the catalog how many of its megabytes have arrived.
 *
 * On this machine the whole start takes about a second. On a phone, or from
 * outside the house, the catalog's tens of megabytes and the sorting of
 * every title take several, and "Loading your library…" alone could not say
 * whether anything was happening.
 */

const mb = (bytes) => (bytes / 1e6).toFixed(1);

/** Whether a download step knows its size: a proxy that recompresses can leave it unknown, or wrong. */
const sized = (step) => step.total > 0 && step.total >= step.received;

/** @param {import("./library-session.js").StartupStep} step */
export function startupSentence(step) {
  if (step.step === "sort") return `Sorting ${step.titles.toLocaleString("en")} titles into shelves…`;
  return sized(step)
    ? `Downloading the catalog · ${mb(step.received)} of ${mb(step.total)} MB`
    : `Downloading the catalog · ${mb(step.received)} MB`;
}

/** How far the download is, for the bar; `null` for a step with no size. */
export function startupFraction(step) {
  return step.step === "download" && sized(step) ? step.received / step.total : null;
}

/** A word every chunk is a flicker, and the line is a live region read aloud. */
const MIN_GAP_MS = 200;

/** Draws into the page's loading state (`index.html`), for as long as that is still on the page. */
export function startupProgress() {
  const state = document.getElementById("loading-state");
  const line = document.getElementById("loading-words");
  const bar = document.createElement("progress");
  bar.className = "loading-bar";
  bar.max = 1;
  let shownAt = 0;

  /** @returns {boolean} whether the words reached the page, which is gone once the shelves draw */
  const show = (text, fraction = null) => {
    if (!line?.isConnected) return false;
    line.textContent = text;
    if (fraction === null) {
      bar.remove();
      return true;
    }
    bar.value = fraction;
    if (!bar.isConnected) state.append(bar);
    return true;
  };

  show("Asking the server for the catalog…");
  return {
    /** @param {import("./library-session.js").StartupStep} step */
    catalog(step) {
      if (step.step === "download") {
        const now = performance.now();
        const edge = step.received === 0 || (sized(step) && step.received === step.total);
        if (!edge && now - shownAt < MIN_GAP_MS) return undefined;
        shownAt = now;
        show(startupSentence(step), startupFraction(step));
        return undefined;
      }
      return show(startupSentence(step)) ? painted() : undefined;
    },
    profiles: () => show("Reading profiles and watch state…"),
  };
}

/**
 * Resolves once the words just set have been drawn. A background tab draws
 * no frames, so the timeout makes sure the start never waits on one.
 */
function painted() {
  return new Promise((resolve) => {
    requestAnimationFrame(() => setTimeout(resolve, 0));
    setTimeout(resolve, 100);
  });
}
