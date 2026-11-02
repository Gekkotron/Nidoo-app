package com.gekkotron.nidoo

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.EntityCache
import com.gekkotron.nidoo.data.FileEntityCache
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.di.ApplicationScope
import com.gekkotron.nidoo.di.DataModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import javax.inject.Singleton

/** Robolectric has no AndroidKeyStore, and each test needs its own file (one DataStore per file per process). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
    @Provides
    @Singleton
    fun profiles(@ApplicationScope scope: CoroutineScope): ProfileRepository =
        ProfileRepository.create(Files.createTempDirectory("nidoo").resolve("profile.json").toFile(), PassThrough, scope)

    @Provides
    @Singleton
    fun preferences(@ApplicationScope scope: CoroutineScope): AppPreferences =
        AppPreferences.create(Files.createTempDirectory("nidoo").resolve("app-preferences.json").toFile(), scope)

    @Provides
    @Singleton
    fun entityCache(@ApplicationScope scope: CoroutineScope): EntityCache =
        FileEntityCache.create(Files.createTempDirectory("nidoo").resolve("entity-cache.json").toFile(), scope)

    private object PassThrough : SecretStore {
        override fun encrypt(plain: ByteArray) = plain.copyOf()
        override fun decrypt(cipher: ByteArray) = cipher.copyOf()
    }
}
