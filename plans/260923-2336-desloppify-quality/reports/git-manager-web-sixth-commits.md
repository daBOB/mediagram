# Web checkpoint commits

Status: DONE — six focused commits created; publication remains on the controller's explicit hold.

Branch: `desloppify/quality-20260923`. Starting HEAD `7b6ee4698259be8b17a7bdd62da4c7012d9aee60`; final HEAD `b05844a37bfa533287ce2e7b8d26be84fd4165bc`. This checkpoint follows the [eight Rust/Android commits](git-manager-rust-android-sixth-commits.md). No source edits or version bumps were made during this git task.

## Commit groups

| Commit | Subject | Files |
| --- | --- | --- |
| `7a27bcc1` | fix(web-profile): require state before acknowledging selection | 7 |
| `8490ffe7` | refactor(web-playback): clarify typed media boundaries | 7 |
| `2708f12e` | fix(web-status): retain prior totals after directory scan failures | 3 |
| `173f1043` | fix(web-package): preserve manifest read failure diagnostics | 2 |
| `d53792f4` | fix(web-state): count newly imported profiles as changed rows | 6 |
| `b05844a3` | fix(web-server): drain stream cancellation before shutdown | 2 |

The profile commit includes the existing player preference fixture's required successful selection acknowledgement. The playback contract/rename commit includes all authored callers, tests and the typed catalog fixture. Each behavior change keeps its regression tests; intermediate commits were not separately rebuilt.

## Exact manifest comparison

- Authorized scope: exactly the **27 leading-`web/` keys** of `web/.desloppify/sixth-tested-source-sha256.json`.
- Manifest SHA-256: `ea4957275664750810cd4a5492b26ec21b7b873e791e4b45f6f0c7aa5ae162e3`; unchanged before and after committing.
- All working files matched before staging. Each staged blob and resulting commit blob matched its expected SHA-256. All 27 final HEAD blobs and working files still match.
- The aggregate diff from starting HEAD to final HEAD contains exactly the authorized 27 paths. The index is empty.
- All **41** Rust/Android checkpoint paths were also rechecked against their manifest at this final HEAD and still match committed and working bytes.
- Secret-pattern scans found no candidates in the added web lines. Every staged diff passed whitespace validation.
- Root docs, `.gitignore`, plans, skills files, dependency manifests and scanner state were excluded. The report itself is an untracked handoff artifact, outside these commits.

