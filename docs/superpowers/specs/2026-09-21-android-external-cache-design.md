# Android: a cache that can live on another volume — design

The Android app holds two gibibytes of cached bytes and cannot be persuaded
to hold any other number. `CACHE_MAX_BYTES` is a compile-time constant and
`context.cacheDir` is a compile-time choice, so on every device the app has
ever run on, the cache is the same size in the same place.

A film in this library is 5.8 GB. The ceiling is therefore below the size of
one title: a viewer who watches an hour and seeks backwards re-downloads
what they already paid for, and a viewer who finishes a film and starts it
again downloads the whole thing twice. The cache is not small; it is smaller
than the unit of work.

This makes both numbers a viewer's choice, and lets the directory sit on a
volume the app did not install itself on.

## 1. What is actually wrong

| | State before this work |
|---|---|
| Cache size | `CACHE_MAX_BYTES = 2 GiB`, a `private const val` in `CacheProvider.kt`. Not read from anywhere, not overridable. |
| Cache location | `File(context.cacheDir, "mlib")`. The app's own internal cache directory, which the system may also evict from under storage pressure. |
| The web player's equivalent | **Both configurable**, and have been all along: `MEDIAGRAM_CACHE_DIR` and `MEDIAGRAM_CACHE_MAX` (`web/src/config.ts:163`), the latter defaulting to 8 G — four times what Android permits at its maximum. |

That last row is the one that settles the question of whether this is worth
doing. Per this project's surface-parity rule the web player is the
reference, and it decided years of commits ago that where the cache lives
and how large it grows are deployment facts, not source constants. Android
is the surface that has not caught up.

### The devices this is actually for

| Device | Internal storage | Removable `File` path? |
|---|---|---|
| Redmi (the device on adb) | 225 GB, 74 GB free | **None.** `sm list-volumes` shows only `emulated;0`. |
| Android TV box | small | Usually — most expose USB/SD as a public volume |
| Phone/tablet with microSD | varies | Yes; this is the case the API exists for |
| Chromecast w/ Google TV | ~4.4 GB usable | Only via adoptable storage, a system setting |
| Fire TV Stick | ~5 GB usable | Fire OS is a fork; varies by model, Amazon restricts it |

Two things follow. The first is that the test device has no removable volume
at all, so the budget half of this work is the half that can be verified
here and the location half cannot. The second is that on the Redmi the 2 GiB
ceiling is not a storage limit — 74 GB sit free beside it — which means
**raising the budget helps every device, including the ones with nowhere
else to put the cache.** The budget is the load-bearing half.

## 2. The constraint that eliminates most of the design space

> **`SimpleCache` takes a `java.io.File`.** Not a URI, not a stream, not a
> `DocumentFile`. Its constructor opens an index database in a directory it
> can `mkdir` and `listFiles`.

Everything below follows from that sentence.

**The Storage Access Framework is out.** SAF is the only way to reach an
arbitrary location on removable media on modern Android, and it hands back
`content://` URIs. There is no `File` behind them, so `SimpleCache` cannot
be pointed at one. Writing a cache implementation that could would mean
replacing media3's cache entirely — a different and much larger piece of
work than this.

**`MANAGE_EXTERNAL_STORAGE` is out.** It is a Play-policy-restricted
permission requiring justification for an app that is not a file manager,
and it still does not reliably reach a USB OTG volume. It would buy a
policy problem and not solve the problem.

**Adoptable storage needs nothing from us,** and is worth naming precisely
because it is the answer on the two hardest devices. A viewer who formats a
USB stick as internal storage in system Settings gets an extended internal
volume; if the app is installed there, `context.cacheDir` is already on the
stick and today's code benefits with no change. On Chromecast with Google TV
this is the supported route. It is documentation, not code.

What is left is the one thing that is both a `File` and permission-free:

> **`context.getExternalCacheDirs()`** returns an app-private cache
> directory on each shared/external volume. No permission since API 19,
> deleted on uninstall, and a real path.

## 3. The volume list

```kotlin
data class CacheVolume(
    val id: String,        // persisted key: "internal", or the volume's UUID
    val label: String,     // "Internal storage", "SD card", "USB drive"
    val dir: File,         // the mlib directory on that volume
    val freeBytes: Long,
    val removable: Boolean,
)
```

The list is `context.cacheDir` as `"internal"`, plus `getExternalCacheDirs()`
**from index 1 onward**.

Index 0 is skipped deliberately. It is the app's cache directory on the
primary *emulated* volume, which on every device in the table above is the
same physical storage as `context.cacheDir`. Including it would offer a
viewer two rows, two labels and two free-space figures for one disk, and
whichever they picked the bytes would land in the same place. One disk, one
row.

