/**
 * The browser side of `library-session.js`'s port: `fetch`, `EventSource` and
 * `document.visibilityState`, gathered in one place so the session itself
 * never touches any of them directly and is tested without a browser.
 */

/** @returns {import("./library-session.js").LibraryPort} */
export function browserLibraryPort() {
  return {
    async fetchCatalog() {
      const response = await fetch("/api/sets");
      if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
      return response.text();
    },

    listen({ catalog, open, state }) {
      const events = new EventSource("/api/events");
      events.addEventListener("catalog", catalog);
      events.addEventListener("open", open);
      events.addEventListener("state", state);
      return () => events.close();
    },

    visible: () => document.visibilityState === "visible",
    onVisibility(fn) {
      document.addEventListener("visibilitychange", fn);
    },
  };
}
