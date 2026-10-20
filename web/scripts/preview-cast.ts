/**
 * A pretend TV for the preview, so the cast surface can be walked in a
 * browser: an in-memory library channel and a TV that answers each request
 * once, as `mode` says. Once a cast is on, the TV makes no moves of its own;
 * the remote's moves come back through the channel's echo.
 */

import type { CatalogEvents } from "../src/catalog-events";
import type { TogetherChannel } from "../src/together/channel";
import { TogetherHub } from "../src/together/hub";
import { accepted, answerOf, busied, refusedBy, tracked } from "../public/lib/together/cast.js";
import { ageReason } from "../public/lib/together/cast-words.js";
import { isRequest, parseSession, writeSession } from "../public/lib/together/session.js";
import type { Session } from "../public/lib/together/session.js";
import { HOME } from "../public/lib/household-rules.js";

export type CastMode = "accept" | "busy" | "refuse" | "none";

const TV = { screen: "preview-tv", label: "TV", remote: false };
const THINK_MS = 300;
const TRACKS = {
  audio: [{ id: "0.0", label: "English 5.1" }, { id: "1.0", label: "Deutsch" }],
  text: [{ id: "en", label: "English" }, { id: "de", label: "Deutsch" }],
};

export function previewCast(mode: CastMode, events: CatalogEvents): TogetherHub {
  const messages = new Map<number, string>();
  const answered = new Set<string>();
  let next = 0;
  let hub!: TogetherHub;

  const hear = (id: number, text: string) => setTimeout(() => hub.heard(id, text), 0);
  const say = (id: number, s: Session) => {
    const text = writeSession(s);
    messages.set(id, text);
    hear(id, text);
  };

  /** What the channel holds for `id` right now; `null` once it is gone. */
  const current = (id: number) => {
    const text = messages.get(id);
    return text === undefined ? null : parseSession(text);
  };

  /** The TV's reply to a request it has not answered yet. */
  const answer = (id: number, s: Session) => {
    const reply = answerOf(s, { screen: TV.screen, refusal: mode === "refuse" ? ageReason("Mia") : null, busy: mode === "busy" ? "Dune (2021)" : null, household: HOME }, Date.now());
    if (reply.kind === "refuse") say(id, refusedBy(s, TV.screen, reply.note));
    else if (reply.kind === "busy") say(id, busied(s, TV.screen, reply.note));
    else if (reply.kind === "accept") {
      const on = accepted(s, TV, Date.now());
      if (!on) return;
      say(id, on);
      setTimeout(() => {
        const now = current(id);
        if (now?.cast?.state === "on") say(id, tracked(now, TV.screen, TRACKS, "0.0", ""));
      }, THINK_MS);
    }
  };

  const request = (id: number, text: string) => {
    const s = parseSession(text);
    const key = s && `${id}:${s.cast?.state}`;
    if (mode === "none" || !s || !isRequest(s) || answered.has(key!)) return;
    answered.add(key!);
    setTimeout(() => {
      // Withdrawn or already answered meanwhile: nothing to answer, and nothing to bring back.
      const now = current(id);
      if (now?.cast?.state === s.cast?.state) answer(id, now!);
    }, THINK_MS);
  };

  const channel: TogetherChannel = {
    available: () => true,
    recent: async () => [...messages].map(([id, text]) => ({ id, text })).sort((a, b) => b.id - a.id),
    read: async (id) => messages.get(id) ?? null,
    send: async (text) => {
      const id = ++next;
      messages.set(id, text);
      hear(id, text);
      request(id, text);
      return id;
    },
    edit: async (id, text) => {
      if (!messages.has(id)) return "gone";
      messages.set(id, text);
      hear(id, text);
      request(id, text);
      return "ok";
    },
    remove: async (id) => { messages.delete(id); },
  };

  hub = new TogetherHub(channel, (id, session, lost) => events.together(id, session, Date.now(), lost));
  return hub;
}
