package com.nexora.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class NexoraWorldView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatar = floatArrayOf(.50f, .73f)
    private val joystick = floatArrayOf(0f, 0f)
    private var joystickActive = false
    private var lastFrame = SystemClock.uptimeMillis()
    private var elapsed = 0f
    private var coins = 1250
    private var crystals = 0
    private var xp = 0
    private var level = 1
    private var speedBoost = 0f
    private var toast = "Explore the plaza and collect 5 crystals!"
    private var toastTimer = 5f
    private var questComplete = false
    private var npcRewarded = false
    private var paused = false
    private var facing = 1f
    private var walkPhase = 0f

    private data class Crystal(var x: Float, var y: Float, var active: Boolean = true, var respawn: Float = 0f)
    private val crystalsInWorld = mutableListOf(
        Crystal(.20f, .73f), Crystal(.34f, .56f), Crystal(.68f, .76f),
        Crystal(.82f, .57f), Crystal(.50f, .48f), Crystal(.34f, .82f),
        Crystal(.72f, .48f)
    )
    private var npcX = .75f
    private var npcY = .66f
    private var npcDirection = -1f

    init { isFocusable = true }

    private fun say(message: String) {
        toast = message
        toastTimer = 3.2f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        val dt = min(.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        if (!paused) updateWorld(dt, w, h)
        drawWorld(canvas, w, h)
        postInvalidateDelayed(16)
    }

    private fun updateWorld(dt: Float, w: Float, h: Float) {
        elapsed += dt
        if (toastTimer > 0f) toastTimer -= dt
        if (speedBoost > 0f) speedBoost = max(0f, speedBoost - dt)
        val moving = sqrt(joystick[0] * joystick[0] + joystick[1] * joystick[1])
        if (moving > .08f) {
            val boost = if (speedBoost > 0f) 1.65f else 1f
            val speed = .34f * boost * dt
            avatar[0] = (avatar[0] + joystick[0] * speed).coerceIn(.07f, .93f)
            avatar[1] = (avatar[1] + joystick[1] * speed * (w / h)).coerceIn(.43f, .91f)
            facing = if (joystick[0] < 0) -1f else if (joystick[0] > 0) 1f else facing
            walkPhase += dt * 11f
        }
        npcX += npcDirection * dt * .035f
        if (npcX < .68f || npcX > .82f) npcDirection *= -1f
        crystalsInWorld.forEach { crystal ->
            if (!crystal.active) {
                crystal.respawn -= dt
                if (crystal.respawn <= 0f) crystal.active = true
            } else {
                val dx = (avatar[0] - crystal.x) * w
                val dy = (avatar[1] - crystal.y) * h
                if (sqrt(dx * dx + dy * dy) < min(w, h) * .055f) {
                    crystal.active = false
                    crystal.respawn = 8f
                    crystals++
                    coins += 15
                    xp += 10
                    if (crystals >= 5 && !questComplete) {
                        questComplete = true
                        coins += 250
                        xp += 50
                        say("QUEST COMPLETE! +250 coins and +50 XP")
                    } else say("Crystal collected! +15 coins")
                    updateLevel()
                }
            }
        }
    }

    private fun updateLevel() {
        val needed = level * 100
        if (xp >= needed) {
            xp -= needed
            level++
            say("LEVEL UP! You are now level $level")
        }
    }

    private fun drawWorld(c: Canvas, w: Float, h: Float) {
        // Sky, distant hills and the green city outskirts.
        c.drawColor(Color.rgb(119, 183, 207))
        p.style = Paint.Style.FILL
        p.color = Color.rgb(89, 151, 145)
        c.drawRect(0f, h * .40f, w, h, p)
        p.color = Color.rgb(92, 157, 139)
        c.drawOval(-w * .18f, h * .30f, w * .42f, h * .63f, p)
        c.drawOval(w * .60f, h * .29f, w * 1.16f, h * .64f, p)

        // Soft clouds.
        p.color = Color.argb(115, 255, 255, 255)
        for (i in 0..3) {
            val x = w * (.10f + i * .28f)
            val y = h * (.12f + (i % 2) * .06f)
            c.drawCircle(x, y, h * .018f, p)
            c.drawCircle(x + h * .025f, y - h * .008f, h * .023f, p)
            c.drawCircle(x + h * .05f, y, h * .017f, p)
        }

        // Distant skyline gives the district depth.
        for (i in 0..10) {
            val bw = w * (.055f + (i % 3) * .012f)
            val x = i * w * .096f
            val top = h * (.24f + (i % 4) * .035f)
            p.color = when (i % 3) {
                0 -> Color.rgb(67, 91, 119)
                1 -> Color.rgb(78, 105, 128)
                else -> Color.rgb(92, 111, 132)
            }
            c.drawRect(x, top, x + bw, h * .58f, p)
            p.color = Color.rgb(232, 211, 158)
            for (row in 0..3) for (col in 0..1) {
                val wx = x + bw * (.20f + col * .48f)
                val wy = top + h * .035f + row * h * .062f
                c.drawRoundRect(wx, wy, wx + bw * .16f, wy + h * .022f, 3f, 3f, p)
            }
        }

        // Main city avenue and side streets.
        p.color = Color.rgb(65, 76, 87)
        c.drawRect(0f, h * .49f, w, h * .62f, p)
        c.drawRect(w * .08f, h * .34f, w * .15f, h, p)
        c.drawRect(w * .78f, h * .34f, w * .85f, h, p)
        p.color = Color.rgb(194, 190, 174)
        c.drawRect(0f, h * .485f, w, h * .495f, p)
        c.drawRect(0f, h * .615f, w, h * .625f, p)
        p.color = Color.rgb(238, 222, 164)
        for (i in 0..10) {
            val x = w * (.025f + i * .095f)
            c.drawRoundRect(x, h * .545f, x + w * .045f, h * .552f, 4f, 4f, p)
        }
        p.color = Color.rgb(202, 197, 179)
        c.drawRect(w * .055f, h * .34f, w * .08f, h, p)
        c.drawRect(w * .15f, h * .34f, w * .175f, h, p)
        c.drawRect(w * .755f, h * .34f, w * .78f, h, p)
        c.drawRect(w * .85f, h * .34f, w * .875f, h, p)

        // Distinct city blocks and storefronts.
        drawBuilding(c, w * .19f, h * .34f, w * .16f, h * .25f, Color.rgb(73, 105, 139))
        drawBuilding(c, w * .37f, h * .28f, w * .12f, h * .22f, Color.rgb(83, 126, 139))
        drawBuilding(c, w * .53f, h * .32f, w * .13f, h * .18f, Color.rgb(143, 104, 91))
        drawBuilding(c, w * .88f, h * .32f, w * .10f, h * .27f, Color.rgb(132, 89, 121))
        drawShop(c, w * .20f, h * .66f, w * .16f, h * .14f, "CAFE", Color.rgb(190, 117, 77))
        drawShop(c, w * .64f, h * .65f, w * .13f, h * .15f, "ARCADE", Color.rgb(93, 111, 174))
        drawShop(c, w * .86f, h * .68f, w * .12f, h * .13f, "STYLE", Color.rgb(155, 91, 139))

        // Central pedestrian square, tiled paths and garden pockets.
        val plaza = Path().apply {
            moveTo(w * .5f, h * .39f)
            lineTo(w * .76f, h * .69f)
            lineTo(w * .5f, h * .98f)
            lineTo(w * .24f, h * .69f)
            close()
        }
        p.color = Color.rgb(194, 181, 153)
        c.drawPath(plaza, p)
        p.color = Color.rgb(226, 215, 190)
        p.strokeWidth = 2f
        for (i in 1..6) {
            val t = i / 7f
            c.drawLine(w * (.5f - .25f * t), h * (.39f + .30f * t),
                w * (.5f + .25f * t), h * (.39f + .30f * t), p)
        }
        c.drawLine(w * .5f, h * .39f, w * .5f, h * .98f, p)
        c.drawLine(w * .24f, h * .69f, w * .76f, h * .69f, p)

        // Park corner, landscaping and street furniture.
        drawTree(c, w * .13f, h * .77f)
        drawTree(c, w * .36f, h * .88f)
        drawTree(c, w * .83f, h * .78f)
        drawTree(c, w * .94f, h * .88f)
        drawTree(c, w * .28f, h * .48f)
        drawStreetLamp(c, w * .17f, h * .61f)
        drawStreetLamp(c, w * .74f, h * .59f)
        drawBench(c, w * .34f, h * .76f)
        drawBench(c, w * .68f, h * .83f)
        drawFountain(c, w * .5f, h * .67f)
        drawCitySign(c, w * .5f, h * .37f, "NEXORA PLAZA")

        // Live world objects are drawn over the environment.
        crystalsInWorld.filter { it.active }.forEach { drawCrystal(c, w * it.x, h * it.y, h) }
        drawNpc(c, w * npcX, h * npcY, h)
        drawAvatar(c, w * avatar[0], h * avatar[1], h)
        drawHud(c, w, h)
        drawControls(c, w, h)
    }

    private fun drawShop(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, label: String, color: Int) {
        p.color = Color.argb(55, 0, 0, 0)
        c.drawRoundRect(x - 5f, y + bh - 4f, x + bw + 7f, y + bh + 8f, 8f, 8f, p)
        p.color = color
        c.drawRect(x, y, x + bw, y + bh, p)
        p.color = Color.rgb(244, 226, 183)
        c.drawRect(x - 4f, y - bh * .18f, x + bw + 4f, y + 5f, p)
        p.color = Color.rgb(46, 57, 74)
        c.drawRect(x + bw * .08f, y + bh * .25f, x + bw * .92f, y + bh * .72f, p)
        p.color = Color.rgb(119, 207, 218)
        c.drawRect(x + bw * .14f, y + bh * .31f, x + bw * .48f, y + bh * .66f, p)
        c.drawRect(x + bw * .54f, y + bh * .31f, x + bw * .86f, y + bh * .66f, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = bh * .16f
        c.drawText(label, x + bw / 2f, y + bh * .12f, p)
    }

    private fun drawStreetLamp(c: Canvas, x: Float, y: Float) {
        p.color = Color.rgb(54, 63, 72)
        c.drawRect(x - 2f, y - 54f, x + 2f, y + 8f, p)
        c.drawLine(x, y - 52f, x + 13f, y - 60f, p)
        p.color = Color.rgb(255, 232, 160)
        c.drawCircle(x + 13f, y - 59f, 6f, p)
        p.color = Color.argb(40, 255, 232, 160)
        c.drawCircle(x + 13f, y - 59f, 14f, p)
    }

    private fun drawBench(c: Canvas, x: Float, y: Float) {
        p.color = Color.rgb(104, 70, 49)
        c.drawRoundRect(x - 20f, y - 8f, x + 20f, y + 1f, 3f, 3f, p)
        c.drawRect(x - 16f, y + 1f, x - 12f, y + 12f, p)
        c.drawRect(x + 12f, y + 1f, x + 16f, y + 12f, p)
        c.drawRect(x - 20f, y - 18f, x - 15f, y - 5f, p)
        c.drawRect(x + 15f, y - 18f, x + 20f, y - 5f, p)
    }

    private fun drawCitySign(c: Canvas, x: Float, y: Float, label: String) {
        p.color = Color.rgb(47, 65, 82)
        c.drawRoundRect(x - 62f, y - 22f, x + 62f, y - 2f, 7f, 7f, p)
        p.color = Color.rgb(113, 226, 221)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 11f
        c.drawText(label, x, y - 8f, p)
    }

    private fun drawBuilding(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, color: Int) {
        p.color = Color.argb(45, 0, 0, 0)
        c.drawOval(x - 8, y + bh - 4, x + bw + 12, y + bh + 12, p)
        p.color = color
        c.drawRect(x, y, x + bw, y + bh, p)
        val roof = Path().apply { moveTo(x - 8, y); lineTo(x + bw / 2, y - bh * .22f); lineTo(x + bw + 8, y); close() }
        p.color = Color.rgb(45, 61, 82); c.drawPath(roof, p)
        p.color = Color.rgb(244, 211, 142)
        for (row in 0..1) for (col in 0..2) {
            val wx = x + bw * (.17f + col * .28f)
            val wy = y + bh * (.22f + row * .30f)
            c.drawRoundRect(wx, wy, wx + bw * .13f, wy + bh * .13f, 3f, 3f, p)
        }
        p.color = Color.rgb(40, 54, 70)
        c.drawRect(x + bw * .42f, y + bh * .72f, x + bw * .59f, y + bh, p)
    }

    private fun drawTree(c: Canvas, x: Float, y: Float) {
        p.color = Color.rgb(100, 70, 48); c.drawRect(x - 7, y - 35, x + 7, y + 18, p)
        p.color = Color.rgb(48, 123, 91); c.drawCircle(x, y - 48, 30f, p)
        p.color = Color.rgb(72, 151, 105); c.drawCircle(x - 13, y - 57, 18f, p)
        p.color = Color.rgb(89, 166, 115); c.drawCircle(x + 13, y - 47, 16f, p)
    }

    private fun drawFountain(c: Canvas, x: Float, y: Float) {
        p.color = Color.rgb(128, 145, 157); c.drawOval(x - 65, y - 18, x + 65, y + 22, p)
        p.color = Color.rgb(82, 177, 207); c.drawOval(x - 48, y - 12, x + 48, y + 13, p)
        p.color = Color.rgb(220, 235, 236); c.drawCircle(x, y - 22 + sin(elapsed * 3f) * 3f, 10f, p)
        p.color = Color.argb(130, 220, 245, 255)
        c.drawLine(x, y - 28, x - 7, y - 48, p)
        c.drawLine(x, y - 28, x + 7, y - 48, p)
    }

    private fun drawCrystal(c: Canvas, x: Float, y: Float, h: Float) {
        val pulse = 1f + .13f * sin(elapsed * 4f + x)
        val r = h * .014f * pulse
        p.color = Color.argb(55, 85, 240, 255)
        c.drawCircle(x, y, r * 2.4f, p)
        val gem = Path().apply {
            moveTo(x, y - r * 1.7f); lineTo(x + r, y - r * .3f)
            lineTo(x + r * .65f, y + r * 1.5f); lineTo(x - r * .65f, y + r * 1.5f)
            lineTo(x - r, y - r * .3f); close()
        }
        p.color = Color.rgb(74, 226, 239); c.drawPath(gem, p)
        p.color = Color.WHITE; c.drawCircle(x - r * .25f, y - r * .45f, r * .22f, p)
    }

    private fun drawNpc(c: Canvas, x: Float, y: Float, h: Float) {
        p.color = Color.argb(45, 0, 0, 0); c.drawOval(x - 17, y + 24, x + 17, y + 32, p)
        p.color = Color.rgb(225, 151, 63); c.drawRoundRect(x - 14, y - 3, x + 14, y + 27, 9f, 9f, p)
        p.color = Color.rgb(238, 190, 151); c.drawCircle(x, y - 18, 13f, p)
        p.color = Color.rgb(71, 47, 36); c.drawArc(x - 13, y - 32, x + 13, y - 8, 180f, 180f, true, p)
        p.color = Color.WHITE; p.textSize = h * .018f; p.textAlign = Paint.Align.CENTER
        c.drawText("MILO", x, y - 42, p)
    }

    // Player's first customisable NEXORA avatar: black streetwear, crown hoodie and premium sneakers.
    private fun drawAvatar(c: Canvas, x: Float, y: Float, h: Float) {
        val s = h / 520f
        val moving = sqrt(joystick[0] * joystick[0] + joystick[1] * joystick[1]) > .08f
        val bob = if (moving) sin(walkPhase) * 2.5f * s else 0f
        val step = if (moving) sin(walkPhase) * 5f * s else 0f
        val top = y - 70f * s + bob
        c.save()
        // Ground shadow.
        p.color = Color.argb(75, 0, 0, 0)
        c.drawOval(x - 24f*s, y + 5f*s, x + 24f*s, y + 13f*s, p)

        // Cargo trousers and articulated legs.
        p.color = Color.rgb(25, 29, 42)
        c.drawRoundRect(x - 15f*s, y - 4f*s + bob, x - 2f*s, y + 20f*s + step, 5f*s, 5f*s, p)
        c.drawRoundRect(x + 2f*s, y - 4f*s + bob, x + 15f*s, y + 20f*s - step, 5f*s, 5f*s, p)
        p.color = Color.rgb(54, 61, 78)
        c.drawRoundRect(x - 16f*s, y + 3f*s + bob, x - 2f*s, y + 10f*s + bob, 2f*s, 2f*s, p)
        c.drawRoundRect(x + 2f*s, y + 3f*s + bob, x + 16f*s, y + 10f*s + bob, 2f*s, 2f*s, p)
        p.color = Color.rgb(236, 237, 241)
        c.drawRoundRect(x - 19f*s, y + 17f*s + step, x - 1f*s, y + 25f*s + step, 4f*s, 4f*s, p)
        c.drawRoundRect(x + 1f*s, y + 17f*s - step, x + 20f*s, y + 25f*s - step, 4f*s, 4f*s, p)
        p.color = Color.rgb(164, 177, 192)
        c.drawLine(x - 15f*s, y + 21f*s + step, x - 4f*s, y + 21f*s + step, p)
        c.drawLine(x + 5f*s, y + 21f*s - step, x + 16f*s, y + 21f*s - step, p)

        // Hoodie sleeves and hands.
        p.color = Color.rgb(19, 22, 34)
        c.drawRoundRect(x - 25f*s, y - 38f*s + bob, x - 13f*s, y - 7f*s + bob, 6f*s, 6f*s, p)
        c.drawRoundRect(x + 13f*s, y - 38f*s + bob, x + 25f*s, y - 7f*s + bob, 6f*s, 6f*s, p)
        p.color = Color.rgb(239, 185, 145)
        c.drawCircle(x - 19f*s, y - 6f*s + bob, 5f*s, p)
        c.drawCircle(x + 19f*s, y - 6f*s + bob, 5f*s, p)

        // Main black hoodie with violet piping.
        p.color = Color.rgb(24, 27, 43)
        c.drawRoundRect(x - 18f*s, y - 48f*s + bob, x + 18f*s, y - 5f*s + bob, 9f*s, 9f*s, p)
        p.color = Color.rgb(111, 75, 222)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f*s
        c.drawRoundRect(x - 18f*s, y - 48f*s + bob, x + 18f*s, y - 5f*s + bob, 9f*s, 9f*s, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(9, 12, 22)
        c.drawRoundRect(x - 11f*s, y - 48f*s + bob, x + 11f*s, y - 35f*s + bob, 7f*s, 7f*s, p)
        // Small gold crown emblem on the chest.
        val crown = Path().apply {
            moveTo(x - 7f*s, y - 28f*s + bob); lineTo(x - 8f*s, y - 36f*s + bob)
            lineTo(x - 3f*s, y - 32f*s + bob); lineTo(x, y - 38f*s + bob)
            lineTo(x + 3f*s, y - 32f*s + bob); lineTo(x + 8f*s, y - 36f*s + bob)
            lineTo(x + 7f*s, y - 28f*s + bob); close()
        }
        p.color = Color.rgb(245, 195, 76)
        c.drawPath(crown, p)
        p.color = Color.rgb(245, 195, 76)
        c.drawRect(x - 7f*s, y - 27f*s + bob, x + 7f*s, y - 25f*s + bob, p)

        // Neck and expressive face.
        p.color = Color.rgb(231, 170, 132)
        c.drawRoundRect(x - 5f*s, y - 55f*s + bob, x + 5f*s, y - 45f*s + bob, 3f*s, 3f*s, p)
        p.color = Color.rgb(244, 194, 157)
        c.drawOval(x - 17f*s, y - 76f*s + bob, x + 17f*s, y - 43f*s + bob, p)
        // Ears.
        c.drawCircle(x - 16f*s, y - 59f*s + bob, 4f*s, p)
        c.drawCircle(x + 16f*s, y - 59f*s + bob, 4f*s, p)
        // Layered dark hairstyle with distinct spikes.
        p.color = Color.rgb(27, 24, 29)
        c.drawOval(x - 18f*s, y - 81f*s + bob, x + 18f*s, y - 55f*s + bob, p)
        val hair = Path().apply {
            moveTo(x - 17f*s, y - 68f*s + bob)
            lineTo(x - 20f*s, y - 82f*s + bob)
            lineTo(x - 9f*s, y - 77f*s + bob)
            lineTo(x - 4f*s, y - 87f*s + bob)
            lineTo(x + 3f*s, y - 78f*s + bob)
            lineTo(x + 13f*s, y - 84f*s + bob)
            lineTo(x + 17f*s, y - 68f*s + bob)
            close()
        }
        c.drawPath(hair, p)
        p.color = Color.rgb(67, 49, 43)
        c.drawLine(x - 11f*s, y - 77f*s + bob, x - 5f*s, y - 80f*s + bob, p)
        c.drawLine(x + 2f*s, y - 78f*s + bob, x + 10f*s, y - 81f*s + bob, p)
        // Eyes, brows and smile.
        p.color = Color.rgb(48, 35, 31)
        c.drawOval(x - 10f*s, y - 63f*s + bob, x - 5f*s, y - 57f*s + bob, p)
        c.drawOval(x + 5f*s, y - 63f*s + bob, x + 10f*s, y - 57f*s + bob, p)
        p.color = Color.WHITE
        c.drawCircle(x - 7f*s, y - 62f*s + bob, 1.2f*s, p)
        c.drawCircle(x + 8f*s, y - 62f*s + bob, 1.2f*s, p)
        p.color = Color.rgb(160, 83, 75)
        p.strokeWidth = 1.2f*s
        c.drawLine(x - 3f*s, y - 52f*s + bob, x + 3f*s, y - 52f*s + bob, p)
        // Floating player nameplate.
        p.color = Color.argb(220, 18, 23, 39)
        c.drawRoundRect(x - 32f*s, y - 104f*s + bob, x + 32f*s, y - 90f*s + bob, 6f*s, 6f*s, p)
        p.color = Color.rgb(255, 218, 126)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 8f*s
        c.drawText("NEXORA", x, y - 95f*s + bob, p)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(220, 14, 23, 39)
        c.drawRoundRect(16f, 14f, w * .46f, h * .18f, 18f, 18f, p)
        p.color = Color.WHITE; p.textAlign = Paint.Align.LEFT; p.textSize = h * .035f
        c.drawText("NEXORA  /  CITY PLAZA", 30f, h * .065f, p)
        p.color = Color.rgb(255, 211, 102); p.textSize = h * .032f
        c.drawText("◆ $coins", 30f, h * .125f, p)
        p.color = Color.rgb(105, 220, 244)
        c.drawText("LEVEL $level  •  $xp XP", w * .20f, h * .125f, p)
        p.color = Color.argb(218, 14, 23, 39)
        c.drawRoundRect(w * .64f, 14f, w - 16f, h * .18f, 16f, 16f, p)
        p.color = Color.WHITE; p.textSize = h * .029f
        c.drawText(if (questComplete) "PLAZA EXPLORER ✓" else "DAILY QUEST", w * .66f, h * .065f, p)
        p.color = Color.rgb(255, 211, 102); p.textSize = h * .026f
        c.drawText(if (questComplete) "Reward claimed: 250 coins" else "Collect crystals  $crystals / 5", w * .66f, h * .12f, p)
        if (!questComplete) {
            p.color = Color.rgb(58, 72, 88)
            c.drawRoundRect(w * .66f, h * .14f, w * .94f, h * .155f, 8f, 8f, p)
            p.color = Color.rgb(65, 211, 190)
            c.drawRoundRect(w * .66f, h * .14f, w * (.66f + .28f * min(1f, crystals / 5f)), h * .155f, 8f, 8f, p)
        }
        if (toastTimer > 0f) {
            p.color = Color.argb(225, 13, 22, 38)
            c.drawRoundRect(w * .27f, h * .205f, w * .73f, h * .27f, 14f, 14f, p)
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.textSize = h * .027f
            c.drawText(toast, w * .5f, h * .245f, p)
        }
        if (speedBoost > 0f) {
            p.color = Color.rgb(255, 221, 111); p.textAlign = Paint.Align.CENTER; p.textSize = h * .025f
            c.drawText("SPEED BOOST  ${speedBoost.toInt()}s", w * .5f, h * .30f, p)
        }
        if (paused) {
            p.color = Color.argb(175, 5, 10, 20); c.drawRect(0f, 0f, w, h, p)
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.textSize = h * .08f
            c.drawText("PAUSED", w * .5f, h * .5f, p)
            p.textSize = h * .03f; c.drawText("Tap the pause icon to continue", w * .5f, h * .56f, p)
        }
    }

    private fun drawControls(c: Canvas, w: Float, h: Float) {
        val cx = w * .13f
        val cy = h * .82f
        val radius = h * .105f
        p.color = Color.argb(95, 12, 22, 38); c.drawCircle(cx, cy, radius, p)
        p.color = Color.argb(125, 220, 239, 246); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        c.drawCircle(cx, cy, radius, p); p.style = Paint.Style.FILL
        val knobX = cx + joystick[0] * radius * .55f
        val knobY = cy + joystick[1] * radius * .55f
        p.color = Color.argb(205, 228, 245, 250); c.drawCircle(knobX, knobY, radius * .38f, p)
        p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.textSize = h * .022f
        c.drawText("MOVE", cx, cy + radius + h * .035f, p)
        val ax = w * .87f
        val ay = h * .82f
        p.color = Color.argb(170, 28, 89, 111); c.drawCircle(ax, ay, h * .075f, p)
        p.color = Color.rgb(125, 230, 235); p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        c.drawCircle(ax, ay, h * .075f, p); p.style = Paint.Style.FILL
        p.color = Color.WHITE; p.textSize = h * .025f
        c.drawText("ACT", ax, ay + h * .009f, p)
        p.textSize = h * .019f; c.drawText("INTERACT", ax, ay + h * .105f, p)
        p.color = Color.argb(160, 14, 23, 39); c.drawCircle(w * .95f, h * .09f, h * .045f, p)
        p.color = Color.WHITE; p.textSize = h * .035f
        c.drawText(if (paused) "▶" else "Ⅱ", w * .95f, h * .103f, p)
    }

    private fun interact(w: Float, h: Float) {
        val fountainDx = (avatar[0] - .5f) * w
        val fountainDy = (avatar[1] - .67f) * h
        val npcDx = (avatar[0] - npcX) * w
        val npcDy = (avatar[1] - npcY) * h
        val fountainDistance = sqrt(fountainDx * fountainDx + fountainDy * fountainDy)
        val npcDistance = sqrt(npcDx * npcDx + npcDy * npcDy)
        if (fountainDistance < min(w, h) * .17f) {
            speedBoost = 8f
            say("Fountain blessing! Speed boost for 8 seconds")
        } else if (npcDistance < min(w, h) * .16f) {
            if (!npcRewarded) {
                npcRewarded = true
                coins += 40
                xp += 20
                updateLevel()
                say("MILO: Welcome to Nexora! +40 coins")
            } else say("MILO: Keep exploring! The plaza is full of secrets.")
        } else say("Move closer to Milo or the fountain to interact.")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val dx = event.x - w * .13f
        val dy = event.y - h * .82f
        val inJoystick = sqrt(dx * dx + dy * dy) < h * .17f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x > w * .90f && event.y < h * .17f) {
                    paused = !paused
                    say(if (paused) "Game paused" else "Welcome back!")
                    return true
                }
                if (event.x > w * .77f && event.y > h * .68f) {
                    interact(w, h)
                    return true
                }
                if (inJoystick || event.x < w * .34f && event.y > h * .60f) {
                    joystickActive = true
                    updateJoystick(event.x, event.y, w, h)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (joystickActive) updateJoystick(event.x, event.y, w, h)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                joystickActive = false
                joystick[0] = 0f; joystick[1] = 0f
                performClick()
                return true
            }
        }
        return true
    }

    private fun updateJoystick(x: Float, y: Float, w: Float, h: Float) {
        val radius = h * .105f
        val dx = (x - w * .13f) / radius
        val dy = (y - h * .82f) / radius
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
        val scale = if (length > 1f) 1f / length else 1f
        joystick[0] = dx * scale
        joystick[1] = dy * scale
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
