# Phase 6 — Profiles

Not in the original plan. Asked for mid-build, reversing the "one viewer"
answer given half an hour earlier.

## What it cost

What the plan predicted: "a column and a default, not a rewrite." Close
enough. `profile_id` joined the primary key of `progress`, `watchlist` and
`collections`, which SQLite cannot do with `ALTER`, so v2 rebuilds those three
tables and adopts anything already recorded into a profile called "Everyone".

## The profile travels in the path

`/api/profiles/<id>/progress/<setId>`, not `?profile=`. Two reasons, and the
first is decisive: `navigator.sendBeacon` is how a position survives the tab
closing and it cannot set a header, so the profile has to be in the URL. Given
that, a path segment beats a query parameter because a missing one is a route
that does not match — and the alternative to a 404 is a write that lands
quietly in somebody else's rows.

## What a profile is not

A login. The player has no authentication and this adds none: anyone who can
reach the port can pick any profile. The chooser says so in as many words,
because somebody will otherwise assume otherwise.

## The bug this found

`open()` replayed **every** migration on **every** open. v1 survived that
because each statement was `CREATE TABLE IF NOT EXISTS`; v2's table rebuild
does not. The second open copied the live rows into a fresh table under an
invented profile, dropped the original and renamed the copy over it — and read
as working. Only groups above the recorded version run now, each in a
transaction, which is what the grouping was always for.

Covered by `state-migration.test.ts`, including opening three times.

## Success criteria

- One profile's shelves are not another's, over HTTP and in the store.
- A list cannot be reached by knowing its id from another profile.
- Deleting a profile takes everything of theirs.
- A v1 database with rows migrates and adopts them; an empty one invents
  nobody and the page asks.
- Opening any database twice changes nothing.
