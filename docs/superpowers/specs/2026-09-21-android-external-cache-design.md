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

**What shipped (`b91e9e8`):** a size list, not presets. The Settings cache
block offers 512 MB, 1 GB, 2 GB, 4 GB and 8 GB, and a choice applies at
once: `AdjustableLruEvictor` takes a new budget while the cache is open and
evicts down to it straight away. The presets, the "as much as fits"
choice, the disabled rows and the `min(8 GiB, freeBytes / 2)` default
described in the first draft of this section were never built.

**What follows ([`260925-2046`](../../../plans/260925-2046-external-cache-volume-and-lan-chunk-server/plan.md)):**
the list becomes a doubling ladder from 512 MiB up to the chosen volume's
cap, where

    cap = free space + bytes this cache already holds there − 1 GiB

Capacity, not free space: the cache's own bytes stop counting as free the
moment they are written, so a cap from free space alone would shrink on
every start. The 1 GiB floor stays for the reason it was first given — a
cache that fills a volume takes the rest of the device down with it. A
stored budget above the cap is clamped, for that run, to the largest step
of the ladder that fits, and the stored choice is kept for when space
returns.

A cache that fails anyway — a full disk, a pulled card — costs the player
its cache for the rest of that title, not its playback:
`PlayerFactory.playbackDataSourceFactory` sets
`FLAG_IGNORE_CACHE_ON_ERROR`. Preload deliberately does not.

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
`CacheDataSource.Factory` the player reads through — the player's only;
preload keeps the strict one, so it fails rather than download into a
cache that keeps nothing. Without it a write failure
on a yanked card propagates and playback stops; with it the read falls
through to upstream and the film keeps running. This is the correct
behaviour for the existing internal-storage cache too — a full disk produces
the same failure — so it is a fix this work happens to be adjacent to rather
than one it creates the need for.

## 7. The compromise: the location applies at next start

`ExoPlayer` is a process-wide Hilt singleton
(`PlaybackModule.provideExoPlayerDeferred`) holding a `CacheDataSource` over
the live `SimpleCache`. `SimpleCache.release()` while a data source is open
over it is not safe, and there is no supported way to swap the cache
underneath a built player.

Doing this properly means tearing down and rebuilding the player graph on a
settings change — a larger change to the app's object lifetime than the
feature that prompted it, for a setting a viewer changes approximately once.

So **the location** is persisted and takes effect when the app next starts,
and the screen says that in as many words rather than appearing to have
done something it has not. **The budget is not bound by this:** it applies
live, through the adjustable evictor. The location is the one thing in this
design that is a limitation rather than a decision, and it is recorded here
so that the next person to read `CacheProvider` finds out why from the spec
instead of from the behaviour.

## 8. The screen

**What shipped instead of a Storage destination:** a Settings screen
(`b91e9e8`) whose cache block chooses the budget. The location joins it
there rather than a screen of its own: a **Where** row lists the volumes,
each with its free space, beside the **How much** size list. Choosing
another volume says so in the two sentences the design owes the viewer:
that it takes effect the next time the app starts, and that titles already
held will be fetched again. A budget change needs neither sentence; it
applies at once.

The System screen's Cache block gains one row naming the volume in use, and
saying when that is not the volume that was asked for.

## 9. What this phase does not do

**The television devices get no screen for it here.** A TV surface now
exists on `feat/android-tv-ui`; it gets the mechanism in `:core:playback`
for free, and its own screen for the choice is owed by the TV plan.
Everything in sections 3 through 6 lives in `:core:playback` and
`:core:data` and is surface-independent.

**The LAN half is its own plan.** A box on the network holding chunks for
every Android device is
[`260925-2046-external-cache-volume-and-lan-chunk-server`](../../../plans/260925-2046-external-cache-volume-and-lan-chunk-server/plan.md):
a separate `mediagram_cache` server rather than the web player, which keeps
its own cache as it is.

## 10. Files and tests

**New**

| File | Holds |
|---|---|
| `:core:playback/CacheVolumes.kt` | `CacheVolume`, the pure list builder, the `Context` adapter |
| `:core:playback/CacheLocation.kt` | resolution against the recorded choice, the cap, the ladder |
| `:core:playback/CacheVolumeSettings.kt` | the recorded volume, beside the budget in `playback_settings` |
| `:ui-mobile/ui/settings/CacheVolumeBlock.kt`, `CacheSection.kt` | the "Where" row and the section hosting both cache blocks |

**Modified**

`CacheProvider.kt` (chosen dir, fallback, stale-directory deletion),
`PlayerFactory.kt` (`FLAG_IGNORE_CACHE_ON_ERROR` on the player's path),
`CacheBudgetViewModel.kt` (the ladder from the cap, the volumes),
`SettingsScreen.kt`, and the System screen's row naming the volume.

**Tests** — the volume list over a single-volume device, a device with a
card, and a device whose card is ejected; the cap and the ladder; fallback
to internal when the recorded volume is absent or fails to open; stale
directories deleted only after a successful open; the row wording,
including the fallback sentence; a failing cache falling through to the
network on the player's path only.

The version is whatever the bump at merge makes it; see `CLAUDE.md`.