| Commit | Authorized path | Expected / committed / working SHA-256 |
| --- | --- | --- |
| `7a27bcc1` | `web/public/app.js` | `c21f1d7e8440ce0090bd97f06fd1c1bcd85a2cc558593b8e4a1514396a31893b` |
| `7a27bcc1` | `web/public/lib/profile-picker.js` | `e722fe8f94d52ca40480de5fb8ac7e93bcfebc485d82223918d992daf961aef5` |
| `7a27bcc1` | `web/public/lib/watch-state.js` | `a99c492cbe05224188cd6ce8ebdadc16331e4019a77233b1d3e65fe14ee3e0fd` |
| `7a27bcc1` | `web/test/browser-application.test.ts` | `45d0b42dc6f99ae6d14baf319a4a07bf1039617028022fa638db5159e278baac` |
| `7a27bcc1` | `web/test/management-controls.test.ts` | `d101bc0679ba1c107bc343be93ea27ecc89ee1276e0f199435037df2f3ec9be6` |
| `7a27bcc1` | `web/test/watch-state-async.test.ts` | `69d66ed7332384b22c90a05c98a1ee7e412bfc94b6a951cb5c97b48da42c4513` |
| `7a27bcc1` | `web/test/player-lifetime.test.ts` | `7bbe5355c44aa5ff4b1b774707ef76354a9d2c50152ecde25ad9a0d31634ff42` |
| `8490ffe7` | `web/public/lib/library.js` | `189c5f1c492d4452c2369a989e9d5172fd74510f52d2ecb89ac642595bdf1eba` |
| `8490ffe7` | `web/public/lib/playback/audio-chooser.js` | `65b752ec15c7f16a3754144ba866ddce95ec19bb116eb55dcd7fb5cc2bade632` |
| `8490ffe7` | `web/public/lib/playback/player-next-title.js` | `38c66b03ef196f9c76e66478ae113827e999ffdf82745d4af07ddbf5e164e63c` |
| `8490ffe7` | `web/public/lib/playback/player.js` | `057c3638a16779a68a030a519fbb639b281272ff3d760b441f578856aa4ee7bd` |
| `8490ffe7` | `web/public/lib/playback/streaming/hls-playback.js` | `c57112678da4c86798fb43d16425d212f7a3cbb7efa1dab25af71e466902504b` |
| `8490ffe7` | `web/test/audio-chooser.test.ts` | `0b82a66f0b29e231d5fef1df8abd0685e98ca1fc6e29ea03c6c73980569c0627` |
| `8490ffe7` | `web/test/browser-html-player.test.ts` | `802a8c13fe8dff15b70f9ccddb2c8a0f543a00fda0385bba92c5699c3eaba02e` |
| `2708f12e` | `web/src/status/dir-bytes.ts` | `5d55b43c008e4017eb0f6d47194d5f5258a75a6e97f47e9f20fda0d71ab5a191` |
| `2708f12e` | `web/test/status-dir-bytes.test.ts` | `36938beb27f82a1014ad0a47fd305ecc91de6f7606114da95af462eb55ff638a` |
| `2708f12e` | `web/test/status-http.test.ts` | `b1025b8bbc06bccb51d1ebabf6c9ade96e5fa8b6dd30b54c47e4d7ef09fccfeb` |
| `173f1043` | `web/src/package/refresh.ts` | `8fd5d044e2ebf8ce32a33e1cae3c59c717b258081ec49926abe6879eb0c0e97b` |
| `173f1043` | `web/test/package-refresh.test.ts` | `f250bf4b8b0c8604a9ab657527f5269c44268e3cecf71f862173d0c3e9bcbfe6` |
| `d53792f4` | `web/src/state/lists-exchange.ts` | `e7dc15a9314f17731a13ceb9facdf9b581f89d18e5034bc11897004d13a61c41` |
| `d53792f4` | `web/src/state/store.ts` | `108763c87c2e2e2949c9a9421f5c3b3c627b66453daa90d22e7e938b86ac36fa` |
| `d53792f4` | `web/src/state/sync.ts` | `13f1be4dd9b29575ec4abbd1f71be58090f3451bd525987e4598c1f685ce6590` |
| `d53792f4` | `web/test/state-store.test.ts` | `4379a209b9212c237387a9edef99edbe54159726e0f60a853b913060f6c39007` |
| `d53792f4` | `web/test/state-sync-coalescing.test.ts` | `79a27f2f1da0da1116949afd07b78a303efdae765dff7e55450240fb772edf7a` |
| `d53792f4` | `web/test/state-sync.test.ts` | `eee5369bab4cfb3e4451789d3bddaa25f5f52febe412c0a5c8ccbbdfc60ecb15` |
| `b05844a3` | `web/src/server.ts` | `1bd2ed6b130341ee60853a83386c3738574e4da7fa433c18c0dbd20bdc8dc5c4` |
| `b05844a3` | `web/test/server-stream-shutdown.test.ts` | `9149d93d5068f69c5ffb708a165c830ad62adf22149461c4167f1fe08448ad99` |

## Validation reused

- `/tmp/web-sixth-full-tests.log`: **1,688 passed, zero failures, 12,012 assertions across 124 files**.
- `/tmp/web-sixth-full-types.log` and `/tmp/web-sixth-full-lint.log`: TypeScript and browser ESLint pass.
- Read `web-sixth-root-fixes.md`, `web-sixth-root-independent-review.md` and `web-http-shutdown-drain.md`. The controller reported the final source independently reviewed and stable; reports preserve earlier regression evidence and coverage limits.
- No standalone test, build, server or watcher was started; the verified source stayed unchanged.

## Publication and handoff

No push was attempted. The controller will authorize ordinary publication after checking the remaining documentation. No pre-push hook was invoked or bypassed, and no force push, merge, PR, scanner operation, finding resolution or scanner commit-tracking update was performed.

Concerns/Blockers: none for the commit checkpoint. Publication remains deliberately pending controller authorization. Unresolved questions: none.
