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
import settings.EncryptedPackageSettings
import settings.EncryptedTelegramSettings
import settings.PackageSettings
import settings.TelegramSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providePackageSettings(@ApplicationContext context: Context): PackageSettings =
        EncryptedPackageSettings(context)

    @Provides
    @Singleton
    fun provideTelegramSettings(@ApplicationContext context: Context): TelegramSettings =
        EncryptedTelegramSettings(context)

    // The same directory the core is constructed with, so clearing it
    // clears the state that core wrote.
    @Provides
    @Singleton
    fun provideCoreStorage(@ApplicationContext context: Context): CoreStorage =
        FileCoreStorage(context.filesDir)

    @Provides
    @Singleton
    fun provideCatalogRepository(
        coreProvider: CoreProvider,
        settings: PackageSettings,
    ): CatalogRepository = DefaultCatalogRepository(coreProvider, settings)
}
