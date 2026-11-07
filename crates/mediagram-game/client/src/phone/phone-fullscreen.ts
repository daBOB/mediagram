// Game night on the phone runs fullscreen: no address bar, no status bar.
// Browsers only allow it from a tap, so it starts on the join tap, and a
// phone that left fullscreen (a swipe, a notification) is back on its next
// tap. iPhones cannot put a page fullscreen at all; there it does nothing.

import { inApp } from "./app-bridge";

let wanted = false;

function enter() {
  if (!wanted || document.fullscreenElement || !document.documentElement.requestFullscreen) return;
  document.documentElement.requestFullscreen({ navigationUI: "hide" }).catch(() => {});
}

/** Call from a tap: fullscreen now and after every tap that finds it left. */
export function goFullscreen() {
  // the controller apps are fullscreen already
  if (inApp()) return;
  wanted = true;
  enter();
}

document.addEventListener("pointerup", enter, { capture: true });
