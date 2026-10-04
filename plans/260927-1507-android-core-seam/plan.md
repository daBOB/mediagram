# The Android core seam is the generated interface (architecture review candidate H)

Branch `refactor/core-seam` (worktree `../mediagram-channel-index`), from main 67b73b22 (0.68.12).

## Decisions (user-confirmed 2026-09-27, "accept your answers")

| # | Decision |
|---|----------|
| Q1 | Delete `CoreClient` and `DefaultCoreClient`; callers depend on the generated `uniffi.mediagram_core.CoreInterface`. The close fence (retire local state, then close the handle, once; a failed retirement leaves it open for a retry) moves into `CoreProvider`, which already owns every production close. The 7 unsigned call sites use the generated types directly |
| Q2 | One fake of `CoreInterface` in a new `:core:testing` module that tests depend on (not testFixtures) |
| Q3 | The fake keeps the real contract where tests touch it (`NotFound` past the end and for unknown sets, `revokeSession("0")` refused, `CoreException` errors so `coreSentence()` works); one shared contract suite runs against the fake in unit tests and against the real `Core` in instrumented tests on the tablet |
| Q4 | Replaces all six fakes and the five `mockk<CoreClient>` doubles; per-test behaviour (e.g. `StateCoreClient`) becomes small overrides of the shared fake. Watch-state rules stay for J |
| Q5 | 0.68.13 (patch, no behaviour change); all Android unit tests + contract suite on tablet `caad49da`; tablet smoke (launch, play, settings; test profile); `docs/system-architecture.md` updated; merge to main when done |

## Facts (main 67b73b22)
- `CoreClient` 49 methods: 40 identical to `CoreInterface`, 7 signed-for-unsigned, `createProfile(kids = false)` default, `close()` in place of `retireLocalState()`; 34 default bodies act as a hidden wrong fake.
- `CoreInterface` (`mediagram_core.kt:1688`) is a plain interface; the native lib loads only in `UniffiLib` init. `Core(NoHandle)` already runs in a JVM test (`DefaultCoreClientCloseTest.kt:12`).
- `CoreInterface` has no `close()`/`uniffiIsDestroyed`: the provider tracks closing itself.
- Fakes: core/data 271, core/playback 80, feature/setup 136, setup/login 123, feature/system 115, catalog `CatalogCoreFixture` 83 lines; ~25 `by FakeCore()` overrides; 5 `mockk<CoreClient>`.

Status reconciled 2026-10-04: completed — shipped 0.68.13 (`f98dec21`).

## Phases
| Phase | Status |
|-------|--------|
| 01 `:core:testing`: `FakeCore : CoreInterface` + contract suite (fake in unit tests) | done |
| 02 `CoreProvider` owns the fence; delete `CoreClient`/`DefaultCoreClient`; callers on `CoreInterface` | done |
| 03 Every module's tests on the shared fake; old fakes + mockk doubles deleted | done |
| 04 Contract suite vs real `Core` on the tablet; smoke; docs; changelog; 0.68.13 | done |

## Review (2026-09-27)
- Code review: production unchanged on every reachable path (fence under the
  provider mutex, unsigned conversions bit-identical, Hilt graph the same). Applied:
  a dropped `coVerify(exactly = 2)` restored; the close retry no longer repeats a
  retirement that already succeeded (a real `Core` refuses every call once destroyed),
  plus the `uniffiIsDestroyed` guard; a Rust test and a `FakeCore` test for a read
  at a set's end (the contract suite can only read unknown sets offline); the default
  `FakeCore()` now meets the contract; sign-out counted exactly; profile ids unique,
  blank names refused as in Rust; two stale comments.
- Gates: Android unit tests 1481 → 1490, lint, all androidTest compiles green;
  `cargo test -p mediagram-core` green (+1).
- Tablet `caad49da`, 0.68.13, test profile: launch and library load, resume and play
  (1:09 → 1:24), Settings (Telegram account, DC, "Signed in; Telegram answered",
  sessions; cache server connected). `RealCoreContractTest` on the tablet: 8/8 pass
  against the real `Core` (plus `CoreLoadsTest`) — the same suite the fake passes.
  MIUI cancels a new package's USB install unless it is confirmed on the tablet.
