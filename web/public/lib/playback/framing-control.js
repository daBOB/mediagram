/**
 * The Framing button: how the picture sits in the window, chosen from a menu
 * or stepped through with `z`.
 *
 * Both go through one setter, so the button's label can never disagree with
 * the picture — before the button existed, `z` changed the framing and
 * nothing on screen said what it had become. The decisions themselves are
 * `framing.js`'s; this is the element and the button.
 */

import { DEFAULT_FRAMING, framingBox, framingLabel, framingStyle, framings, nextFraming } from "./framing.js";

/**
 * @param {{video: HTMLVideoElement, button: HTMLElement,
 *   menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   recall?: (name: string) => string|null, remember?: (name: string, value: string) => void}} deps
 */
export function mountFramingControl({ video, button, menus, recall, remember }) {
  /** How the picture sits in the window. Per title, not per player. */
  let framing = DEFAULT_FRAMING;

  /**
   * `fit`/`fill` are left to the stylesheet's own `inset: 0; width: 100%;
   * height: 100%` — only `objectFit` changes for them. A named ratio
   * overrides all four with a pixel box computed against the stage this
   * element's parent already is (`framingBox`; see there for why `object-fit`
   * alone cannot do this): cleared back to the stylesheet's own values the
   * moment framing moves off a named ratio, so `fit`/`fill` are never left
   * sized to a stale window from before.
   */
  function apply() {
    const style = framingStyle(framing);
    video.style.objectFit = style.objectFit;
    video.style.aspectRatio = style.aspectRatio;
    const box = framingBox(framing, video.parentElement?.clientWidth ?? 0, video.parentElement?.clientHeight ?? 0);
    video.style.left = box ? `${box.left}px` : "";
    video.style.top = box ? `${box.top}px` : "";
    video.style.width = box ? `${box.width}px` : "";
    video.style.height = box ? `${box.height}px` : "";
    button.textContent = framingLabel(framing);
  }

  function choose(name) {
    framing = name;
    apply();
    remember?.("framing", name);
  }

  menus.list(button, {
    items: () => framings().map((one) => ({ value: one.name, label: one.label, current: one.name === framing })),
    pick: choose,
  });
  // A named ratio's window is computed against the stage's own pixels, which
  // a window resize (or entering/leaving fullscreen, which resizes it too)
  // changes without this hearing about it any other way; `fit`/`fill`
  // re-apply the same 100% they already were, so this is a no-op for them.
  window.addEventListener("resize", apply);

  return {
    /**
     * How this show was last framed. A framing is a property of how a show
     * was mastered, so it is remembered; one this player does not offer is
     * the default rather than a menu with nothing marked.
     */
    recall() {
      const asked = recall?.("framing");
      framing = framings().some((one) => one.name === asked) ? asked : DEFAULT_FRAMING;
      apply();
    },
    /** `z`: the next framing round the cycle. */
    cycle: () => choose(nextFraming(framing)),
  };
}
