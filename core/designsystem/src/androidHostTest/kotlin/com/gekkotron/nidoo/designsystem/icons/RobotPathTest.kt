package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import kotlin.math.round
import kotlin.test.assertEquals

class RobotPathTest {
    @Test
    fun `the robot starts top-left facing right and ends bottom-left facing left`() {
        assertEquals(RobotPose(Offset(5f, 5.5f), 0f), RobotPath.poseAt(0f))
        assertEquals(RobotPose(Offset(5f, 19f), 180f), RobotPath.poseAt(1f))
    }

    @Test
    fun `it turns down at the end of the first row`() {
        val p = RobotPath.poseAt((14f + 2.25f) / 69.5f)
        assertEquals(19f, p.center.x, 1e-3f)
        assertEquals(7.75f, p.center.y, 1e-3f)
        assertEquals(90f, p.headingDeg, 1e-3f)
    }

    @Test
    fun `out-of-range phases stay on the path`() {
        assertEquals(RobotPath.poseAt(0f), RobotPath.poseAt(-0.01f))
        assertEquals(RobotPath.poseAt(1f), RobotPath.poseAt(1.01f))
    }

    @Test
    fun `the trail holds every corner passed plus the robot's position`() {
        assertEquals(listOf(Offset(5f, 5.5f)), RobotPath.trailTo(0f))
        val t = (14f + 4.5f + 7f) / 69.5f
        assertEquals(
            listOf(Offset(5f, 5.5f), Offset(19f, 5.5f), Offset(19f, 10f), Offset(12f, 10f)),
            RobotPath.trailTo(t).map { Offset(round(it.x * 1000) / 1000, round(it.y * 1000) / 1000) },
        )
        assertEquals(RobotPath.ROWS, RobotPath.trailTo(1f))
    }

    @Test
    fun `the round takes ten brush periods, baseline for unknown speeds`() {
        assertEquals(8000, rowPeriodMs(null))
        assertEquals(8000, rowPeriodMs("Standard"))
        assertEquals(11000, rowPeriodMs("quiet"))
        assertEquals(6000, rowPeriodMs("turbo"))
        assertEquals(4500, rowPeriodMs("max"))
    }
}
