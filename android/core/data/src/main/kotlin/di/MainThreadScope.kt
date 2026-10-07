package data.di

import javax.inject.Qualifier

/**
 * The process-lifetime scope on `Dispatchers.Main.immediate` that playback,
 * LAN discovery and the preloaders share. Its single thread is a guarantee,
 * not a detail: `LanServerLocator` keeps its state without a lock because
 * everything it does runs here. Not named `MainScope`: that would shadow
 * kotlinx's `MainScope()`, and a file importing both would not compile.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainThreadScope
