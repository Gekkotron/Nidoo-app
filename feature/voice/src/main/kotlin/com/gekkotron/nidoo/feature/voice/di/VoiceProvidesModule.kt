package com.gekkotron.nidoo.feature.voice.di

import android.content.Context
import com.gekkotron.nidoo.feature.voice.AndroidAudioPlayer
import com.gekkotron.nidoo.feature.voice.AndroidTtsEngine
import com.gekkotron.nidoo.feature.voice.AudioPlayer
import com.gekkotron.nidoo.feature.voice.TtsSpeaker
import com.gekkotron.nidoo.voice.VoiceLang
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceProvidesModule {
    @Provides @Singleton
    fun audioPlayer(@ApplicationContext context: Context): AudioPlayer = AndroidAudioPlayer(context)

    // Kept until the settings screen stops listing the phone's TTS state.
    @Provides @Singleton
    fun ttsSpeaker(@ApplicationContext context: Context): TtsSpeaker =
        TtsSpeaker(VoiceLang.fromAppLocale(Locale.getDefault().toLanguageTag())) { listener ->
            AndroidTtsEngine(context, listener)
        }
}
