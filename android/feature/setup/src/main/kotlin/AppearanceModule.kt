package setup

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import designsystem.AppearanceSettings
import designsystem.SharedPreferencesAppearanceSettings
import javax.inject.Singleton

/**
 * One [AppearanceSettings] for the whole process: [AppearanceViewModel] is
 * asked for at both the root theme composition and Settings' own screen,
 * on the phone and the television, and all of them must see the same
 * live choice the moment one of them changes it.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppearanceModule {
    @Provides
    @Singleton
    fun provideAppearanceSettings(
        @ApplicationContext context: Context,
    ): AppearanceSettings = SharedPreferencesAppearanceSettings(context)
}
