/**
 * The control overlay's focus rules and rest timer, independent of media sources.
 *
 * `holding` answers whether something the viewer opened — a menu, a panel —
 * is up; the card stays while it is, and whatever closes it calls `show` so
 * the clock starts again from then.
 */
export function mountPlayerHud({ dialog, video, card, holding }) {
  const REST_MS = 2600;
  let active = false;
  let timer = null;
  /** A pointer on the card is a viewer about to use it. */
  let over = false;

  function clear() {
    active = false;
    over = false;
    clearTimeout(timer);
    timer = null;
    dialog.classList.remove("resting");
  }

  function show() {
    if (!active) return;
    dialog.classList.remove("resting");
    clearTimeout(timer);
    if (video.paused || over || holding()) return;
    const focused = document.activeElement;
    // Neither the picture nor the dialog itself holds the controls open — a
    // click on the picture focuses the dialog — but a focused button does.
    if (focused !== null && focused !== video && focused !== dialog && dialog.contains(focused)) return;
    // Asked again as the clock runs out: a menu opened since it started — by
    // the very click that started it — holds the card as much as one open before.
    timer = setTimeout(() => {
      if (!holding()) dialog.classList.add("resting");
    }, REST_MS);
  }

  for (const event of ["pointermove", "pointerdown", "focusin", "focusout"]) {
    dialog.addEventListener(event, show);
  }
  for (const event of ["play", "pause", "ratechange"]) {
    video.addEventListener(event, show);
  }
  card.addEventListener("pointerenter", () => {
    over = true;
    show();
  });
  card.addEventListener("pointerleave", () => {
    over = false;
    show();
  });

  return {
    open() {
      active = true;
      show();
    },
    show,
    clear,
  };
}
