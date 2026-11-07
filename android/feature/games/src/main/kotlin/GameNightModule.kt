package games

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface GameNightModule {
    @Binds
    fun announcer(impl: NsdGameNightAnnouncer): GameNightAnnouncer
}
