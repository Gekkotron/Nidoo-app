package com.gekkotron.nidoo.feature.voice

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.assist.AssistClient
import com.gekkotron.nidoo.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val preferences: AppPreferences,
    private val client: AssistClient,
    private val player: AudioPlayer,
) : ViewModel() {
    private val _state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Listening())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()
    private val _closeRequested = MutableStateFlow(false)
    val closeRequested: StateFlow<Boolean> = _closeRequested.asStateFlow()
    private val _canRepeat = MutableStateFlow(false)
    val canRepeat: StateFlow<Boolean> = _canRepeat.asStateFlow()
    private var session: VoiceSession? = null

    val started: Boolean get() = session != null

    fun start() {
        if (started) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = VoiceSessionState.Error(ErrorKind.SPEECH)
            return
        }
        launchSession()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun launchSession() {
        val speech = try {
            AndroidSpeechCapture(appContext, "fr-FR")
        } catch (e: IllegalStateException) {
            _state.value = VoiceSessionState.Error(ErrorKind.SPEECH)
            return
        }
        val s = VoiceSession(client, speech, player, viewModelScope)
        session = s
        viewModelScope.launch { s.state.collect { _state.value = it } }
        viewModelScope.launch { s.closeRequested.collect { _closeRequested.value = it } }
        viewModelScope.launch { s.canRepeat.collect { _canRepeat.value = it } }
        s.start()
    }

    /** Shown instead of listening when the voice toggle is off; no session is started. */
    fun showDisabled() { _state.value = VoiceSessionState.Error(ErrorKind.DISABLED) }

    fun choose(index: Int) { session?.choose(index) }

    fun confirm(yes: Boolean) { session?.confirm(yes) }

    fun listenAgain() { session?.listenAgain() }

    fun repeat() { session?.repeat() }

    fun cancel() { session?.cancel() }

    override fun onCleared() { cancel() }
}

/** Suspends until DataStore has emitted the real preference (the flow is seeded with null). */
suspend fun awaitVoiceEnabled(flow: StateFlow<Boolean?>): Boolean = flow.filterNotNull().first()
