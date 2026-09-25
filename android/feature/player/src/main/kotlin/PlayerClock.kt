package player

/**
 * A position as a viewer reads it: `1:58:02` for a film, `58:02` for an
 * episode, `0:09` for the first few seconds.
 *
 * A thin unit conversion over the one shared clock format both surfaces read
 * ([model.clockTime]): media3 reports position and duration in
 * milliseconds, the catalog in seconds, and this is where that difference
 * gets absorbed so nothing downstream has to know about it. A length media3
 * does not know yet comes back as `C.TIME_UNSET`, a large negative, which
 * survives the division and reads as zero the same way it does for the
 * catalog.
 */
fun clockTime(ms: Long): String = model.clockTime(ms / 1_000.0)
