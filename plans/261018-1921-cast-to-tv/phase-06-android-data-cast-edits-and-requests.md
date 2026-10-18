# Phase 06 — Android data: cast edits and requests

**Spec:** §1 liveness, §2, §3 tracks, §10. **Status:** pending.
**Context** (`android/core/data/src/main/kotlin/`):
- `TogetherLive.kt` (the interface, :42);
- `DefaultTogetherLive.kt` (`start` :101, `join` :120, `act` :137, the listen condition in `init` :81);
- `TogetherSessions.kt` (`take` :64, `keep` :77, `tell` :103, `shown` :35);
- `TogetherReads.kt` (`discover` :44, `listen` :83);
- `TogetherSender.kt`.

Tests: `android/core/data/src/test/kotlin/TogetherFixture.kt` (`TogetherHarness` :41, `push()`, `holdEdit`, `answerEdit`), over `FakeCore`'s together section (`core/testing/.../FakeCore.kt:462-551`). `core/testing/.../FakeTogetherLive.kt` throws on moves and gains the new members.

**Why not collect `channel.heard()` while waiting:** `togetherHeard()` pops a single slot, so a second collector would steal pushes from `TogetherReads.listen`. The offer waits on what the sessions keep instead.

### Task 6.1: Requests beside the shown session

**Files:**
- Modify: `TogetherSessions.kt` (`tell` routes requests), `TogetherReads.kt` (`discover` skips requests for the newest, and tracks the newest request), `TogetherLive.kt`, `DefaultTogetherLive.kt` (`requests`, listen condition), `FakeTogetherLive.kt`
- Test: `android/core/data/src/test/kotlin/TogetherCastRequestsTest.kt`

**Interfaces — Produces:**
- `TogetherLive.requests: StateFlow<LiveSession?>`: the newest live request (`isRequest`), or `null`.
- `TogetherSessions.kept: SharedFlow<LiveSession>`: every session `keep` stores.
- Listening runs while `shown` **or** `requests` has a collector.

- [ ] **Step 1: Write the failing tests** (`runTest`, `TogetherHarness`).
  - `requestsAreNeverShown`: a plain session 10 is pushed, then offer 11 → `live.value?.id == 10` and `requests.value?.id == 11`.
  - `aRequestGoesAfterAMinute`: offer 11 is pushed, the clock moves 60 s, and a recheck runs → `requests.value == null`, and the message is deleted.
  - `collectingRequestsAloneListens`: only `requests` is collected; a pushed offer arrives.
  - `discoveryFindsAnOfferBesideASession`: `recent()` holds plain 10 and offer 11 → after `refresh()`, `live` is 10 and `requests` is 11.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests '*TogetherCastRequests*'` → FAIL.
- [ ] **Step 3: Implement.** In `tell`, a request updates `requests` (newest id wins, cleared when that id is over) and leaves `shown` untouched. Update the KDoc on `TogetherLive.live` to say requests are never shown.
- [ ] **Step 4: Run** → PASS, plus all `Together*` tests in `:core:data`. **Step 5: Commit** `feat(android): cast requests are heard beside, never instead of, the shown session`.

### Task 6.2: Offering, answering and the tracks

**Files:**
- Create: `android/core/data/src/main/kotlin/TogetherCasting.kt` (an internal class holding the new moves, so `DefaultTogetherLive` only delegates)
- Modify: `TogetherLive.kt`, `DefaultTogetherLive.kt`, `FakeTogetherLive.kt`
- Test: `android/core/data/src/test/kotlin/TogetherCastingTest.kt`

**Interfaces — Consumes:** `offered`, `answerOf`, `busied`, `refusedBy`, `replaced`, `accepted`, `tracked`, `chose`, `CAST_ANSWER_MS` and `isRequest` (phase 03).
**Interfaces — Produces** (on `TogetherLive`):
```kotlin
data class CastAnswer(val answer: Answer, val live: LiveSession?, val note: String) { enum class Answer { ON, BUSY, REFUSED, NONE } }
data class ArmedCast(val id: Int, val set: String, val profile: String, val from: String, val playing: Boolean)
suspend fun offer(me: Member, own: Playback, profile: String): CastAnswer
suspend fun replace(id: Int, me: Member, own: Playback, profile: String): CastAnswer
fun cancel(id: Int)
fun refuse(id: Int, by: String, note: String)
fun busy(id: Int, by: String, note: String)
suspend fun accept(id: Int, me: Member): LiveSession?
fun tracks(id: Int, screen: String, tracks: CastTracks, audio: String, text: String)
fun choose(id: Int, screen: String, audio: String? = null, text: String? = null)
fun castOnOpen(cast: ArmedCast)
fun takeCast(): ArmedCast?
```

**Behaviour:**
- **`offer`:**
  - sends `offered(...)` with `me.copy(remote = true)`, under `NonCancellable` as `start` does;
  - waits on `sessions.kept` for that id, for up to `CAST_ANSWER_MS`, while collecting `shown`, so that listening runs;
  - the first kept session whose state is neither `OFFER` nor `REPLACE` gives the answer;
  - at the deadline it reads `channel.read(id)` once: still a request → `delete`, `forget`, `NONE`;
  - a thrown send or read is rethrown, and the caller shows `TOGETHER_UNREACHABLE`.
- **`replace`:** reads the session by id. When it is no longer a live request, it makes a new `offer`. Otherwise it queues `replaced` and waits as `offer` does.
- **`accept`:**
  - reads by id;
  - queues `accepted(read, me.copy(remote = false), now())`;
  - returns `null` when `accepted` is `null` (another TV holds it) or the message is gone.
- **`refuse`, `busy`, `tracks`, `choose`:** each queues its move on `work`, fire-and-forget as `leave` is.
- **`castOnOpen` / `takeCast`:** an `AtomicReference`, as `joinOnOpen` is.

- [ ] **Step 1: Write the failing tests.**
  - `anAcceptAnswersTheOffer`: `offer` runs in `async`; a pushed `accepted` edit → `ON`, with `live.session.cast!!.tv == "tv-1"`.
  - `aBusyTvAnswersWithItsTitle` → `BUSY`, `note == "Dune (2021)"`.
  - `offerReadsTheMessageBeforeGivingUp`: nothing is pushed, and `FakeCore` holds an accepted text at id 900 → `ON`.
  - `noAnswerDeletesTheOffer`: `NONE`; `togetherDeletes` contains 900.
  - `replaceAfterStaleOffersAnew`: the clock moves 61 s → a second `send` with state `offer`.
  - `acceptLosesToAnotherTv`: the message already has `tv = "tv-2"` → `null`, and no edit is sent.
  - `tracksStayUnderTheMessageLimit`: `tracks` with 40 long text tracks → the edited text is under 4096 characters.
  - `castOnOpenIsTakenOnce`.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests '*TogetherCasting*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `:core:data` and `:core:testing` compile.
- [ ] **Step 5: Commit** `feat(android): offer a cast, answer it, and keep the TV's track list`.

**Success criteria:** `./gradlew :core:data:testDebugUnitTest detekt` passes, and every existing `Together*` test still passes unchanged.
