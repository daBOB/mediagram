package data.di

import javax.inject.Qualifier

/**
 * The coroutine scope for work that outlives any one screen: process
 * lifetime, cancelled only when the app itself is. [player.di.PlaybackModule]
 * already binds an unqualified `CoroutineScope` for the player alone; this
 * is a second, independent one — for [data.SharedLibraryEvents] and
 * [data.WatchSync] — so neither scope can be cancelled by whatever holds
 * the other.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
