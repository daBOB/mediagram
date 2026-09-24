package ui

/**
 * A rotation disposes and recreates `PlayerScreen`'s whole composition
 * exactly the way leaving it for the catalog does; the two are told apart
 * by whether the Activity itself is mid configuration change. Stopping on
 * a rotation would restart the same set from zero every time the device
 * turns, which is worse than the drop-to-catalog bug this replaced. Split
 * out to keep `PlayerScreen.kt` under the project's line guideline.
 */
internal fun shouldStopOnDispose(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations
