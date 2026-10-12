# Phase 06 — Android: the session logic port (Kotlin)

**Spec:** `../spec.md` §1, §2, §4. **Status:** pending. **Depends on:** phase 02's fixtures. It can run in parallel with phases 03–05.

## Context

- **The rules are not re-decided.** This is a line-for-line port of `web/public/lib/together/{session,moves,follow}.js` into the pure JVM module `android/core/model`, held to `web/test/fixtures/together/*.json`. The precedent is `core/model/src/test/kotlin/ProfileRolesFixtureTest.kt`, which reads the web's file, walks up from `user.dir` (`locateFixture`) and declares the file as a test input in `core/model/build.gradle.kts`.
- **JSON.** `core/model` has `kotlinx-serialization-json` only as a test dependency. This port parses the message at runtime, so move it to `implementation` in `build.gradle.kts`. Only the `JsonElement` API is used; there are no `@Serializable` classes, so the compiler plugin isn't needed.
- **Package** `model.together`, in `core/model/src/main/kotlin/together/`. Each file stays under 200 lines.

## Files

- Modify: `android/core/model/build.gradle.kts`: kotlinx-serialization-json becomes `implementation`, and the seven fixture files become test inputs.
- Create: `android/core/model/src/main/kotlin/together/TogetherSession.kt`
- Create: `android/core/model/src/main/kotlin/together/TogetherMoves.kt`
- Create: `android/core/model/src/main/kotlin/together/TogetherFollow.kt`
- Create: `android/core/model/src/test/kotlin/together/TogetherFixtureTest.kt`

## Interfaces produced (for phase 07)

```kotlin
package model.together
const val TOGETHER_MARKER: String
data class Member(val screen: String, val label: String)
data class Session(val session: String, val set: String, val playing: Boolean, val pos: Double, val rate: Double, val at: Long, val by: String, val members: List<Member>, val ended: Boolean)
data class Playback(val set: String, val playing: Boolean, val pos: Double, val rate: Double)
data class PlaybackChange(val set: String? = null, val playing: Boolean? = null, val pos: Double? = null, val rate: Double? = null)
data class ScreenState(val set: String?, val playing: Boolean, val pos: Double, val rate: Double)
sealed interface Step { data class Open(val set: String, val pos: Double, val playing: Boolean, val rate: Double) : Step; data class Rate(val rate: Double) : Step; data class Seek(val pos: Double) : Step; data object Play : Step; data object Pause : Step }
enum class Change { PLAYED, PAUSED, SOUGHT, RATE, TITLE, JOINED, LEFT }
data class Noticed(val change: Change, val label: String?)
fun parseSession(message: String): Session?
fun writeSession(s: Session): String
fun started(id: String, me: Member, own: Playback, now: Long): Session
fun joined(s: Session, me: Member): Session
fun left(s: Session, screen: String): Session
fun acted(s: Session, change: PlaybackChange, me: String, now: Long): Session
fun finished(s: Session, screen: String): Session
const val DRIFT_TOLERANCE_SECS: Double
fun expectedPosition(s: Session, now: Long): Double
fun isLive(s: Session, durationSecs: Double?, now: Long): Boolean
fun follow(current: Session?, incoming: Session, me: String): Session
fun decide(own: ScreenState, s: Session, now: Long): List<Step>
fun changeOf(prev: Session, next: Session): Noticed?
```

## Tasks

### Task 6.1: The fixture test, failing

- [ ] **Write `TogetherFixtureTest.kt`.** One `@Test` per fixture file, each looping its cases with the case name in the assertion message, as `ProfileRolesFixtureTest` does. Write small JSON → model readers in the test (`JsonObject.toSession()`, `toScreenState()`, `toStep()`), and compare positions with `assertEquals(expected, actual, 1e-6)`. Use a `locateFixture(name)` that walks up to `web/test/fixtures/together/<name>`.
  - `format.json`: check `parseSession(text)` against `expect` (or `null`), and the round trip `parseSession(writeSession(it)) == it`.
  - `moves.json`: dispatch on `move` to `started`, `joined`, `left` or `acted`.
  - `expected.json`, `liveness.json`, `follow.json` (`"current"` or `"incoming"` maps to the identity of the result), `decide.json` and `change.json`.
- [ ] **Declare the inputs** in `build.gradle.kts`: `inputs.dir(rootProject.file("../web/test/fixtures/together"))`.
- [ ] **Run:** `cd android && ./gradlew :core:model:test --tests 'model.together.TogetherFixtureTest'`. Expected: FAIL, because the functions don't exist yet.

### Task 6.2: The port

- [ ] **Write `TogetherSession.kt`:**