Entries whose directory is `null` are dropped: `getExternalCacheDirs()`
returns a null slot for a volume that is currently ejected, and a volume
that is not there is not a choice.

`Environment.isExternalStorageRemovable(dir)` supplies `removable`;
`StorageManager.getStorageVolume(dir).getDescription(context)` supplies the
label, falling back to a generic one when the platform has nothing to say —
which it sometimes has not, since `getStorageVolume` answers `null` for a
path it cannot place.

Every API here clears this project's `minSdk = 24`, two of them exactly:
`getExternalCacheDirs()` is 19 and `isExternalStorageRemovable(File)` is 21,
but `getStorageVolume(File)` and `StorageVolume.getDescription(Context)` are
both 24. They are at the floor, not above it, so the labelling is the part
of this that a future `minSdk` drop would break first.

The `Context` walk is a thin adapter over a pure function of
`(dirs, stats)`. The adapter is untestable off a device; the function that
decides what the list contains is not, and that is where the index-0 rule,
the null-dropping and the labelling live. This is how `:core:playback`
already splits `MlibDataSource` from its tests.

## 4. The budget

Four presets: **8 GiB, 32 GiB, 128 GiB, and as much as fits.**

"As much as fits" resolves at cache-open time to the volume's free space
minus a **1 GiB floor**. The floor exists because a cache that fills a
volume completely takes the rest of the device down with it — on a Fire
Stick with 5 GB there is no slack to lose.

A preset larger than the chosen volume's free space is shown **disabled,
with the reason underneath its own label**. That is not a new interaction
invented here: `AppChrome.kt:187` already has `MenuItem(label,
disabledReason)` doing exactly this for the library menu, and reusing it
keeps one answer in the app to "why can I not press that".

The default, before a viewer has ever opened the screen, is
`min(8 GiB, freeBytes / 2)`.

Two numbers rather than one because the two failure modes are opposite. A
flat 8 GiB is right on the Redmi — four times today's ceiling, taken from
74 GB of slack — and catastrophic on a Fire Stick with 3 GB free, where it
would mean the cache alone is larger than the disk. A flat fraction is right
on the Fire Stick and pointlessly timid on a 512 GB card. Taking the smaller
of the two is right on both, and needs nothing from the viewer to be right
on a device nobody has tested.

`LeastRecentlyUsedCacheEvictor` takes a fixed byte count at construction, so
the resolved number is computed once when the cache opens and holds for that
run. A budget that tracked free space continuously would need a custom
evictor; nothing here is worth that.

## 5. Persistence

`CacheSettings` in `:core:data/settings/`, in the shape `PackageSettings`
established: an interface, an `InMemoryCacheSettings` for tests, and one
real implementation. It stores a volume id and a budget selection.

Plain `SharedPreferences`, **not** `EncryptedSharedPreferences`. A volume id
and a byte count are not secrets. `EncryptedPreferences` exists in this
module because an auth key and a package decryption key are secrets, not
because encryption is the house default for settings, and using it here
would blur a distinction worth keeping sharp.

## 6. Three behaviours in `CacheProvider`

`get()` reads the settings, resolves the volume and builds there. Beyond
that, three cases that a cache on removable media has and a cache on
internal storage does not:

**The chosen volume is gone.** A card removed between runs. Fall back to
internal storage, **keep the recorded choice**, and report the fallback on
the System screen. Keeping the choice is the point: clearing it would mean a
viewer who takes their card out to copy a file onto it finds the setting
silently reset when they put it back. The recorded choice is what they
asked for; the internal directory is only where the bytes are going
meanwhile.

**The location changed.** The old directory is deleted once the new one has
opened successfully — after, so that a failure to open the new location
cannot also lose the old contents.

Bytes are **not copied across**. Copying 30 GB between volumes on a phone is
minutes of work that can fail halfway, needs progress reporting and
cancellation, and can run the destination out of space — all for data whose
defining property is that it can be fetched again. The screen says so
plainly: titles already held will be fetched again.

**The volume dies mid-playback.** Add `FLAG_IGNORE_CACHE_ON_ERROR` to the
`CacheDataSource.Factory` in `PlayerFactory.kt`. Without it a write failure
on a yanked card propagates and playback stops; with it the read falls
through to upstream and the film keeps running. This is the correct
behaviour for the existing internal-storage cache too — a full disk produces
the same failure — so it is a fix this work happens to be adjacent to rather
than one it creates the need for.

