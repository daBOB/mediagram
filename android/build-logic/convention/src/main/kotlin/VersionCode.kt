package convention

/**
 * The versionCode Android compares before installing an update, derived from
 * versionName so a release can never forget to raise it and two machines
 * can never hand out the same one: 0.93.0 → 93000. Every app module uses this one.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: error("versionName $name is not major.minor.patch") }
    require(parts.size == 3) { "versionName $name is not major.minor.patch" }
    val (major, minor, patch) = parts
    require(minor < 1000 && patch < 1000) { "versionName $name: minor and patch must stay below 1000" }
    return major * 1_000_000 + minor * 1_000 + patch
}
