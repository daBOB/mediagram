/**
 * The account's active sessions: every one this app can see, scoped to its
 * own `api_id` plus the current row. Confirmed in place — a row grows a
 * second button rather than a browser `confirm()` — since that is what the
 * rest of this page already does for a destructive action.
 */

import { el } from "./dom.js";
import { listSessions, revokeSession } from "./settings-api.js";

function row(session, refresh) {
  const item = el("li", "settings-session");
  const label = el("div");
  const title = [session.device, session.current ? "(this device)" : null].filter(Boolean).join(" ");
  label.append(el("p", null, title));
  const detail = [session.platform, session.app, session.location].filter(Boolean).join(" · ");
  label.append(el("p", "sub", detail));
  item.append(label);

  if (!session.current) {
    const actions = el("div", "settings-actions");
    const revoke = el("button", "settings-button danger", "Sign out");
    revoke.type = "button";
    const message = el("p", "settings-message");
    revoke.onclick = () => {
      if (revoke.dataset.confirming === "1") {
        void (async () => {
          revoke.disabled = true;
          const result = await revokeSession(session.id);
          if (!result.ok) {
            revoke.disabled = false;
            revoke.dataset.confirming = "";
            revoke.textContent = "Sign out";
            message.textContent = "";
            message.append(el("p", "error", result.error));
            return;
          }
          await refresh();
        })();
        return;
      }
      revoke.dataset.confirming = "1";
      revoke.textContent = "Confirm sign out";
    };
    actions.append(revoke);
    item.append(actions, message);
  }
  return item;
}

/** Renders the sessions section into `root`. `refresh` re-fetches the whole Settings view. */
export function renderSessionsSection(root, refresh) {
  const section = el("section", "status-block");
  section.append(el("h3", null, "Active sessions"));
  const body = el("div");
  body.append(el("p", null, "Reading the account's sessions…"));
  section.append(body);
  root.append(section);

  void listSessions().then((result) => {
    body.textContent = "";
    if (!result.ok) {
      body.append(el("p", "error", result.error));
      return;
    }
    const list = el("ul", "settings-session-list");
    for (const session of result.sessions) list.append(row(session, refresh));
    body.append(list);
  });
}
