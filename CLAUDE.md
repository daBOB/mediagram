# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.


## Workflow Orchestration

### 1. Plan Mode Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

### 3. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### 4. Meta Self-Improvement (CLAUDE.md Evolution)
- **Trigger**: When the same lesson appears in `tasks/lessons.md` across 2+ projects, OR when a correction reveals a gap in CLAUDE.md itself
- **Action**: Propose an edit to CLAUDE.md with:
  - The recurring pattern observed
  - The proposed rule (concise, actionable, generalizable)
  - Which existing section it belongs in (or new section if needed)
- **Confirmation**: Always show the diff and get user approval before writing to CLAUDE.md
- **Pruning**: If a rule hasn't been triggered in 10+ sessions, flag it for removal — keep this file lean
- **Versioning**: Add a dated entry at the bottom under `## Changelog` for every CLAUDE.md update (one line: date + what changed + why)
- **No silent edits**: Never modify CLAUDE.md without explicit user sign-off, even if confidence is high

### 5. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 6. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### 7. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

## Task Management
1. **Plan First**: Write plan to a dated dir under `plans/` (`plan.md` + `phase-NN-*.md`) with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to the plan's `plan.md`, or a report under its `reports/`
6. **Capture Lessons**: Update `tasks/lessons.md` after corrections
7. **Evolve Rules**: When lessons recur, propose CLAUDE.md updates (see §4)

## Surface Parity

The web player and the Android app are two surfaces over one library. A
viewer who uses both should not have to learn them twice.

- **Parity is the default.** A feature on one surface is owed to the other.
  Shipping it once is half the job, not the whole of it.
- **The web player is the reference.** It is further along and its behaviour
  is already decided. Before building an Android surface that has a web
  counterpart, read the web implementation and match the decisions it made —
  how a shelf is grouped, what a card stands for, what happens to something
  unrecognised. Re-deciding it separately is how the two drift.
- **A deliberate difference is fine; a silent one is not.** Where a surface
  must differ, write down why, where someone will find it: the Android
  catalog has no posters because the pinned channel index carries none, and
  the phase that chose that says so.
- **Divergence is a defect in the newer surface.** "The categories are not
  correct" was the Android catalog listing every episode as its own card
  while the web player had long grouped them into shows. Treat a gap between
  the two as a bug until shown otherwise.

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.
- **Living Document**: This file is not static — it evolves with each mistake learned.

## Versioning

After any code changes, bump the version in every manifest, in step:

- `Cargo.toml` (workspace root) — the uploader and `mlib-spec`, which inherit it
- `web/package.json` — the player
- `android/app/build.gradle.kts` — `versionName`, the Android app

All three carry the same number: they are three programs of one project, and a
reader who finds them disagreeing has no way to tell which is the project's
version.

`versionCode` beside it is not part of this. It counts builds for Android's
own upgrade check and only ever goes up by one; tying it to semver would mean
inventing an integer from a dotted string, and the two answer different
questions.

Following semver:

- **patch** (`0.1.0` → `0.1.1`): bug fixes, docs, refactors with no behavior change
- **minor** (`0.1.0` → `0.2.0`): new features, backwards-compatible additions
- **major** (`0.1.0` → `1.0.0`): breaking changes, removed APIs, schema migrations requiring manual
  steps

Bump before committing so the commit reflects the new version.

## Changelog
- 2025-01-XX: Added §4 Meta Self-Improvement loop; CLAUDE.md now updates itself with user approval. Fixed typos ("Plan Node" → "Plan Mode", "One tack" → "One task", "Minimat" → "Minimal").
- 2026-09-18: Task Management steps 1 and 5 now name `plans/`, which is where planning has actually happened since the first phase; `tasks/todo.md` never existed and duplicated it. Lessons stay at `tasks/lessons.md`, which now exists — it was the half of the rule with no home.
- 2026-09-20: Added Surface Parity, at the user's request. The Android catalog was listing every episode and lesson as its own card while the web player had long grouped them into shows and courses; it also dropped kinds it did not recognise where the web player deliberately keeps them visible. Both were the same mistake — deciding separately what had already been decided — so the web player is now named as the reference.
- 2026-09-20: Added § Versioning: bump the version after any code change, before committing.
  Names both manifests, because the version lived only in the workspace `Cargo.toml` and
  `web/package.json` had no `version` field at all — the rule as first written had nothing to
  bump. The player's manifest now carries `0.1.0` to match, so the two start in step.
- 2026-09-20: § Versioning now names three manifests, not two. The Android app landed on `main`
  carrying its own `versionName`, which the rule did not mention — so the surface most likely to
  be installed and reported on by a viewer was the one that would silently sit at an old number
  while the other two moved. That is the disagreement the rule exists to prevent. `versionCode`
  is explicitly excluded: it counts builds for Android's upgrade check, not releases. Entries
  also re-ordered oldest-first, which the merge had scrambled.
