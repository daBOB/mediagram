package com.mediagram.android.di

import android.content.Context
import com.mediagram.android.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CoreClient
import data.DefaultCoreClient
import uniffi.mediagram_core.Core
import javax.inject.Singleton

/**
 * `Core` is constructed here, never earlier: the app only reaches a screen
 * that requests [CoreClient] once `MainActivity` has already confirmed the
 * Telegram application identity is configured, so a blank api id or hash
 * never reaches this native constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideCore(@ApplicationContext context: Context): Core = Core(
        dataDir = context.filesDir.absolutePath,
        apiId = BuildConfig.MEDIAGRAM_API_ID.toIntOrNull() ?: 0,
        apiHash = BuildConfig.MEDIAGRAM_API_HASH,
    )

    @Provides
    @Singleton
    fun provideCoreClient(core: Core): CoreClient = DefaultCoreClient(core)
}
