/**
 * How the picture sits in the window.
 *
 * `object-fit: contain` is right, and the stylesheet says why: a 2.39:1 film
 * in a 16:9 window is letterboxed, not cropped, and the bars are the picture's
 * shape rather than a layout failure.
 *
 * It is right about the *file*, which is the problem. A film mastered with the
 * bars already burnt into a 16:9 frame is a correctly-displayed picture of a
 * letterbox, and no amount of being right about the container helps a viewer
 * looking at a band of black on all four sides. This is the way out of that,
 * and it is a viewer's judgement rather than a fact anything can read off the
 * file.
 */

/**
 * The cycle, in the order `z` walks it.
 *
 * `fit` first because it is the honest default and the one to come back to.
 * The two named ratios are for the case above: they crop to a shape the viewer
 * can see is right, which is the only evidence available.
 */
const FRAMINGS = [
  { name: "fit", label: "Fit", fit: "contain", ratio: null },
  { name: "fill", label: "Fill", fit: "cover", ratio: null },
  { name: "16:9", label: "16:9", fit: "cover", ratio: 16 / 9 },
  { name: "4:3", label: "4:3", fit: "cover", ratio: 4 / 3 },
];

export const DEFAULT_FRAMING = FRAMINGS[0].name;

/** What comes after `name` when `z` is pressed. Wraps, and tolerates nonsense. */
export function nextFraming(name) {
  const found = FRAMINGS.findIndex((one) => one.name === name);
  // An unknown name is treated as the default, because that is what the player
  // is showing when it does not know — and then advanced like any other, so
  // pressing `z` always changes something. Letting `-1` fall through the
  // modulo would land back on the default and look like a key that does
  // nothing.
  const at = found === -1 ? 0 : found;
  return FRAMINGS[(at + 1) % FRAMINGS.length].name;
}

/**
 * What a framing does to the element, as styles to set.
 *
 * Returned as values rather than applied, so the decision can be proved and
 * the one place that touches the DOM stays in the player.
 *
 * `aspectRatio` is left empty for the two that do not impose one — setting it
 * to `auto` and setting it to nothing are the same thing to the element, and
 * the empty string is what removes a property.
 */
export function framingStyle(name) {
  const found = FRAMINGS.find((one) => one.name === name) ?? FRAMINGS[0];
  return {
    objectFit: found.fit,
    aspectRatio: found.ratio === null ? "" : String(found.ratio),
  };
}

/** What to call it on screen while it is being changed. */
export function framingLabel(name) {
  return (FRAMINGS.find((one) => one.name === name) ?? FRAMINGS[0]).label;
}

/** Every framing there is, for anything that offers them as a list. */
export function framings() {
  return FRAMINGS.map((one) => ({ name: one.name, label: one.label }));
}

/**
 * The box a named ratio crops the picture into, centred in a
 * `containerWidth` x `containerHeight` stage — `null` for `fit`/`fill`,
 * which already fill the stage exactly (`framingStyle`'s `objectFit` alone
 * is enough for those two).
 *
 * `object-fit: cover` never changes the *shape* of the content — it only
 * scales and crops within whatever box it is given. Setting `aspectRatio`
 * on an element whose `width`/`height` are also both definite (this app's
 * own stylesheet sets `video { width: 100%; height: 100% }`) has no effect
 * at all, so a named ratio rendered as no more than `objectFit: "cover"`
 * plus that inert `aspectRatio` — which is what this looked like before
 * this function existed — is indistinguishable from `fill`. Cropping to
 * the *shape* this exists for needs the box itself to be that shape: a
 * window of the named ratio, fit within the stage the same way `fit`
 * itself fits the video's own shape within it, so a viewer sees letterbox
 * bars where the window does not reach the stage's own edges and a crop
 * everywhere inside it — never a distorted picture.
 */
export function framingBox(name, containerWidth, containerHeight) {
  const found = FRAMINGS.find((one) => one.name === name) ?? FRAMINGS[0];
  if (found.ratio === null || containerWidth <= 0 || containerHeight <= 0) return null;
  const containerRatio = containerWidth / containerHeight;
  const box = containerRatio > found.ratio
    ? { width: containerHeight * found.ratio, height: containerHeight }
    : { width: containerWidth, height: containerWidth / found.ratio };
  return { ...box, left: (containerWidth - box.width) / 2, top: (containerHeight - box.height) / 2 };
}
