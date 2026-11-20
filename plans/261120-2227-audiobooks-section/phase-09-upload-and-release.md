# Phase 09 — Upload the books and update every app

**Context:** spec § Upload and release; user: "update all apps". Memories: release sessions need the installed uploader; two uploaders share the channel index; one upload slot; Android self-update via `scripts/release-android.sh`.
**Status:** todo. **Depends on:** Phase 08. Outward-facing: the user asked for it ("update all apps").

## Order (an app must know `book` before the index carrying books is published)

- [ ] **1: Uploader here.** `cargo install --path crates/mediagram --locked`; `mediagram --version` prints the new version.
- [ ] **2: Pull first.** `mediagram pull-index` (the other uploader may have published since).
- [ ] **3: Android release.** `scripts/release-android.sh` (rebuilds the core; publishes the pinned app release the devices update from). Check `install-dm` after the box updates.
- [ ] **4: Web player.** Redeploy the live player here (bun on :8770) and on funstation (port 80) the way the last web release was deployed; open `#/audiobooks` on each and see the empty state.
- [ ] **5: Dry run.** `mediagram add-audiobook --dry-run /media/DBI/HiDrive/HB` → 118 lines, all with covers.
- [ ] **6: Upload.** `mediagram add-audiobook /media/DBI/HiDrive/HB --no-push` with `upload_slots = 1`; about 16 GB. Run in the background and watch its log; on interruption re-run the same command (held books are skipped).
- [ ] **7: Publish.** `mediagram push-index`; `mediagram status` shows 118 complete `book` sets and no pending.
- [ ] **8: Other uploader.** Update the uploader on bytefortress to this version before it next uploads (build there as its memory note describes); until then it keeps `book` rows it cannot read.
- [ ] **9: Walks.**
  - Web (live player): Audiobooks page with 13 genre rows; a book plays directly with its cover; a kids test profile sees the 8 Kinderbücher books only and opens one at its limit.
  - Tablet `caad49da` and TV box: the Phase 07 walk against real books, including the 26-hour Red Rising seeked past 20:00:00 and resumed.
  - iPhone/iPad (the user): Safari on the web player, play a book, lock the screen → keeps playing; lock screen shows title and cover. If it pauses, report back — the `<audio>` element follow-up starts there.
- [ ] **10: Record.** Results in `plans/261120-2227-audiobooks-section/reports/`; update the memory note `audiobooks-section-in-design.md` to shipped (version, message ids of the release), and `plan.md` status.

## Success criteria

All 118 books in the channel and on every surface; every walk item passes or is written down as a follow-up.
