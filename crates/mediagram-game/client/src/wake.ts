import NoSleep from "nosleep.js";

// Plain LAN HTTP is not a secure context, so the Wake Lock API is missing on
// phones and NoSleep falls back to a tiny muted looping video. iOS only lets
// that start inside a user gesture, so every tap retries until it holds.
const noSleep = new NoSleep();
let wanted = false;

function tryEnable() {
  if (wanted && !noSleep.isEnabled) noSleep.enable().catch(() => {});
}

export function keepAwake() {
  wanted = true;
  tryEnable();
}

document.addEventListener("pointerdown", tryEnable, { capture: true });
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") tryEnable();
});
