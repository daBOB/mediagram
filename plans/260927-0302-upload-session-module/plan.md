# Upload session module (architecture review candidate A)

One deep module, `crates/mediagram/src/upload/session/`, runs every command's
uploads: `add-show`, `add-course`, `add-docu` (file and folder), `finish-set`
(behind `add`) and `resume`. Terms: `CONTEXT.md` (Upload session, Set,
Publish). Builds on candidate B (`channel_index`). Branch
`refactor/upload-session` in worktree `../mediagram-channel-index`.

## Decisions (user-confirmed 2026-09-27)

| # | Decision |
|---|----------|
| Q1 | All six commands become upload sessions |
| Q2 | Upload lock per item; status re-read after taking it |
| Q3 | One publish at the end; skipped when another process holds the upload lock (it publishes when it ends) |
| Q4 | An item counts as uploaded only when its set completes; incomplete = pending |
| Q5 | One lazy connection for uploads and the publish |
| Q6 | `upload/session`, term "Upload session" in `CONTEXT.md` |
| Q7/Q8 | Interface from design-it-twice: design 1 (minimal) + design 3's source-check-before-connect and test list |
| Q9 | A failure while sending (after the transport's retries) stops the session; `end` still publishes what completed |
| Q10 | `publish_owed` in the local index: set when completed sets go unpublished; any session publishes if it completed something or a publish is owed; every publish clears it |
| Q11 | `add-docu` folder lookup bug fixed first, separately (900e3414, 0.67.1) |

## Interface

`Session::new(cfg, link)`, `upload(items, say) -> Counts` (any number of
times), `end(no_push) -> Result<()>`. `Item { tag, set: Set::{File(NewSet),
Document(Document), Planned(id)}, delete_source }`; `say(&tag, Step::{Start,
End(&Outcome)})`; `Outcome::{Uploaded, AlreadyHeld, Pending, Blocked(e),
Failed(e)}`. Identity derived from the planning data. Port `Link` bundles
`Transport` + `ChannelRemote` behind one lazy connection (Telegram, fake).

Status reconciled 2026-10-04: completed — shipped 0.68.0 (`3f2d906b`).

## Phases

| Phase | Status |
|-------|--------|
| 01 Port: `Link`, `TelegramLink` (TelegramRemote owns clones), `publish_owed` bookkeeping | done |
| 02 Session interface + failing tests (fake link, Document and Planned items) | done |
| 03 Session implementation (identity, per-item run, stop rule, end/publish) | done |
| 04 Switch six commands; delete `Uploader`, `finish_with`, `upload::resume::pending`; port their tests; docs; 0.68.0 | done (0.68.0, `3f2d906b`) |

## Tests (at the session interface)
held re-run never connects · uploads then exactly one publish · `--no-push`
publishes nothing and owes · another upload holding the lock defers and owes ·
an owed publish is paid by the next session · failure while sending stops the
walk, publishes what completed, `end` errors · blocked source continues and
never connects if all blocked · delete only when complete · pending walked
item not re-planned · Planned pending set is finished · docu collection
re-run holds everything · completion then cleanup failure counts as uploaded.

## Risks
- Planning under the lock holds a queued upload for the length of a remux.
  Accepted (Q2); TMDB prompts stay outside (`add` plans before its session).
- Wording drift: resume's "set X complete" becomes the session's "set X added".
