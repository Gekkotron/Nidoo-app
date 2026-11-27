package com.gekkotron.nidoo.feature.voice

object SpeechAvailability {
    sealed interface Status {
        data object Ready : Status
        data object NeedsApi31 : Status
        data object NoOnDevice : Status
        data class LanguagePackMissing(val tag: String) : Status
    }
}
