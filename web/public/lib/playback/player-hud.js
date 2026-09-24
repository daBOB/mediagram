/** The control overlay's focus rules and rest timer, independent of media sources. */
export function mountPlayerHud({ dialog, video }) {
  const REST_MS = 2600;
  let active = false;
  let timer = null;

  function clear() {
    active = false;
    clearTimeout(timer);
    timer = null;
    dialog.classList.remove("resting");
  }

  function show() {
    if (!active) return;
    dialog.classList.remove("resting");
    clearTimeout(timer);
    if (video.paused) return;
    const focused = document.activeElement;
    // The picture itself does not hold the controls open; a focused button does.
    if (focused !== null && focused !== video && dialog.contains(focused)) return;
    timer = setTimeout(() => dialog.classList.add("resting"), REST_MS);
  }

  for (const event of ["pointermove", "pointerdown", "focusin", "focusout"]) {
    dialog.addEventListener(event, show);
  }
  for (const event of ["play", "pause", "ratechange"]) {
    video.addEventListener(event, show);
  }

  return {
    open() {
      active = true;
      show();
    },
    show,
    clear,
  };
}
