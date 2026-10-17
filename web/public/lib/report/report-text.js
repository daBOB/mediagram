/**
 * The words a broken-video report reaches the library's keeper in: a
 * reminder in the account's own Saved Messages. Telegram notifies an
 * account of its own message only when it is a scheduled one there, so the
 * sender schedules it `REPORT_DELAY_SECONDS` ahead. The web is the
 * reference; Android's `ReportText.kt` is held to `report-text.json`.
 */

export const REPORT_REASONS = [
  { id: "wont-play", words: "Won't play" },
  { id: "picture-sound", words: "Picture or sound wrong" },
  { id: "subtitles", words: "Subtitles wrong" },
  { id: "wrong-title", words: "Wrong title or poster" },
];

export const REPORT_WORDS = {
  button: "Report",
  sent: "Reported",
  heading: "What's wrong?",
  note: "Anything else? (optional)",
  send: "Send",
  cancel: "Cancel",
  failed: "Couldn't send. Try again later.",
};

export const NOTE_MAX = 200;
export const REPORT_DELAY_SECONDS = 60;

/** `h:mm:ss`, the hour unpadded, as a player's clock reads past an hour. */
function clock(seconds) {
  const s = Math.floor(seconds);
  const two = (n) => String(n).padStart(2, "0");
  return `${Math.floor(s / 3600)}:${two(Math.floor((s % 3600) / 60))}:${two(s % 60)}`;
}

/** One line, at most `NOTE_MAX` characters; empty when nothing was said. */
export function cleanNote(note) {
  return (note ?? "").replace(/\s+/g, " ").trim().slice(0, NOTE_MAX).trimEnd();
}

export function reportText(f) {
  const reason = REPORT_REASONS.find((r) => r.id === f.reason)?.words ?? f.reason;
  const note = cleanNote(f.note);
  const who = [f.profile, f.device ? `${f.screen} (${f.device})` : f.screen, f.app, f.position == null ? null : `at ${clock(f.position)}`];
  const how = [f.played ? `Played: ${f.played}${f.held ? ", from cache" : ""}` : null, f.error ? `Error: ${f.error}` : null].filter(Boolean);
  return [
    "#mlib-report",
    `${reason}: ${f.title}`,
    note ? `"${note}"` : null,
    who.filter(Boolean).join(" · "),
    how.length > 0 ? how.join(" · ") : null,
    `Check: mediagram verify --full ${f.setId}`,
  ].filter((line) => line !== null).join("\n");
}
