import type { LibraryPort, LibraryPortListeners } from "../../public/lib/library-session.js";

/**
 * An in-memory `LibraryPort`: no fetch, no `EventSource`, no
 * `document.visibilityState` — a test drives all three by hand, and can see
 * how many times the session asked for either.
 */
export function fakeLibraryPort(initialText = "[]") {
  let text = initialText;
  const answers: Array<() => Promise<string>> = [];
  let visible = true;
  let onVisibilityFn: (() => void) | null = null;
  let listeners: LibraryPortListeners | null = null;
  let fetches = 0;
  let streamOpens = 0;

  const port: LibraryPort = {
    fetchCatalog() {
      fetches++;
      const next = answers.shift();
      return next ? next() : Promise.resolve(text);
    },
    listen(handlers) {
      listeners = handlers;
      streamOpens++;
      return () => { if (listeners === handlers) listeners = null; };
    },
    visible: () => visible,
    onVisibility(fn) {
      onVisibilityFn = fn;
    },
  };

  return {
    port,
    /** What the next `fetchCatalog()` settles to, once. Queued in order. */
    queueFetch(settled: Promise<string>) {
      answers.push(() => settled);
    },
    /** What the next `fetchCatalog()` resolves to, once. Queued in order. */
    resolveNextWith(value: string) {
      answers.push(() => Promise.resolve(value));
    },
    /** What the next `fetchCatalog()` rejects with, once. */
    failNextWith(error: Error) {
      answers.push(() => Promise.reject(error));
    },
    setText(next: string) {
      text = next;
    },
    setVisible(value: boolean) {
      visible = value;
      onVisibilityFn?.();
    },
    fire(event: keyof LibraryPortListeners) {
      listeners?.[event]();
    },
    get fetches() {
      return fetches;
    },
    get streamOpens() {
      return streamOpens;
    },
    get streamOpen() {
      return listeners !== null;
    },
  };
}
