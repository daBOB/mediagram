package games

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface GameNightModule {
    @Binds
    fun announcer(impl: NsdGameNightAnnouncer): GameNightAnnouncer

    companion object {
        /** In this app's private files: only it, and through it Spieltisch, ever reads the night. */
        @Provides
        @Singleton
        fun snapshots(
            @ApplicationContext context: Context,
        ): NightSnapshotStore {
            val app = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) app.longVersionCode else @Suppress("DEPRECATION") app.versionCode.toLong()
            return NightSnapshotStore(File(context.filesDir, "game-night"), version)
        }
    }
}
