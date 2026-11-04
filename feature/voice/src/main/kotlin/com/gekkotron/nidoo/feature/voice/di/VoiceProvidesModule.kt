package com.gekkotron.nidoo.feature.voice.di

import android.content.Context
import com.gekkotron.nidoo.feature.voice.AndroidAudioPlayer
import com.gekkotron.nidoo.feature.voice.AudioPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceProvidesModule {
    @Provides @Singleton
    fun audioPlayer(@ApplicationContext context: Context): AudioPlayer = AndroidAudioPlayer(context)
}
