# Web checkpoint commits

Status: DONE — five focused commits created; no push attempted.

Branch: `desloppify/quality-20260923`. Starting HEAD `67ed2bceef144f92455edfb126d188985132fce9`; final HEAD `ca7b5b9cd72f6335ebb6a8d0360c1c38d1cd0ee7`. No production, test or version bytes were changed during this git task.

## Focused groups

| Commit | Subject | Files |
| --- | --- | --- |
| `55533861e9d0ca5b0ecdf61146f422b52a1f4ddd` | fix(web-state): preserve metadata errors without replaying migrations | 2 |
| `5ae3fec83a8eb7e3542a1846033d4ab6cbbc2b92` | fix(web-events): preserve delivery after unprintable callback failures | 2 |
| `283df8995a91af666270c273cf313ac3a94e421a` | fix(web-cache): drain speculative reads before disconnecting upstream | 5 |
| `5579d2420554695bf8062d1dba0c65e63720cf09` | fix(web-playback): report manual play failures with safe retry | 3 |
| `ca7b5b9cd72f6335ebb6a8d0360c1c38d1cd0ee7` | refactor(web-catalog): align view contracts with catalog records | 13 |

The metadata, event, cache and manual playback commits keep their owning regressions with the change. The catalog commit includes the declaration, view JSDoc, complete shared fixture and all typed consumers together; its 13 paths form one contract update. The reviewed `flattenCollection` declaration correction is included.

## Exact path and hash verification

- Scope equals exactly the **25 keys** of `web/.desloppify/seventh-tested-source-sha256.json`.
- Manifest SHA-256: `c49efe61a57ff2d9bb5f1ffe5ade1d7f70028417124ffad8a86199306f5ad130`; unchanged before and after committing.
- Every working file matched before staging. Each index blob and resulting committed blob matched the saved expected SHA-256. All 25 final HEAD blobs and working files still match.
- The aggregate diff between starting and final HEAD contains exactly these 25 paths; the index is empty.
- Secret scans found no candidates in added lines. Every staged diff passed whitespace validation.
- Concurrent Android/Rust changes, plans, tool state, `skills-lock.json`, `web/.claude` and scanner state were excluded and preserved. The report is the only workspace file written outside git metadata.

