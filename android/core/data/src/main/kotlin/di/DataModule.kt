package data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CatalogRepository
import data.CoreProvider
import data.CoreStorage
import data.DefaultCatalogRepository
import data.CoreLibraryEvents
import data.DefaultPlayerPreferences
import data.DefaultWatchStateRepository
import data.DefaultWatchSync
import data.FileCoreStorage
import data.LibraryEvents
import data.PlayerPreferences
import data.RefreshLog
import data.SharedLibraryEvents
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import settings.EncryptedLibrarySettings
import settings.EncryptedTelegramSettings
import settings.EncryptedTmdbSettings
import settings.LibrarySettings
import settings.TelegramSettings
import settings.TmdbSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // The one dispatcher this app's blocking work goes to: keystore
    // decryption, the native library's first load, and the file reads
    // behind "is this device signed in". None of it may run on main, and a
    // ViewModel cannot be handed Dispatchers.IO directly and still be
    // testable.
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideLibrarySettings(@ApplicationContext context: Context): LibrarySettings =
        EncryptedLibrarySettings(context)

    @Provides
    @Singleton
    fun provideTelegramSettings(@ApplicationContext context: Context): TelegramSettings =
        EncryptedTelegramSettings(context)

    @Provides
    @Singleton
    fun provideTmdbSettings(@ApplicationContext context: Context): TmdbSettings =
        EncryptedTmdbSettings(context)

    // The same directory the core is constructed with, so clearing it
    // clears the state that core wrote.
    @Provides
    @Singleton
    fun provideCoreStorage(
        @ApplicationContext context: Context,
        dispatcher: CoroutineDispatcher,
    ): CoreStorage = FileCoreStorage(context.filesDir, dispatcher)

    // One per process, like the counters it is shaped after: the catalog
    // writes what a refresh did and the System screen reads it back, and a
    // second instance would leave the screen reporting on refreshes that
    // never happened.
    @Provides
    @Singleton
    fun provideRefreshLog(): RefreshLog = RefreshLog()

    @Provides
    @Singleton
    fun provideCatalogRepository(
        coreProvider: CoreProvider,
        settings: LibrarySettings,
        refreshes: RefreshLog,
        dispatcher: CoroutineDispatcher,
    ): CatalogRepository = DefaultCatalogRepository(coreProvider, settings, refreshes, dispatcher)

    // Process-lifetime work that is not the player: see AppScope's own doc
    // for why it is a second scope rather than the one PlaybackModule binds.
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The core serves one update stream: a second collector's wait queues
    // behind the first and takes whichever event comes next. Both the
    // catalog's INDEX handling and WatchSync's STATE handling read this one
    // shared collection instead of asking the core each on their own.
    @Provides
    @Singleton
    fun provideLibraryEvents(
        coreProvider: CoreProvider,
        settings: LibrarySettings,
        @AppScope scope: CoroutineScope,
    ): LibraryEvents = SharedLibraryEvents(CoreLibraryEvents(coreProvider, settings), scope)

    @Provides
    @Singleton
    fun provideWatchStateRepository(
        coreProvider: CoreProvider,
        dispatcher: CoroutineDispatcher,
    ): WatchStateRepository = DefaultWatchStateRepository(coreProvider, dispatcher)

    @Provides
    @Singleton
    fun providePlayerPreferences(coreProvider: CoreProvider): PlayerPreferences =
        DefaultPlayerPreferences(coreProvider)

    @Provides
    @Singleton
    fun provideWatchSync(
        coreProvider: CoreProvider,
        settings: LibrarySettings,
        repository: WatchStateRepository,
        libraryEvents: LibraryEvents,
        @AppScope scope: CoroutineScope,
    ): WatchSync = DefaultWatchSync(coreProvider, settings, repository, libraryEvents, scope)
}