```kotlin
package model.together

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The watch-together session message, ported from `web/public/lib/together/session.js`
 * and held to the same fixtures (`web/test/fixtures/together/`). The web decides:
 * a case that passes only after moving this away from it is a bug here.
 */
const val TOGETHER_MARKER = "#mlib-together v=1"

data class Member(val screen: String, val label: String)

data class Session(
    val session: String,
    val set: String,
    val playing: Boolean,
    /** Seconds into [set] at the moment [at]. */
    val pos: Double,
    val rate: Double,
    /** The writer's wall clock, epoch milliseconds. */
    val at: Long,
    val by: String,
    val members: List<Member>,
    val ended: Boolean,
)

private fun JsonObject.named(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

private fun JsonElement.member(): Member? {
    val o = this as? JsonObject ?: return null
    return Member(o.named("screen") ?: return null, o.named("label") ?: return null)
}

fun parseSession(message: String): Session? {
    val newline = message.indexOf('\n')
    if (newline < 0 || message.substring(0, newline) != TOGETHER_MARKER) return null
    val json = runCatching { Json.parseToJsonElement(message.substring(newline + 1)) }.getOrNull() as? JsonObject ?: return null
    val members = (json["members"] as? JsonArray)?.map { it.member() ?: return null } ?: return null
    return Session(
        session = json.named("session") ?: return null,
        set = json.named("set") ?: return null,
        playing = json.flag("playing") ?: return null,
        pos = json.number("pos")?.takeIf { it >= 0 } ?: return null,
        rate = json.number("rate")?.takeIf { it > 0 } ?: return null,
        at = (json["at"] as? JsonPrimitive)?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: return null,
        by = json.named("by") ?: return null,
        members = members,
        ended = json.flag("ended") ?: return null,
    )
}

fun writeSession(s: Session): String {
    val body = JsonObject(
        mapOf(
            "session" to JsonPrimitive(s.session), "set" to JsonPrimitive(s.set),
            "playing" to JsonPrimitive(s.playing), "pos" to JsonPrimitive(s.pos), "rate" to JsonPrimitive(s.rate),
            "at" to JsonPrimitive(s.at), "by" to JsonPrimitive(s.by),
            "members" to JsonArray(s.members.map { JsonObject(mapOf("screen" to JsonPrimitive(it.screen), "label" to JsonPrimitive(it.label))) }),
            "ended" to JsonPrimitive(s.ended),
        ),
    )
    return "$TOGETHER_MARKER\n$body"
}
```

  Drop the unused `add`/`put` imports if detekt flags them. The `JsonObject(mapOf(...))` form needs none of them.
- [ ] **Write `TogetherMoves.kt`:**

```kotlin
package model.together

import kotlin.math.roundToLong

/** What each screen writes; ported from `moves.js`. A change made while following is never one of these. */
data class Playback(val set: String, val playing: Boolean, val pos: Double, val rate: Double)

data class PlaybackChange(val set: String? = null, val playing: Boolean? = null, val pos: Double? = null, val rate: Double? = null)

private fun toMillis(secs: Double): Double = (secs * 1000).roundToLong() / 1000.0

fun started(id: String, me: Member, own: Playback, now: Long): Session =
    Session(id, own.set, own.playing, toMillis(own.pos), own.rate, now, me.screen, listOf(me), ended = false)

fun joined(s: Session, me: Member): Session {
    val present = s.members.any { it.screen == me.screen }
    return s.copy(by = me.screen, members = if (present) s.members else s.members + me)
}

fun left(s: Session, screen: String): Session {
    val members = s.members.filter { it.screen != screen }
    return s.copy(by = screen, members = members, ended = members.isEmpty())
}

fun acted(s: Session, change: PlaybackChange, me: String, now: Long): Session =
    s.copy(
        set = change.set ?: s.set,
        playing = change.playing ?: s.playing,
        pos = toMillis(change.pos ?: expectedPosition(s, now)),
        rate = change.rate ?: s.rate,
        at = now,
        by = me,
    )
```

  Also `fun finished(s: Session, screen: String) = s.copy(playing = false, by = screen, ended = true)`.

  Check the rounding: JS `Math.round` rounds .5 up, and Kotlin's `roundToLong` rounds half up for positives. They match for positions ≥ 0.
- [ ] **Write `TogetherFollow.kt`.** Declare `ScreenState`, `Step`, `Change` and `Noticed` exactly as listed under Interfaces. Port `follow.js` exactly, with the constants (`DRIFT_TOLERANCE_SECS = 0.5`, past-end grace 120 s, unknown length 6 h, paused 3 h, seek notice 2 s), `expectedPosition`, `isLive`, `follow`, `decide` (order: rate, seek, play/pause; `Open` alone when the set differs) and `changeOf` (order: title, joined, left, played/paused, rate, sought). Copy the doc comments from the JS: they explain why liveness has no keep-alive and why an own echo can win.

```kotlin
fun expectedPosition(s: Session, now: Long): Double =
    if (s.playing) maxOf(0.0, s.pos + (now - s.at) / 1000.0 * s.rate) else s.pos

fun follow(current: Session?, incoming: Session, me: String): Session {
    val stillMine = current != null && current.session == incoming.session && current.by == me
    return if (incoming.by == me && stillMine) current!! else incoming
}

fun decide(own: ScreenState, s: Session, now: Long): List<Step> {
    val pos = expectedPosition(s, now)
    if (own.set != s.set) return listOf(Step.Open(s.set, pos, s.playing, s.rate))
    return buildList {
        if (own.rate != s.rate) add(Step.Rate(s.rate))
        if (kotlin.math.abs(own.pos - pos) > DRIFT_TOLERANCE_SECS) add(Step.Seek(pos))
        if (own.playing != s.playing) add(if (s.playing) Step.Play else Step.Pause)
    }
}
```

  Note on `own.rate != s.rate` for a `Float` speed from media3: the controller converts with `.toDouble()` and rounds to 2 decimals before comparing (`1.5f` is exact, but `1.1f` is not). Do the rounding in the controller (phase 07), not here, so the port stays identical to the JS.
- [ ] **Run:** `./gradlew :core:model:test`. Expected: PASS.
- [ ] **Commit:** `feat(android): watch-together session rules, ported from the web`

## Todo

- [ ] 6.1 Fixture test and inputs
- [ ] 6.2 Port

## Success criteria

- Every case in every `together/*.json` passes in Kotlin with no Kotlin-only case added.
- `:core:model:test`, detekt and spotless pass.

## Pre-flight rulings (binding; they override the text above where they differ)

- `moves.json` has a `finished` case: the Kotlin moves test dispatches `started`, `joined`, `left`, `acted` **and `finished`**. Every fixture key the JS test reads, the Kotlin test reads too.
