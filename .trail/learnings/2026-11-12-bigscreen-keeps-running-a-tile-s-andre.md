---
title: "Bigscreen keeps running a tile's old command until Plasma's app cache is rebuilt in the session"
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 2, tokens: 24093 }
id: l-7ee50f9c
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Watch out
- Rewriting a .desktop file on funstation did not change what its Bigscreen tile runs. Plasma's app cache (ksycoca6) was not rebuilt, so from 2026-11-11 23:30 until the next morning every tile fix tested fine over SSH while the real tiles ran the original commands. A tile marked NoDisplay also kept showing.
- `kbuildsycoca6` over SSH builds a separate cache for SSH's locale (ksycoca6_en-POSIX_*), which Bigscreen never reads. Run it inside the session with `systemd-run --user --wait kbuildsycoca6`, which builds ksycoca6_en_* for the session's LANG.
- To test a tile the way Bigscreen runs it, check the cache's contents, or launch it through the cache with `kioclient exec <file>.desktop` in the session environment, not by running its Exec line yourself.

In the code:
- scripts/setup-funstation.sh: `systemd-run --user --wait --quiet kbuildsycoca6` right after the tiles are written
