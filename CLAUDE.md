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

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.
- **Living Document**: This file is not static — it evolves with each mistake learned.

## Versioning

After any code changes, bump the `version` field in both manifests, in step:

- `Cargo.toml` (workspace root) — the uploader and `mlib-spec`, which inherit it
- `web/package.json` — the player

Both carry the same number: they are two programs of one project, and a reader
who finds them disagreeing has no way to tell which is the project's version.

Following semver:

- **patch** (`0.1.0` → `0.1.1`): bug fixes, docs, refactors with no behavior change
- **minor** (`0.1.0` → `0.2.0`): new features, backwards-compatible additions
- **major** (`0.1.0` → `1.0.0`): breaking changes, removed APIs, schema migrations requiring manual
  steps

Bump before committing so the commit reflects the new version.

## Changelog
- 2026-09-20: Added § Versioning: bump the version after any code change, before committing.
  Names both manifests, because the version lived only in the workspace `Cargo.toml` and
  `web/package.json` had no `version` field at all — the rule as first written had nothing to
  bump. The player's manifest now carries `0.1.0` to match, so the two start in step.
- 2025-01-XX: Added §4 Meta Self-Improvement loop; CLAUDE.md now updates itself with user approval. Fixed typos ("Plan Node" → "Plan Mode", "One tack" → "One task", "Minimat" → "Minimal").
- 2026-09-18: Task Management steps 1 and 5 now name `plans/`, which is where planning has actually happened since the first phase; `tasks/todo.md` never existed and duplicated it. Lessons stay at `tasks/lessons.md`, which now exists — it was the half of the rule with no home.
