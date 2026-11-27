package com.gekkotron.nidoo.feature.voice.di

import android.os.Build
import com.gekkotron.nidoo.feature.voice.AndroidAudioPlayer
import com.gekkotron.nidoo.feature.voice.AndroidSpeechCapture
import com.gekkotron.nidoo.feature.voice.AudioPlayer
import com.gekkotron.nidoo.feature.voice.SpeechCaptureFactory
import com.gekkotron.nidoo.feature.voice.VOICE_LOCALE
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** The Android recognizer (API 31+, on-device) and the AudioTrack player. */
val androidVoiceModule: Module = module {
    single<AudioPlayer> { AndroidAudioPlayer(androidContext()) }
    single<SpeechCaptureFactory> {
        val context = androidContext()
        SpeechCaptureFactory {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                null
            } else {
                try {
                    AndroidSpeechCapture(context, VOICE_LOCALE)
                } catch (e: IllegalStateException) {
                    null
                }
            }
        }
    }
}
