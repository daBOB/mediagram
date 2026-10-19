# Phase 03 — Access per profile (to confirm with the user)

**Status:** blocked until the user confirms what "access management" means. Nothing here is built before that.

**The controller's reading:** per profile, which library sections it may open — Movies, Series, Anime, Documentaries, Tutorials, Collections (`web/public/lib/departments.js`). Like a media server's per-user library access. A hidden section disappears for that profile everywhere: nav, Home shelves, search, Continue and the catalogue on Android, applied at the one place a kids filter is applied today (`web/public/lib/library-session.js` `filtered`; Android `CatalogViewModel`).

**Who sets it:** the admin for every grown-up (itself included); a kid's owner for that kid — the existing household rule, with a new action `set-access`.

**Why it is a project:** the setting must reach every device, so it rides the roles record like a kid's age (`kids_age` + `kids_age_updated_at`): a schema step in `web/src/state/roles-*.ts`, the Rust core's `state/exchange/roles.rs` and `api/state/profile_roles.rs`, regenerated bindings and native core, `profile-rules.json` for both rule ports, then the web and Android filters and the Manage UI on all three surfaces. Rough size: the daily-limit work of 0.125.0.

**Other readings, if this one is wrong:**
- who may open the web player at all (a sign-in in front of the whole player, beyond the household's network rule);
- per-profile PIN required to *enter* a profile, not only to manage;
- what each grown-up may do (e.g. a second admin).
