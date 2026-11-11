---
title: "Sibling worktrees (../mediagram-*) can hold finished, reviewed subagent work never…"
author: Andre
date: 2026-11-08
id: l-41dadc0d
scope: repo
status: active
touches:
  - scripts/check.sh
---

## Decided
Subagents build in sibling worktrees and end with "Not committed — lead commits",
so a worktree can hold a finished, reviewed phase that exists nowhere in git.

**Why:** 2026-09-28, "merge everything and delete worktrees": `mediagram-home`
held phase 03b (department hero, changelog + 0.71.1 bump, code review done) and
`mediagram-phone-review` held phone review fixes from 2026-09-26 — both
uncommitted. `git worktree remove --force` would have destroyed both.

## Watch out
before merging or removing worktrees, run
`git -C ../<wt> status --porcelain` for each; commit finished work onto its own
branch first. Also run `scripts/check.sh` on the result — unpushed branches never
went through the pre-push hook (the preload branch pushed two cache files over
the 200-line limit). Related: bump-versions-by-pattern.

`git worktree remove` (even without --force) silently deletes *ignored* files: `.superpowers/` (SDD ledgers)
sits in the shared info/exclude, `.claude/`, `.impeccable/`, `.desloppify/`, `*.db` in .gitignore. Before removing,
…

_From Claude Code's memory of this repo, last updated Sep 28._
