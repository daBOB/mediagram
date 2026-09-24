/** The notes column owns fetching, rendering, visibility and its title lifetime. */
import { renderNotes } from "./notes-view.js";

export function mountPlayerNotes({ dialog }) {
  const summary = document.getElementById("summary");
  const button = document.getElementById("notes");
  const panel = document.getElementById("notes-panel");
  const close = document.getElementById("notes-close");
  let request = null;

  function show(visible) {
    panel.hidden = !visible;
    dialog.classList.toggle("with-notes", visible);
    button.setAttribute("aria-expanded", String(visible));
  }

  function clear() {
    request?.abort();
    request = null;
    show(false);
    summary.textContent = "";
    button.hidden = true;
  }

  async function open(set) {
    clear();
    if (!set.hasSummary) return;
    const operation = new AbortController();
    request = operation;
    try {
      const response = await fetch(`/api/sets/${encodeURIComponent(set.setId)}/summary`, {
        signal: operation.signal,
      });
      if (!response.ok) return;
      const markdown = await response.text();
      if (operation.signal.aborted) return;
      // The renderer creates nodes, never markup from server bytes.
      renderNotes(summary, markdown);
      button.hidden = false;
      // A lesson's notes are the point; a film's notes wait to be requested.
      if (set.kind === "tut") show(true);
    } catch {
      // Missing notes must not interrupt playback.
    }
  }

  button.addEventListener("click", () => show(panel.hidden));
  close.addEventListener("click", () => show(false));
  return { open, clear, contains: (target) => panel.contains(target) };
}
