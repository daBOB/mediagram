# Web checkpoint commits

Status: DONE_WITH_CONCERNS — committed; publication pending the stable Android gate.

Created seven focused conventional commits on `desloppify/quality-20260923`, from `2c9e0e99e3b443192be640140cb2e1f104d3e55c` through `e57625a182e188b5c79e4d5810b7cd4b25a2a275`. No source edits or version changes were made during this git task.

## Commits

| Commit | Subject | Files |
| --- | --- | --- |
| `3332e95e` | fix(web-state): preserve outcomes for arbitrary failures | 10 |
| `134c9f0b` | fix(web-stream): coalesce probes and await cancelled reads | 5 |
| `b6a17100` | fix(web-cache): surface inventory errors and recheck idle sessions | 4 |
| `5f9f31fb` | fix(web-login): hide terminal input through the public output stream | 2 |
| `6dd4c33b` | fix(web-browser): recover profile discovery and guard late navigation | 6 |
| `cedfd44c` | fix(web-playback): retain subtitle choice and align media contracts | 5 |
| `e57625a1` | chore(web-lint): check authored browser modules with ESLint | 5 |

## Exact byte verification

- All **292** entries in `web/.desloppify/fifth-tested-source-sha256.json` match both the final committed blobs and current working files. Checked before staging and again after the push attempt.
- Manifest SHA-256 remains `4b04177bb157a8e95df1f0ab3860b8fff7b0f6c85f3e13fc79830f17d8ebc95e`.
- The aggregate commit diff contains exactly the **37** authorized paths below. Every staged blob and resulting commit blob was checked against its saved pre-commit SHA-256; the final working files still match.
- The index is empty. Android, `.gitignore`, other root docs, plans, `skills-lock.json`, `web/.claude` and local scanner state were not staged or edited. Concurrent Android modifications were preserved.
- Broad credential matches were limited to the isolated PTY password fixture, verified as explicitly dummy/test data. No real credential or sensitive value was printed. Strong credential-pattern checks and staged whitespace checks passed for every commit.
- The open GitHub issue list was empty; no issue references were invented.

