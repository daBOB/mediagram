package update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import update.ApkInstaller
import update.PackageApkInstaller

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindings {
    @Binds
    abstract fun installer(impl: PackageApkInstaller): ApkInstaller
}
