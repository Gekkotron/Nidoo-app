package com.gekkotron.nidoo.feature.controls

class ControlsComponent(val session: ControlsSession)

interface ControlsComponentProvider {
    fun controlsComponent(): ControlsComponent
}
