package com.gekkotron.nidoo.feature.onboarding.di

import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.data.ProfileRepository
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class OnboardingModuleTest {
    /** Both apps supply the repository and the tester (Hilt's bridge on Android, the iOS module); the rest must resolve. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun onboardingModuleResolves() {
        onboardingModule.verify(extraTypes = listOf(ProfileRepository::class, ConnectionTester::class))
    }
}
