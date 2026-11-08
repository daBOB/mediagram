/**
 * The browser side of `library-session.js`'s port: `fetch`, `EventSource` and
 * `document.visibilityState`, gathered in one place so the session itself
 * never touches any of them directly and is tested without a browser.
 */

/** @returns {import("./library-session.js").LibraryPort} */
export function browserLibraryPort() {
  return {
    async fetchCatalog(onBytes) {
      const response = await fetch("/api/sets");
      if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
      if (!onBytes || !response.body) return response.text();
      // Read in chunks only to count them, for the startup's bar. The bytes
      // read are decoded ones, so the length is too (`compression.ts`).
      const total = Number(response.headers.get("x-decoded-length") ?? response.headers.get("content-length")) || 0;
      const reader = response.body.getReader();
      const chunks = [];
      let received = 0;
      onBytes(0, total);
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        received += value.byteLength;
        onBytes(received, total);
      }
      return new Blob(chunks).text();
    },

    listen({ catalog, open, state, together, posters }) {
      const events = new EventSource("/api/events");
      events.addEventListener("catalog", catalog);
      events.addEventListener("open", open);
      events.addEventListener("state", state);
      events.addEventListener("together", (event) => together(JSON.parse(event.data)));
      events.addEventListener("posters", (event) => posters(JSON.parse(event.data)));
      return () => events.close();
    },

    visible: () => document.visibilityState === "visible",
    onVisibility(fn) {
      document.addEventListener("visibilitychange", fn);
    },
  };
}
