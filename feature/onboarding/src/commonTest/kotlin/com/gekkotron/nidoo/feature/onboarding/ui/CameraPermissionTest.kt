package com.gekkotron.nidoo.feature.onboarding.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Coming back from the system settings (ON_RESUME): a permission granted there shows the camera at once. */
class CameraPermissionTest {
    @Test
    fun deniedThenGrantedShowsTheCamera() {
        assertEquals(CameraPermission.GRANTED, CameraPermission.DENIED.onResume(granted = true))
        assertEquals(CameraPermission.GRANTED, CameraPermission.WAITING.onResume(granted = true))
    }

    @Test
    fun resumeWithoutGrantKeepsTheState() {
        assertEquals(CameraPermission.DENIED, CameraPermission.DENIED.onResume(granted = false))
        assertEquals(CameraPermission.WAITING, CameraPermission.WAITING.onResume(granted = false))
    }
}
