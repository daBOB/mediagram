package data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CatalogRepository
import data.CoreClient
import data.DefaultCatalogRepository
import settings.EncryptedPackageSettings
import settings.PackageSettings
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
    fun provideCatalogRepository(core: CoreClient, settings: PackageSettings): CatalogRepository =
        DefaultCatalogRepository(core, settings)
}
