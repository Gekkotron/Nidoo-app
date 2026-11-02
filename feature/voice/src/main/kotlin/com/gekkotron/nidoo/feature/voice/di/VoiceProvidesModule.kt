package com.gekkotron.nidoo.feature.voice.di

import com.gekkotron.nidoo.feature.voice.CommandRunner
import com.gekkotron.nidoo.feature.voice.VoiceCommandRunner
import com.gekkotron.nidoo.source.EntitySource
import android.content.Context
import com.gekkotron.nidoo.feature.voice.AndroidTtsEngine
import com.gekkotron.nidoo.feature.voice.TtsSpeaker
import com.gekkotron.nidoo.voice.VoiceLang
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceProvidesModule {
    @Provides @Singleton
    fun commandRunner(source: Provider<EntitySource>): CommandRunner = VoiceCommandRunner { source.get() }

    @Provides @Singleton
    fun ttsSpeaker(@ApplicationContext context: Context): TtsSpeaker =
        TtsSpeaker(VoiceLang.fromAppLocale(Locale.getDefault().toLanguageTag())) { listener ->
            AndroidTtsEngine(context, listener)
        }
}