| Commit | Authorized path | Expected / committed / working SHA-256 |
| --- | --- | --- |
| `55533861` | `web/src/state/store.ts` | `822db59d05b39a5327892b3e0ac913992f324b8913329f80c751b58985af1b3d` |
| `55533861` | `web/test/state-metadata-failures.test.ts` | `ef2c60f2a459e5a14068f6f0d99e40e4afeb8ba5821996f5592c967f04eea749` |
| `5ae3fec8` | `web/src/telegram/channel-events.ts` | `4d4387eb24a3a899070ce79a8c64780194fa550ad81768131ba2b4f7a309f79f` |
| `5ae3fec8` | `web/test/channel-events.test.ts` | `89fbfe166d97caa727522998a98a009c4ec1037e1e285140201f96e480cdc405` |
| `283df899` | `web/src/cache/store.ts` | `444838a2093ac135abafe80441b4243a56232e912e145394747575267bfd2bcf` |
| `283df899` | `web/src/cache/reader.ts` | `857f5bb97c2cb2f2f303155dbb337652171dc6b2c1775e351c986448bb21e38e` |
| `283df899` | `web/src/application/lifecycle.ts` | `889e60521f567092a1e60019a5a997d60464887250071446494eef89f6b4887b` |
| `283df899` | `web/src/index.ts` | `ddb0be71c2ed8c0f119a4eee034687f6e283be9adc4eb21e128db925679eb0e7` |
| `283df899` | `web/test/cache-shutdown.test.ts` | `1c263cdd9eff74735da6c23e41b4afecf7223a07935ed57de8e5694c74f30aff` |
| `5579d242` | `web/public/lib/playback/player.js` | `65156263981130db7835ea4759832a3dc4fb94483a99f78895f711a7ec624a2f` |
| `5579d242` | `web/public/lib/playback/transport.js` | `aef2fc23302978fec4e4f15631a7f59b2c915e5f03f287b16502c2ee8b93663b` |
| `5579d242` | `web/test/player-manual-play.test.ts` | `6114e96a1509f3074497ef02a2f980a600d14342f334c19cddae78badc69ba1b` |
| `ca7b5b9c` | `web/public/lib/catalog/home-view.js` | `0e06bc802a402aed57bfd39ca2d581b538ee2249cc4c89f3a7bd2fc21ca4b9f4` |
| `ca7b5b9c` | `web/public/lib/catalog/plate.js` | `2bff5f9d55d2b53f2e39b10612d8f2c80788cc875bad2cca853ea8db6e6f9d02` |
| `ca7b5b9c` | `web/public/lib/catalog/series-header.js` | `68529138b37bd68f0d650d1903ebf0ec722cc82afaefd4d8b6a26818b5bf4f5f` |
| `ca7b5b9c` | `web/public/lib/catalog/series-summary.js` | `7f5cfcb601415ac85a738dc39e581b9387734fd771f075920d486a5891be4d43` |
| `ca7b5b9c` | `web/public/lib/catalog/shelf-view.js` | `4b1b4cb2193761edf92a711e4ff0bfef5b081bf3f5b5f38f56e8b72331695a48` |
| `ca7b5b9c` | `web/public/lib/library.d.ts` | `276a7168219be4a3b250d438642177cb07096676065b5bb51a258b33f2254233` |
| `ca7b5b9c` | `web/test/browser-html-player.test.ts` | `85f40bad9aec90919ac1a28935f7e3a6299edd4d4ff6567293ff5e440e20073e` |
| `ca7b5b9c` | `web/test/home-shelves.test.ts` | `dc74e398f7d395679bca41d28778183ecc54d7ca55382878b2db73d2a32c970e` |
| `ca7b5b9c` | `web/test/library-documents.test.ts` | `797eae00c5863580249447f8c996d62a368518e2c11cc52ff68ece4a62b51476` |
| `ca7b5b9c` | `web/test/library.test.ts` | `f2215a82c7843ee7d4dcdc8d90ff7e49a8b7a91ced82f711e06647805a109b46` |
| `ca7b5b9c` | `web/test/series-summary.test.ts` | `3377a38cf37fac432c2fa6ac3dbf92f88205f0f3850ed4f694424ab22871d043` |
| `ca7b5b9c` | `web/test/shared-watch-state-fixtures.test.ts` | `247415b4ae7e4394c87595b38e1b95bcb442b5a6e21dcd6f472d5e4579725f74` |
| `ca7b5b9c` | `web/test/support/catalog-set.ts` | `ab2a7ef66d6e64b6857ad9aaac41b6672ad3a3330317e989572d86d148b8346d` |

## Validation reused

- Full web gate: **1,707 passed, zero failed, 12,082 assertions across 127 files**, `/tmp/web-seventh-full-tests.log`.
- TypeScript and browser lint passed, `/tmp/web-seventh-full-{types,lint}.log`.
- Root also reports the post-review browser gate of **77 tests**, TypeScript and lint passing after the declaration correction; the manifest contains that corrected source.
- Read `web-seventh-root-fixes.md`, `web-seventh-root-independent-review.md`, `browser-fifth-fixes.md`, `browser-fifth-spec-scout.md` and `browser-fifth-independent-review.md`. Both final independent reviews report PASS without an unresolved scoped defect.
- No additional tests/builds were run. No background process was started or stopped. Intermediate commits were not separately rebuilt.

## Handoff

No push or hook invocation was attempted, as instructed. No hook bypass, force push, merge, PR, scanner operation, finding resolution or scanner commit-tracking update was performed. The controller retains publication timing while Android gates and blind reviews continue.

Concerns/Blockers: none for this commit checkpoint. Unresolved questions: none.
