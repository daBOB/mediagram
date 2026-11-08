/**
 * The server's artwork fetch, drawn as a hairline along the masthead's foot
 * with a line of words under it: which step, how far, and how long that
 * step's pace says is left.
 *
 * The phone and the TV say the same sentences (`UpdateLibrary.kt`), so the
 * wait reads the same wherever a viewer meets it. The fetch runs on the
 * server after each new index (`catalog-follow.ts`); without this a page
 * showed initials where posters would be and nothing to say they were coming.
 */

const STEPS = { lookup: "Looking up titles", download: "Downloading artwork" };

/** "Looking up titles · 212 of 640 · about 3 min left". */
export function artworkSentence({ step, done, total, elapsedMs }) {
  const count = `${done.toLocaleString("en")} of ${total.toLocaleString("en")}`;
  return [STEPS[step] ?? "Fetching artwork", count, timeLeft(done, total, elapsedMs)].filter(Boolean).join(" · ");
}

/**
 * What the step's own pace says is left, or `null` until it has a pace: the
 * first items carry the connection's setup, and an estimate that opens at an
 * hour and drops to minutes reads as broken.
 */
export function timeLeft(done, total, elapsedMs) {
  if (done < 5 || elapsedMs < 3000) return null;
  const minutes = Math.ceil((elapsedMs * (total - done)) / done / 60_000);
  return minutes <= 1 ? "less than a minute left" : `about ${minutes} min left`;
}

/** The drawn progress while a fetch runs; one page has one masthead, so one of these. */
let shown = null;

/** Shows `progress` at the foot of `host` (the masthead), or takes it away for `null`. */
export function showArtworkProgress(host, progress) {
  if (!progress) {
    shown?.box.remove();
    shown = null;
    return;
  }
  if (!shown) {
    const box = document.createElement("div");
    box.className = "artwork-progress";
    const bar = document.createElement("progress");
    bar.max = 1;
    const words = document.createElement("p");
    // The bar carries the same words as its label; read once, not twice.
    words.setAttribute("aria-hidden", "true");
    box.append(bar, words);
    host.append(box);
    shown = { box, bar, words };
  }
  const sentence = artworkSentence(progress);
  shown.bar.value = progress.total > 0 ? progress.done / progress.total : 0;
  shown.bar.setAttribute("aria-label", sentence);
  shown.words.textContent = sentence;
}
