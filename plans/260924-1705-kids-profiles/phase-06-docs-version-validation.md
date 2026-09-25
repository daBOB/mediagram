# Phase 06 — Docs, version, and validation on real surfaces

**Context:** spec § Docs and release, § Testing (stub + device). Depends on Phases 01–05.

---

### Task 10: Architecture doc, changelog, version 0.43.0

**Files:**
- Modify: `docs/system-architecture.md` — new section before the watch-state prose (`:632` area); the sync-record paragraph gains the `kids` key
- Modify: `docs/project-changelog.md` — `## Unreleased — 0.43.0` (rename the current Unreleased heading) with an **Added** entry
- Modify: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`), `Cargo.lock` (refreshed)

- [ ] **Step 1: Architecture section** — add under the watch-state part:

```markdown
### Kids profiles

A profile made with "Kids profile" ticked sees only titles rated FSK 12 or
under, plus unrated titles someone marked for Kids by hand; everything else —
FSK 16 and 18, unrated titles, and so every course unless marked — is hidden.
The rule is `forKidsProfile` in `web/public/lib/age-rating.js`, ported to
`android/core/model/.../AgeRating.kt`, and each surface applies it once, where
it takes in its catalog (`applyCatalog` in `app.js`, `CatalogViewModel` on the
phone), so every shelf, search, reel and title page inherits it.

It is a filter, not a lock: anyone can choose another profile, the server
does not know which profile is asking, and a direct stream URL still plays.
The chunk cache is device-wide and shared by every profile.

The flag is `profiles.kids` (web state v7, core state v3) and travels as an
optional `"kids": true` on the profile in the sync record — written only when
true, `format` still 1. Merging is "any device says yes": no device's
document can switch it off, so the flag is set at creation and never changed.
A profile made by mistake is removed and made again, from the web (the phone
cannot remove profiles). A device that has not been updated reads the key as
absent and shows that profile everything until it is.
```

In the sync-record paragraph, after the sentence listing a profile's fields, add: "A kids profile also carries `kids: true`; see Kids profiles."

- [ ] **Step 2: Changelog** — rename `## Unreleased — 0.42.0` to `## Unreleased — 0.43.0` and add under **Added**:

```markdown
- Kids profiles. Tick "Kids profile" when creating a profile, on the web or
  the phone, and that profile sees only titles rated FSK 12 or under plus
  unrated titles marked for Kids by hand — on every shelf, in search, in
  Featured and in Play next. The flag syncs between devices and cannot be
  switched off by a sync. It is a filter, not a lock. The web header's
  profile name now opens "Who's watching?" to switch profile without a reload.
```

- [ ] **Step 3: Version** — confirm the current number first (`git show HEAD:Cargo.toml | grep -m1 ^version`), then:

```bash
V=0.43.0
sed -i -E "0,/^version = \"[0-9]+\.[0-9]+\.[0-9]+\"/s//version = \"$V\"/" Cargo.toml
sed -i -E "s/(\"version\": \")[0-9]+\.[0-9]+\.[0-9]+/\1$V/" web/package.json
sed -i -E "s/(versionName = \")[0-9]+\.[0-9]+\.[0-9]+/\1$V/" android/app/build.gradle.kts
cargo metadata --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep versionName android/app/build.gradle.kts
```

Expected: all three read `0.43.0`; `Cargo.lock`'s `mediagram` and `mlib-spec` entries read `0.43.0`.

- [ ] **Step 4: Full check** — `scripts/check.sh` (clippy `-D warnings`, `cargo test --all`, web lint + tests, Gradle tests + lint). Expected: all green.

- [ ] **Step 5: Commit** `git add docs Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts && git commit -m "docs: describe kids profiles and release 0.43.0"`

---

### Task 11: Validate on the stub harness and on the phone

No code; evidence goes in a report `plans/260924-1705-kids-profiles/reports/validation-kids-profiles.md`.

- [ ] **Step 1: Web, stub harness** (never the real player — it holds the Telegram auth key): serve `web/public` with a scratchpad Bun server stubbing `/api/sets` (films rated 0, 6, 12, 16, 18, one unrated, one unrated course), `/api/profiles` (one kids, one adult), `/api/profiles/*/state`, `/api/kids` (`{kids:[<unrated id>]}`), `/api/search`, `/api/posters/*`. With the `/browse` tool, screenshot: the picker with the create form and Kids checkbox; the Kids label on a tile; Movies on the kids profile (only 0/6/12 + marked); switching to the adult via the header name (full library, no second `/api/sets` in the network log); the player on a kids profile without the Kids button; phone width.

- [ ] **Step 2: Real device** (adb, the phone already signed in — never "start over" on it): install the debug app, open the picker, create "Test Kids" with the switch on; confirm the Kids label and a filtered catalog. Then on the web (real player, **only if no upload is running** — ask the user first), create a kids profile, trigger a sync, and confirm it appears on the phone labelled Kids with a filtered catalog after the phone's next sync. If the real player cannot be used, record that the cross-device leg was covered only by the shared fixtures and `state-two-machines.test.ts`.

- [ ] **Step 3: Write the report** with screenshots paths, what was verified, and anything not verified. Commit it: `git add plans/260924-1705-kids-profiles/reports && git commit -m "docs(plans): record kids profile validation"`

## Success criteria

Docs describe the rule, merge, limits and older-device behaviour; all three manifests at 0.43.0; `scripts/check.sh` green; stub screenshots and a device check recorded.
