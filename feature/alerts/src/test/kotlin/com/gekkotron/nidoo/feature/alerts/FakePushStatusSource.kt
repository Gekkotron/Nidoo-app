package com.gekkotron.nidoo.feature.alerts

import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.push.PushStatusSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakePushStatusSource(initial: PushStatus = PushStatus.Waiting) : PushStatusSource {
    override val status = MutableStateFlow(initial)
    var refreshes = 0
    var registerAgains = 0

    override fun refresh() {
        refreshes++
    }

    override fun registerAgain() {
        registerAgains++
    }
}
