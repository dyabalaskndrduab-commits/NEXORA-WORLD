package com.nexora.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoraCityLayoutTest {
    @Test
    fun spawnAndRoadsRemainWalkable() {
        assertTrue(NexoraCityLayout.canOccupy(0f, 3f))
        assertTrue(NexoraCityLayout.canOccupy(0f, 25f))
        assertTrue(NexoraCityLayout.canOccupy(25f, 0f))
    }

    @Test
    fun buildingFootprintsBlockTheAvatar() {
        assertFalse(NexoraCityLayout.canOccupy(-11f, -7f))
        assertFalse(NexoraCityLayout.canOccupy(35f, -35f))
        assertFalse(NexoraCityLayout.canOccupy(35f, 0f))
        assertFalse(NexoraCityLayout.canOccupy(0f, -58f))
    }

    @Test
    fun avatarMustStayInsideTheWorldBoundary() {
        assertTrue(NexoraCityLayout.canOccupy(63.4f, 0f))
        assertFalse(NexoraCityLayout.canOccupy(63.6f, 0f))
        assertFalse(NexoraCityLayout.canOccupy(0f, 64.5f))
    }

    @Test
    fun invalidCoordinatesAndRadiusAreRejected() {
        assertFalse(NexoraCityLayout.canOccupy(Float.NaN, 0f))
        assertFalse(NexoraCityLayout.canOccupy(0f, Float.POSITIVE_INFINITY))
        assertFalse(NexoraCityLayout.canOccupy(0f, 0f, Float.NaN))
        assertFalse(NexoraCityLayout.canOccupy(0f, 0f, -1f))
    }

    @Test
    fun avatarCanPassBesideBuildingFootprints() {
        assertTrue(NexoraCityLayout.canOccupy(-14f, -7f))
    }
}
