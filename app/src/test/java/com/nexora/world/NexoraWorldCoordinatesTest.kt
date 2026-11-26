package com.nexora.world

import org.junit.Assert.assertEquals
import org.junit.Test

class NexoraWorldCoordinatesTest {
    @Test
    fun worldCornersMapToNormalizedBounds() {
        assertEquals(0f, NexoraWorldCoordinates.toNormalized(-70f, -70f).first, 0.0001f)
        assertEquals(0f, NexoraWorldCoordinates.toNormalized(-70f, -70f).second, 0.0001f)
        assertEquals(1f, NexoraWorldCoordinates.toNormalized(70f, 70f).first, 0.0001f)
        assertEquals(1f, NexoraWorldCoordinates.toNormalized(70f, 70f).second, 0.0001f)
    }

    @Test
    fun cityCenterMapsToNormalizedCenter() {
        val point = NexoraWorldCoordinates.toNormalized(0f, 0f)
        assertEquals(0.5f, point.first, 0.0001f)
        assertEquals(0.5f, point.second, 0.0001f)
    }

    @Test
    fun normalizedCoordinatesRoundTrip() {
        val world = NexoraWorldCoordinates.toWorld(0.25f, 0.8f)
        val normalized = NexoraWorldCoordinates.toNormalized(world.first, world.second)
        assertEquals(0.25f, normalized.first, 0.0001f)
        assertEquals(0.8f, normalized.second, 0.0001f)
    }

    @Test
    fun serverCoordinatesAreClampedToCityBounds() {
        val world = NexoraWorldCoordinates.toWorld(-2f, 3f)
        assertEquals(-70f, world.first, 0.0001f)
        assertEquals(70f, world.second, 0.0001f)
    }
}
