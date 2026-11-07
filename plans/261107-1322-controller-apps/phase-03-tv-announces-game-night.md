# Phase 03 — TV announces game night

**Priority:** needed for automatic joining. **Status:** todo. Module: `android/feature/games`.

**Files:**
- Create: `android/feature/games/src/main/kotlin/GameNightAnnouncer.kt` (interface + `NsdGameNightAnnouncer`)
- Create: `android/feature/games/src/main/kotlin/GameNightModule.kt` (Hilt `@Binds`)
- Modify: `android/feature/games/src/main/kotlin/GameNightViewModel.kt` (constructor; `start()` after `startGameNight`; `stop()`)
- Test: `android/feature/games/src/test/kotlin/GameNightViewModelTest.kt` (both constructions, lines ~46 and ~98)

**Interfaces:**
- Consumes: `GameNightUrls.joinUrl` from `startGameNight` (`FakeCore.gameNightUrls` default join URL `http://192.168.0.2:8080/`); plan.md § Contracts (DNS-SD).
- Produces: `interface GameNightAnnouncer { fun announce(joinUrl: String); fun withdraw() }`.

- [ ] **Step 1: Write the failing tests** in `GameNightViewModelTest` with a `RecordingAnnouncer` (lists of announced URLs and withdraw count), passed into both constructions:
  - `startAnnouncesTheJoinUrl` — after `start()` reaches `Ready`, announced == `["http://192.168.0.2:8080/"]`.
  - `failedStartAnnouncesNothing` — with `gameNightStartFailure` set, announced is empty.
  - `stopWithdrawsTheAnnouncement` — `start()`, then `stop()` → withdraw count 1, before the core's stop completes is not required.
- [ ] **Step 2: Run** `cd android && ./gradlew :feature:games:testDebugUnitTest` → FAIL (constructor parameter missing).
- [ ] **Step 3: Implement.** ViewModel takes `announcer: GameNightAnnouncer`, calls `announce(urls.joinUrl)` once `startGameNight` returns, and `withdraw()` at the start of `stop()`. `NsdGameNightAnnouncer @Inject constructor(@ApplicationContext context: Context)`: `NsdManager.registerService` with `serviceName = "Spieltisch"`, `serviceType = "_mediagram-game._tcp"`, `port = URI(joinUrl).port`, `setAttribute("url", joinUrl)`, `PROTOCOL_DNS_SD`; `withdraw()` unregisters only a registered listener and never throws (a second call is a no-op). Bind it in `GameNightModule` (`SingletonComponent`).
- [ ] **Step 4: Run** the same command → PASS; `./gradlew :ui-tv:assembleDebug` (or the module that hosts the TV screen) compiles.
- [ ] **Step 5: Emulator check.** On the Google TV emulator (never the real box in this phase): start game night, then from this machine `avahi-browse -rt _mediagram-game._tcp` — note emulator networking may hide mDNS; if nothing shows, record it and leave the real check to phase 06.
- [ ] **Step 6: Bump patch** (by pattern) and commit: `feat(game): the TV announces game night on the local network`.

**Success:** unit tests pass; the announcement carries `url=<joinUrl>`; stopping game night withdraws it.

**Risk:** the box once failed to *discover* the cache server by mDNS. Advertising is a different path, but it is proven only on the real box (phase 06).
