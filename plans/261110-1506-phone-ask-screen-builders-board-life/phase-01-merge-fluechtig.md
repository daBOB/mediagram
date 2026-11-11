# Phase 1 — Merge Flüchtig

Priority: high (unblocks everything). Status: pending. Result: 0.173.0.

## Facts (2026-11-10)

- `feat/fluechtig` is at `ebd23317` (0.173.0), 5 commits ahead of
  `spieltisch-games` and 1 behind. Worktree: `spieltisch/mediagram-fluechtig`.
- `git merge-tree` reports conflicts only in the version files: `Cargo.lock`,
  `Cargo.toml`, `android/app/build.gradle.kts`, `docs/project-changelog.md`,
  `web/package.json`. All code merges by itself.
- No untracked file in `mediagram-games` sits on a path the branch tracks
  (`git ls-tree` on the five untracked paths: 0 files each).

## Decided

- Merge in the `mediagram-games` worktree: `git merge --no-ff feat/fluechtig`.
  Run `git merge-tree` again first; the branches may have moved since.
- Version files take 0.173.0. The changelog keeps both entries, newest first.
  `Cargo.lock` is not merged by hand: take either side, then `cargo check`.
- A second, docs-only commit:
  - `docs/adr/0003-a-game-may-bring-its-own-phone-screen.md` (text below).
  - `src/protocol.rs:71-74`: the comment says phones "know nothing about the
    game being played". Replace it with the rule: shared screens first, a game
    may bring its own.
  - `CONTEXT.md`: the **Ask** entry, already in the working tree.

## ADR 0003 (text)

> # A game may bring its own phone screen
>
> Phones draw a few shared screens (wait, choice, sheet, cards, outcome,
> final), and a new game uses those first. When a game's phone layout does not
> fit them, the game brings its own screen, as Flüchtig does with its numbered
> tiles, tickets and travel log. We decided against forcing every game into the
> shared screens: that is how `tricks`, `hotels` and `bid` came to sit on
> screens every game shares (decided 2026-11-10).
>
> ## Consequences
>
> - A review should not fold Flüchtig's screen back into the shared ones.
> - A third game-specific field on a shared screen is the sign to give that
>   game its own screen instead.

Phase 4 adds one sentence: what every screen shares is the ask, which the room
stamps and checks.

## Acceptance

- `cargo test -p mediagram-game`, `bun run typecheck`, `bun test`, `bun run build` green.
- All four version files say 0.173.0; the lobby's catalogue test lists Flüchtig.
- `git status` shows the same untracked files as before the merge.