## 7. The compromise: changes apply at next start

`ExoPlayer` is a process-wide Hilt singleton
(`PlaybackModule.provideExoPlayerDeferred`) holding a `CacheDataSource` over
the live `SimpleCache`. `SimpleCache.release()` while a data source is open
over it is not safe, and there is no supported way to swap the cache
underneath a built player.

Doing this properly means tearing down and rebuilding the player graph on a
settings change — a larger change to the app's object lifetime than the
feature that prompted it, for a setting a viewer changes approximately once.

So the choice is persisted and takes effect when the app next starts, and
the screen says that in as many words rather than appearing to have done
something it has not. This is the one thing in this design that is a
limitation rather than a decision, and it is recorded here so that the next
person to read `CacheProvider` finds out why from the spec instead of from
the behaviour.

## 8. The screen

A new `Destination.Storage`, reached from the overflow menu the way
`Destination.TmdbKey` is.

Not added to the System screen, whose own doc comment commits it to being
"a snapshot a viewer opens to check on, not a live dashboard". Pickers that
write settings are the opposite of a snapshot, and `TmdbKey` already
established that a screen which changes something is its own destination.

Two sections — **Where** (the volumes, each with its free space) and
**How much** (the presets) — plus the two sentences the design owes the
viewer: that the change takes effect when the app restarts, and that held
titles will be fetched again.

`StorageViewModel` and `StorageUiState` in `:feature:system`, which already
owns the System screen and already reads `CacheProvider.occupancy`.
`StorageScreen.kt` and a pure `StorageRows.kt` in `:ui-mobile`, mirroring
the `SystemScreen`/`SystemRows` split that keeps the wording testable off a
device.

The System screen's Cache block gains one row naming the volume in use, and
saying when that is not the volume that was asked for.

## 9. What this phase does not do

**The television devices get nothing from it.** `:ui-tv` contains a
`build.gradle.kts` and no source; `MainActivity` renders the string
"Mediagram — television surface". Fire Stick, Chromecast and the TV box have
no screen on which to open a setting, so they keep internal storage until
that surface exists. Three of the four devices that motivated this work are
in that list, which is uncomfortable and is the reason it is written down
here rather than discovered later.

What they do get, when `:ui-tv` is built, is a working mechanism behind it:
everything in sections 3 through 6 lives in `:core:playback` and
`:core:data` and is surface-independent. The TV surface will need a screen,
not a cache design.

**The LAN cache server is not in scope.** A box on the network holding the
bytes for every device is the other half of what was asked for, and it is a
separate design: the web player already answers HTTP Range requests over a
set's bytes through its own configurable cache, so the shape of that work is
pointing Android's byte path at an `HttpDataSource` rather than building a
server. It is phase 2 and gets its own spec.

## 10. Files, tests, version

**New**

| File | Holds |
|---|---|
| `:core:playback/CacheVolumes.kt` | `CacheVolume`, the pure list builder, the `Context` adapter |
| `:core:playback/CacheBudget.kt` | the presets, resolution against free space, availability |
| `:core:data/settings/CacheSettings.kt` | interface, in-memory, `SharedPreferences` |
| `:feature:system/StorageUiState.kt`, `StorageViewModel.kt` | the facts the screen renders |
| `:ui-mobile/StorageScreen.kt`, `StorageRows.kt` | the screen and its pure wording |

**Modified**

`CacheProvider.kt` (chosen dir and budget, fallback, stale-directory
deletion), `PlayerFactory.kt` (`FLAG_IGNORE_CACHE_ON_ERROR`), `AppChrome.kt`
(the destination and its menu item), `LibraryFlow.kt` (the route),
`SystemUiState.kt` / `SystemViewModel.kt` / `SystemScreen.kt` (the row
naming the volume).

`CacheProvider.kt` is 95 lines and does not have 105 spare. The volume
resolution is the piece that leaves, into `CacheVolumes.kt`, so the file
that remains is what it says it is: the process's one `SimpleCache` and its
occupancy.

**Tests** — the volume list over a single-volume device, a device with a
card, and a device whose card is ejected; budget resolution including the
1 GiB floor, the `min(8 GiB, half)` default and the disabled presets;
fallback to internal when the recorded volume is absent; the row wording,
including the fallback sentence.

**Version** `0.17.0` → `0.18.0`, a minor: this is a backwards-compatible
addition. All three manifests in step — `Cargo.toml`, `web/package.json`,
`android/app/build.gradle.kts` — and `versionCode` 1 → 2 beside the last,
which counts builds and is not part of the semver rule.
