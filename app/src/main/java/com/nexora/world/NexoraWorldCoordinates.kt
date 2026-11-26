package com.nexora.world

/** Converts the 140 x 140 Android city coordinates to the backend's normalized 0..1 plane. */
object NexoraWorldCoordinates {
    const val WORLD_SIZE = 140f
    private const val HALF_WORLD = WORLD_SIZE / 2f

    fun toNormalized(worldX: Float, worldZ: Float): Pair<Float, Float> {
        val x = ((worldX + HALF_WORLD) / WORLD_SIZE).coerceIn(0f, 1f)
        val y = ((worldZ + HALF_WORLD) / WORLD_SIZE).coerceIn(0f, 1f)
        return x to y
    }

    fun toWorld(normalizedX: Float, normalizedY: Float): Pair<Float, Float> {
        val x = normalizedX.coerceIn(0f, 1f) * WORLD_SIZE - HALF_WORLD
        val z = normalizedY.coerceIn(0f, 1f) * WORLD_SIZE - HALF_WORLD
        return x to z
    }
}