| Path | SHA-256 |
| --- | --- |
| `web/src/application/catalog-follow.ts` | `fbf6195bba45b0191f5f7fcf341d477df9415ae16fce876984bce21e2ab8f8ae` |
| `web/src/channel-index/fetch-posters-for-index.ts` | `c02b2b7e173453801795fd971090e94694d010354a8ebabe1b6d7cfc1b084a7b` |
| `web/src/channel-index/install-channel-index.ts` | `2ee664c3b741db6ac8b28a2b38e029af8e6b1a3601dee2bbf76948aed5d35fdc` |
| `web/src/state/store.ts` | `af3558812d54b62e90f75ddcb42dfd3ac94f1518002d6493d9562e3bfaedd6bd` |
| `web/src/state/sync.ts` | `1f1bc66a47a6eea52b24dedd54abad046a9c3cb770e43bbb3b20403351c9ebfb` |
| `web/test/application-catalog.test.ts` | `c5ce81246ec8d5f2d302c36c7ee80e4dda5ab37dc36f9868764db16f2fca1dc1` |
| `web/test/fetch-posters-for-index.test.ts` | `058b1e3c3d7c2e294ec2a5699ef96dd79a08fb1899dca63f198121e5e68cf158` |
| `web/test/install-channel-index.test.ts` | `6ee3361d6b125bc724c3f0885d33b09ed5ccc79613024fd810578cb424e0a543` |
| `web/test/state-sync-coalescing.test.ts` | `ae1bc3a593bff3275374c0ce81182e54c9b06eb4b2c61698f27766389e2204bd` |
| `web/test/state-sync.test.ts` | `7e2ee9ed433ca34dd7ad43fd3b86ffd70b918dcab2631e1222c71555a644bbdc` |
| `web/src/catalog/audio-tracks.ts` | `3df343663ddeedc5a15006f419bad0e7810877e9292a553eb01c75d2a4a9772a` |
| `web/src/telegram/source.ts` | `3c2f1acb662242f688f5c510e3c4c0f4d2ba1892a38ceff8334f3ecdb875949b` |
| `web/test/audio-tracks.test.ts` | `163347a36c9a95ad1fadc4c97d02beb365cc25c1b2c8cfa8d15452472a0a5660` |
| `web/test/audio-shutdown.test.ts` | `7f19d34635304446f114244e07a558bbdf8d9cb69850787deef4af058cc3c830` |
| `web/test/telegram-stream-cancel.test.ts` | `37d861bfc1b5ae188dad186083ef69928c76d1c0e6a28ed048c912b9089979b6` |
| `web/src/cache/store.ts` | `b1240b24b0c4ac37f20a9a21bc781570dfc68d855769f15e05bf1000303ea2f7` |
| `web/src/transcode/registry.ts` | `a4ae07da647a73065286b9663bc89b9800727a28d9e240358dd5528a2ff927ff` |
| `web/test/cache-store.test.ts` | `3a1dc8377a3ddc95fb9ff70805006b6587a708ad9003d3cb437523b7b62c6d83` |
| `web/test/transcode-registry.test.ts` | `6106dab14e31e1f20d307e3997976fc8fbf758de854cd7e0174b4fab4fee3521` |
| `web/src/login/prompts.ts` | `6318cc03fe569c7b42fe3029209410e93ced23afeabd19138e4bd2a401ed2477` |
| `web/test/login-prompts.test.ts` | `eff84e13453308dd93ce6e2f50a9b05f1133b6532beae6d97665d488071ba8c3` |
| `web/public/app.js` | `eb5c977ab31d5f30b23f69fa1a8dff3348c796478a960a832842b75bf3db0a49` |
| `web/public/lib/profile-picker.js` | `b65db002c1e7b3eda148bc96cd9d71815e78b2df86e13024e0a45bd83b7773d8` |
| `web/public/lib/watch-state.js` | `5df8f0738f1ff6a17ea4ae5d9fcb73c6b4bb3a337588659e977c1454840d69bd` |
| `web/test/browser-application.test.ts` | `40be80b69b50938422c27b863f92ea992b51164672ec2544db56d4351a6f53e7` |
| `web/test/management-controls.test.ts` | `7b305c4140781aa7041801ced215d25268c430eca1807dd9d2c6043f5b462a44` |
| `web/test/watch-state-async.test.ts` | `d6f5ea7c89160eb2fddafa76fcfa46abf3c7c9e492dfbbfa312ac3569e4f2f84` |
| `web/public/lib/playback/audio-chooser.js` | `515e7b7eb3ddc50c004e1a4aa6796a0f041c95d2426b4b7e0459f7afa582786e` |
| `web/public/lib/playback/streaming/hls-playback.js` | `65272f6a7b7444c1cba45235423ab0786a2fbf132b5defc50acf878a77b51510` |
| `web/public/lib/playback/transport.js` | `2903f27436a30ebea53ef94c5b56ce6ee94dee460cc14ec329c30e7e3d8e2eb8` |
| `web/test/audio-chooser.test.ts` | `548e52dfb652370e1453559d30b2e951942472592c2ab618f0d420ad96afdb0d` |
| `web/test/browser-html-player.test.ts` | `690884f918ad258d25a9bab581a47686b31663445d40a0c4b118f6423405ecc9` |
| `web/eslint.config.js` | `f6e85f4c9e496dfe397949e71859bf033aba7683d6dc6ae5920cf46764b04902` |
| `web/package.json` | `786235dad45325a3189824f35042e3a0a7d8660ece9ffcdb7430461a977872b3` |
| `web/bun.lock` | `42cb13ab545e9f3fded8879ee8cbd57d8714e3b833906b7fdfb65d1c302fb360` |
| `scripts/check.sh` | `b1acdda2d4a893a7af62f1294bd9725104502a5d2593c18cb3961d064205bf9c` |
| `docs/code-standards.md` | `c20cd82b8dbc847c4f33c58a608c86d121f86fc6cb0fe40b356f6f819f5fa7c5` |

## Validation and publication

The supplied whole-web gate passed **1,664 tests, 0 failures, 11,896 assertions across 123 files**, with TypeScript and ESLint clean (`/tmp/web-fifth-full-{tests,types,lint}.log`). Implementation evidence and both independent reviews were read. No standalone tests were rerun.

An ordinary `git push --set-upstream origin HEAD` invoked the configured `.githooks/pre-push` gate (`scripts/check.sh`). Its Rust Clippy/tests and web lint/tests completed before the Android stage failed:

```text
:core:data:compileDebugUnitTestKotlin FAILED
WatchSyncTest.kt:44:9: RecordingRepository does not implement invalidate(): Unit
BUILD FAILED in 12s
git push exit code: 1
```

Android was concurrently under active development. The controller instructed publication to wait for its coordinated fixes and independent review. The push had already exited before termination inspection; no process signals were needed or sent. No owned push, check-script, Cargo-test or Gradle-wrapper process remained. Shared Gradle daemons and unrelated reviewer processes were untouched.

The existing SSH transport emitted a missing `ssh-askpass` warning but successfully reached the hook; this was not the publication blocker. No bypass, force push, merge, PR, scanner resolution or commit-log update was performed.

Remote: `git@github.com:daBOB/mediagram.git` — [repository](https://github.com/daBOB/mediagram). Remote branch absent (GitHub ref API returned HTTP 404 after the failed push).

Next action: after Android stabilizes and passes its coordinated gate, retry the ordinary push with hooks enabled. Seven web commits are ready and their tested bytes remain unchanged.

Concerns/Blockers: publication pending the Android gate; no concern in the committed web bytes. Unresolved questions: none.
