package com.mediagram.android.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CoreProvider
import data.DefaultCoreClient
import data.StoredCoreProvider
import settings.TelegramSettings
import uniffi.mediagram_core.Core
import javax.inject.Singleton

/**
 * The only place the generated `Core` is named outside the client that
 * wraps it. Nothing here builds one: it hands [StoredCoreProvider] the
 * means to, and that happens the first time a stored Telegram application
 * identity is actually read back — so a blank api id or hash never reaches
 * this constructor, on a first run or after a reset.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideCoreProvider(
        @ApplicationContext context: Context,
        settings: TelegramSettings,
    ): CoreProvider = StoredCoreProvider(settings) { credentials ->
        DefaultCoreClient(
            Core(
                dataDir = context.filesDir.absolutePath,
                apiId = credentials.apiId,
                apiHash = credentials.apiHash,
            ),
        )
    }
}
