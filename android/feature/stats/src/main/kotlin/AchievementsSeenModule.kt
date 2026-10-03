package stats

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One [AchievementsSeen] for the process: the rail's dot and the Stats page
 * each have a ViewModel of their own, and the page marking an achievement
 * seen must put out the dot the rail is showing.
 */
@Module
@InstallIn(SingletonComponent::class)
object AchievementsSeenModule {
    @Provides
    @Singleton
    fun provideAchievementsSeen(
        @ApplicationContext context: Context,
    ): AchievementsSeen = SharedPreferencesAchievementsSeen(context)
}
