package data.di

import javax.inject.Qualifier

/**
 * The coroutine scope for work that outlives any one screen: process
 * lifetime, cancelled only when the app itself is, on `Dispatchers.Default`
 * — for [data.SharedLibraryEvents] and [data.WatchSync]. [MainThreadScope]
 * is the separate main-thread scope playback, LAN discovery and the
 * preloaders share; neither can be cancelled by whatever holds the other.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
