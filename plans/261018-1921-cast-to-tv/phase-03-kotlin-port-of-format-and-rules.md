# Phase 03 — Kotlin port of the format and rules

**Spec:** §1, §9 ("held to the same fixtures"). **Status:** pending.
**Pattern:** `android/core/model/src/main/kotlin/together/` and `android/core/model/src/test/kotlin/together/TogetherFixtureTest.kt` (`cases()`, `locateFixture()`, `toSession()` :160, `toMember()` :158).

### Task 3.1: Session v=2, liveness and leaving

**Files:**
- Modify: `android/core/model/src/main/kotlin/together/TogetherSession.kt`, `TogetherFollow.kt` (`isLive`), `TogetherMoves.kt` (`left`)
- Test: `android/core/model/src/test/kotlin/together/TogetherFixtureTest.kt`

**Interfaces — Produces** (package `model.together`):
```kotlin
const val TOGETHER_MARKER = "#mlib-together v=2"
const val TOGETHER_MARKER_V1 = "#mlib-together v=1"
const val REQUEST_LIFETIME_MS = 60_000L
data class Member(val screen: String, val label: String, val remote: Boolean = false)
data class Track(val id: String, val label: String)
data class CastTracks(val audio: List<Track> = emptyList(), val text: List<Track> = emptyList())
enum class CastState(val word: String) { OFFER("offer"), BUSY("busy"), REFUSED("refused"), REPLACE("replace"), ON("on") }
data class Cast(val to: String = "TV", val from: String, val profile: String, val state: CastState,
                val tv: String = "", val note: String = "", val tracks: CastTracks = CastTracks(),
                val audio: String = "", val text: String = "")
// Session gains, last: val cast: Cast? = null
fun isRequest(s: Session): Boolean
```

- [ ] **Step 1: Update the tests.**
  - `toMember` reads `remote` with a default of `false`; `toSession` reads `cast`, with a `toCast()` helper.
  - `writeSessionIsByteIdenticalToJsonStringify` now expects `"#mlib-together v=2\n{...,\"members\":[{...,\"remote\":false}],\"ended\":false,\"cast\":null}"`.
  - Add `writeSessionWithACastIsByteIdenticalToTheWeb`. It writes the `accepted` result of `cast.json`'s first `moves` case and compares it with the string `writeSession` produced in `together-cast.test.ts`. Paste that string into the test as a literal, generated once with `bun -e`.
- [ ] **Step 2: Run** `cd android && ./gradlew :core:model:test --tests model.together.TogetherFixtureTest` → FAIL.
- [ ] **Step 3: Implement** a line-for-line port of phase 02's `session.js`, `follow.js` `isLive` and `moves.js` `left`.
  - Number writing stays `number()` (:91).
  - Strings are written with the existing escaper.
  - `cast` keys go in the web's order.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(android): read and write together v=2, port the cast liveness and leave rules`.

### Task 3.2: Cast rules and words

**Files:**
- Create: `android/core/model/src/main/kotlin/together/TogetherCast.kt`, `TogetherCastWords.kt`
- Test: `android/core/model/src/test/kotlin/together/TogetherCastFixtureTest.kt` (reads `cast.json` through `locateFixture`)
- Modify: `android/feature/player/src/main/kotlin/together/TogetherWords.kt`; test `android/feature/player/src/test/kotlin/together/RemoteNoticeFixtureTest.kt`

**Interfaces — Produces:**
```kotlin
const val CAST_ANSWER_MS = 5_000L; const val AUDIO_TRACKS_MAX = 8; const val TEXT_TRACKS_MAX = 24; const val TRACK_LABEL_MAX = 32
data class TvState(val screen: String, val busy: String?, val refusal: String?)
sealed interface TvAnswer { data object Ignore : TvAnswer; data class Refuse(val note: String) : TvAnswer
                            data class Busy(val note: String) : TvAnswer; data object Accept : TvAnswer }
fun offered(id: String, me: Member, own: Playback, profile: String, now: Long): Session
fun answerOf(s: Session, tv: TvState, now: Long): TvAnswer
fun busied(s: Session, tv: String, note: String): Session
fun refusedBy(s: Session, tv: String, note: String): Session
fun replaced(s: Session, me: String, own: Playback, now: Long): Session
fun accepted(s: Session, me: Member, now: Long): Session?
fun tracked(s: Session, tv: String, tracks: CastTracks, audio: String, text: String): Session
fun chose(s: Session, screen: String, audio: String? = null, text: String? = null): Session
enum class Ending { FINISHED, STOPPED }
fun endingOf(s: Session, me: String): Ending?
object CastWords { const val PLAY_ON_TV = "Play on TV"; /* …every key of CAST_WORDS, UPPER_SNAKE… */ }
fun busyQuestion(title: String): String; fun refusedLine(reason: String): String; fun castFrom(from: String): String
fun barLine(title: String): String; fun ageReason(name: String): String; fun timeReason(name: String): String
// feature/player together/TogetherWords.kt:
internal fun sayOnRemote(noticed: Noticed, session: Session): String?
```

- [ ] **Step 1: Write `TogetherCastFixtureTest`** with one test per array, each named `<array>AgreesWithTheWeb`.
  - Check every `CastWords` constant and formatter against `words`.
  - Add `tracksStayUnderTheMessageLimit`, mirroring the web's 4096 test.
  - Write `RemoteNoticeFixtureTest` (feature/player) for `remoteNotices`. Map `change` strings to `Change` by lower-case name; `"sought"` → `SOUGHT`.
- [ ] **Step 2: Run** `./gradlew :core:model:test :feature:player:testDebugUnitTest --tests '*Cast*' --tests '*RemoteNotice*'` → FAIL.
- [ ] **Step 3: Implement** a line-for-line port of `cast.js`, `cast-words.js` and `sayOnRemote`. The KDoc names the web module as the reference and names the fixture.
- [ ] **Step 4: Run** → PASS, then `./gradlew :core:model:test detekt`.
- [ ] **Step 5: Commit** `feat(android): port the cast handshake, track list and words`.

**Success criteria:** the JS and Kotlin suites both pass on the same `format`, `liveness`, `moves` and `cast` fixtures, and the byte-identity tests pass.
