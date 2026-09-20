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
import data.FileCoreStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    @Provides
    @Singleton
    fun provideCatalogRepository(
        coreProvider: CoreProvider,
        settings: LibrarySettings,
    ): CatalogRepository = DefaultCatalogRepository(coreProvider, settings)
}
