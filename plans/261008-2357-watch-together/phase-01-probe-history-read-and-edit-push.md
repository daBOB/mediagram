# Phase 01 — Probe: history read and edit push

**Spec:** `../spec.md` §7. **Status:** pending. **Kind:** spike. The code is thrown away; only the measured answer is kept.

## Why

The design rests on two facts that this project has not yet measured for an **unpinned text** message:

1. A history read of the newest 100 channel messages includes a message sent a second earlier. Search is known **not** to (`web/src/telegram/state-channel.ts:18-26`).
2. Another session of the account hears an edit to that message within about a second, the same way it hears `#mlib-state` edits today (memory: "push updates arrive in ms").

## Rules

- **Ask the user before running it.** It posts to the real library channel. Never pin anything in the probe (the pin flood limit was measured at FLOOD_WAIT_633).
- One message, at most 5 edits, deleted at the end.
- Keep the script in the session scratchpad, not in the repo.

## Task 1.1: Run the probe

- [ ] Write `probe-together.ts` in the scratchpad. It opens two teleproto clients on the web player's stored session:
  - Get the session and channel the way `web/src/index.ts` does, through `web/src/config.ts` and `TelegramConnection`.
  - Sharing one session string across two clients counts as one session. The second listener must therefore be the **Android core**: run the probe while the tablet (`ANDROID_SERIAL=caad49da`) has the app open and is logging `nextLibraryEvent`.
  - Simpler alternative: a second web player process logged in as a separate session (Settings → sessions). Use whichever the user prefers.
- [ ] **Step 1.** `sendMessage(peer, { message: "#mlib-together v=1\n{\"probe\":true}", silent: true })`. Note `sent.id` and the time.
- [ ] **Step 2.** One second later, `getMessages(peer, { limit: 100 })` with **no** `search`. Check that `sent.id` is in the result. Record yes/no.
- [ ] **Step 3.** Five edits, two seconds apart, each changing the text. On the listening session, log the arrival time of every `UpdateEditChannelMessage` with that id. Record the latencies.
- [ ] **Step 4.** `deleteMessages(peer, [sent.id], { revoke: true })`.
- [ ] Write `plans/261008-2357-watch-together/reports/probe-261009-history-read-and-edit-push-report.md`:
  - the two answers;
  - the latencies;
  - any FLOOD_WAIT seen.

## Success criteria

- History read finds the message: **yes**.
- Edit push median under 1 s.

If either fails, stop and bring it back to the user before phase 03. The fallbacks:
- **History read misses fresh messages:** the starting screen also writes the session id into its own `#mlib-state` document. That is a format change; ask first.
- **Edit pushes are slow:** each screen polls `read(id)` every 2 s while in a session.

## Todo

- [ ] 1.1 Probe run and report written
