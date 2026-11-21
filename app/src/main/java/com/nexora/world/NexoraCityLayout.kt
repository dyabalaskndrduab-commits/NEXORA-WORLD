package com.nexora.world

import kotlin.math.abs

/**
 * World bounds and solid building footprints for the playable city.
 * Kept independent of Android/OpenGL so movement collision rules are easy to test.
 */
internal object NexoraCityLayout {
    const val WORLD_LIMIT = 64f

    private data class Obstacle(
        val x: Float,
        val z: Float,
        val halfWidth: Float,
        val halfDepth: Float
    )

    private val obstacles = listOf(
        // Central plaza buildings and shops.
        Obstacle(-11f, -7f, 2.5f, 2.5f),
        Obstacle(10f, -8f, 2.5f, 2.5f),
        Obstacle(-11f, 8f, 2.5f, 2.5f),
        Obstacle(11f, 8f, 2.5f, 2.5f),
        Obstacle(-5.5f, -9.8f, 2.2f, 1.35f),
        Obstacle(5.5f, -9.8f, 2.2f, 1.35f),
        Obstacle(-5.5f, 10f, 2.2f, 1.35f),

        // Residential towers in the outer ring.
        Obstacle(-35f, -35f, 4f, 4.5f),
        Obstacle(35f, -35f, 4.5f, 4f),
        Obstacle(-35f, 35f, 4f, 4.5f),
        Obstacle(35f, 35f, 4.5f, 4.5f),

        // Retail buildings and the two district landmarks.
        Obstacle(-35f, 0f, 2.2f, 1.35f),
        Obstacle(35f, 0f, 2.2f, 1.35f),
        Obstacle(0f, -58f, 6.5f, 5f),
        Obstacle(0f, 58f, 6f, 5f)
    )

    /** Returns false outside the city or when the avatar footprint intersects a building. */
    fun canOccupy(x: Float, z: Float, radius: Float = .48f): Boolean {
        if (!x.isFinite() || !z.isFinite() || !radius.isFinite() || radius < 0f) return false
        if (abs(x) + radius > WORLD_LIMIT || abs(z) + radius > WORLD_LIMIT) return false
        return obstacles.none { obstacle ->
            abs(x - obstacle.x) < obstacle.halfWidth + radius &&
                abs(z - obstacle.z) < obstacle.halfDepth + radius
        }
    }
}
