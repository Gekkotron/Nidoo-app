package com.gekkotron.nidoo.source

import com.gekkotron.nidoo.model.NidooJson
import com.gekkotron.nidoo.voice.VoiceAction
import com.gekkotron.nidoo.voice.VoiceIntent
import com.gekkotron.nidoo.voice.VoiceLang
import com.gekkotron.nidoo.voice.VoiceShortcut
import com.gekkotron.nidoo.voice.VoiceText
import kotlinx.serialization.Serializable

/** Retained JSON payload on `<prefix>/voice/shortcut/<id>` (voice spec §4). */
@Serializable
data class VoiceShortcutDocument(
    val v: Int = 1,
    val phrase: String,
    val lang: String,
    val action: String,
    val entityIds: List<String>,
    val value: Int? = null,
) {
    fun toShortcut(id: String? = null): VoiceShortcut? {
        val voiceLang = when (lang) {
            "fr" -> VoiceLang.FR
            "en" -> VoiceLang.EN
            else -> return null
        }
        val voiceAction = VoiceAction.entries.firstOrNull { it.name == action } ?: return null
        return VoiceShortcut(
            id = id ?: VoiceShortcut.id(voiceLang, VoiceText.normalizeShortcut(phrase, voiceLang)),
            phrase = phrase,
            lang = voiceLang,
            intent = VoiceIntent(voiceAction, entityIds, value),
        )
    }

    companion object {
        fun encode(doc: VoiceShortcutDocument): String = NidooJson.encodeToString(serializer(), doc)
        fun decode(payload: String): VoiceShortcutDocument? =
            runCatching { NidooJson.decodeFromString(serializer(), payload) }.getOrNull()
    }
}
