package com.gekkotron.nidoo.transport

/** Why a direct connection attempt failed; drives the onboarding messages (spec §5, §6). */
sealed interface DirectFailure {
    data object Unreachable : DirectFailure
    data object Rejected : DirectFailure
    data class Tls(val message: String) : DirectFailure
    data class Other(val message: String) : DirectFailure
}
