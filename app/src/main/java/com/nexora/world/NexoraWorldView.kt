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
        c.drawColor(Color.rgb(112, 170, 193))
        p.style = Paint.Style.FILL
        p.color = Color.rgb(89, 151, 145)
        c.drawRect(0f, h * .53f, w, h, p)
        p.color = Color.argb(105, 255, 255, 255)
        for (i in 0..3) {
            val x = w * (.12f + i * .27f)
            val y = h * (.13f + (i % 2) * .07f)
            c.drawCircle(x, y, h * .018f, p)
            c.drawCircle(x + h * .025f, y - h * .008f, h * .023f, p)
            c.drawCircle(x + h * .05f, y, h * .017f, p)
        }
        for (i in 0..8) {
            val bw = w * .075f
            val x = i * w * .13f
            val top = h * (.25f + (i % 3) * .055f)
            p.color = if (i % 2 == 0) Color.rgb(66, 91, 120) else Color.rgb(80, 107, 133)
            c.drawRect(x, top, x + bw, h * .62f, p)
            p.color = Color.rgb(226, 210, 163)
            for (row in 0..3) for (col in 0..1) {
                val wx = x + bw * (.22f + col * .42f)
                val wy = top + h * .035f + row * h * .065f
                c.drawRoundRect(wx, wy, wx + bw * .13f, wy + h * .025f, 3f, 3f, p)
            }
        }
        val plaza = Path().apply {
            moveTo(w * .5f, h * .37f); lineTo(w * .96f, h * .77f)
            lineTo(w * .5f, h * 1.02f); lineTo(w * .04f, h * .77f); close()
        }
        p.color = Color.rgb(205, 192, 163)
        c.drawPath(plaza, p)
        p.color = Color.rgb(226, 215, 190)
        p.strokeWidth = 2f
        for (i in 1..6) {
            val t = i / 7f
            c.drawLine(w * (.5f - .45f * t), h * (.37f + .40f * t),
                w * (.5f + .45f * t), h * (.37f + .40f * t), p)
        }
        drawBuilding(c, w * .11f, h * .34f, w * .22f, h * .30f, Color.rgb(73, 105, 139))
        drawBuilding(c, w * .68f, h * .29f, w * .20f, h * .35f, Color.rgb(132, 89, 121))
        drawBuilding(c, w * .39f, h * .27f, w * .14f, h * .21f, Color.rgb(83, 126, 139))
        drawTree(c, w * .20f, h * .70f)
        drawTree(c, w * .82f, h * .72f)
        drawTree(c, w * .36f, h * .84f)
        drawFountain(c, w * .5f, h * .67f)
        crystalsInWorld.filter { it.active }.forEach { drawCrystal(c, w * it.x, h * it.y, h) }
        drawNpc(c, w * npcX, h * npcY, h)
        drawAvatar(c, w * avatar[0], h * avatar[1], h)
        drawHud(c, w, h)
        drawControls(c, w, h)
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

    private fun drawAvatar(c: Canvas, x: Float, y: Float, h: Float) {
        val moving = sqrt(joystick[0] * joystick[0] + joystick[1] * joystick[1]) > .08f
        val bob = if (moving) sin(walkPhase) * 3f else 0f
        p.color = Color.argb(55, 0, 0, 0); c.drawOval(x - 20, y + 24, x + 20, y + 34, p)
        p.color = Color.rgb(39, 50, 72)
        c.drawRoundRect(x - 17, y - 4 + bob, x + 17, y + 29 + bob, 12f, 12f, p)
        p.color = Color.rgb(242, 190, 151); c.drawCircle(x, y - 20 + bob, 15f, p)
        p.color = Color.rgb(48, 38, 35); c.drawArc(x - 15, y - 36 + bob, x + 15, y - 9 + bob, 180f, 180f, true, p)
        p.color = Color.WHITE; c.drawCircle(x + facing * 5f, y - 21 + bob, 2f, p)
        p.color = Color.rgb(72, 197, 224); c.drawRoundRect(x - 18, y + 6 + bob, x + 18, y + 12 + bob, 4f, 4f, p)
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
