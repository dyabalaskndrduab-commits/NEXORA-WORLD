package com.nexora.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class NexoraWorldView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var avatarX = 0.5f
    private var avatarY = 0.66f
    private var notice = "Welcome to Nexora City"
    private var downX = 0f
    private var downY = 0f

    fun showNotice(message: String) {
        notice = message
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(125, 184, 205))

        paint.color = Color.rgb(93, 158, 153)
        canvas.drawRect(0f, h * .55f, w, h, paint)

        // Distant skyline
        for (i in 0..7) {
            val bw = w * .075f
            val x = i * w * .14f
            paint.color = if (i % 2 == 0) Color.rgb(66, 91, 120) else Color.rgb(80, 107, 133)
            canvas.drawRect(x, h * (.24f + (i % 3) * .06f), x + bw, h * .62f, paint)
            paint.color = Color.rgb(220, 211, 174)
            for (row in 0..3) for (col in 0..1) {
                canvas.drawRect(x + 10 + col * 22, h * (.29f + (i % 3) * .06f) + row * 27,
                    x + 22 + col * 22, h * (.305f + (i % 3) * .06f) + row * 27, paint)
            }
        }

        // Plaza floor in a pseudo-isometric perspective
        val plaza = Path().apply {
            moveTo(w * .5f, h * .38f); lineTo(w * .94f, h * .76f)
            lineTo(w * .5f, h * .98f); lineTo(w * .06f, h * .76f); close()
        }
        paint.color = Color.rgb(204, 190, 158)
        canvas.drawPath(plaza, paint)

        // Paths and plaza tiles
        paint.color = Color.rgb(229, 218, 190)
        paint.strokeWidth = 3f
        for (i in 1..5) {
            val t = i / 6f
            canvas.drawLine(w * (.5f - .44f * t), h * (.38f + .38f * t),
                w * (.5f + .44f * t), h * (.38f + .38f * t), paint)
        }

        drawBuilding(canvas, w * .12f, h * .34f, w * .22f, h * .30f, Color.rgb(73, 105, 139))
        drawBuilding(canvas, w * .68f, h * .29f, w * .20f, h * .35f, Color.rgb(132, 89, 121))
        drawTree(canvas, w * .23f, h * .70f)
        drawTree(canvas, w * .79f, h * .70f)
        drawFountain(canvas, w * .5f, h * .67f)
        drawAvatar(canvas, w * avatarX, h * avatarY)

        paint.color = Color.argb(210, 16, 24, 39)
        canvas.drawRoundRect(w * .30f, h * .08f, w * .70f, h * .15f, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(notice, w * .5f, h * .125f, paint)

        paint.color = Color.argb(110, 16, 24, 39)
        canvas.drawCircle(w * .88f, h * .82f, 48f, paint)
        paint.color = Color.WHITE
        paint.textSize = 12f
        canvas.drawText("DRAG", w * .88f, h * .825f, paint)
    }

    private fun drawBuilding(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + bw, y + bh, paint)
        val roof = Path().apply { moveTo(x - 8, y); lineTo(x + bw / 2, y - bh * .22f); lineTo(x + bw + 8, y); close() }
        paint.color = Color.rgb(45, 61, 82); c.drawPath(roof, paint)
        paint.color = Color.rgb(244, 211, 142)
        for (i in 0..2) c.drawRect(x + 14 + i * (bw / 3), y + 24, x + 30 + i * (bw / 3), y + 52, paint)
    }

    private fun drawTree(c: Canvas, x: Float, y: Float) {
        paint.color = Color.rgb(100, 70, 48); c.drawRect(x - 7, y - 35, x + 7, y + 18, paint)
        paint.color = Color.rgb(48, 123, 91); c.drawCircle(x, y - 48, 30f, paint)
        paint.color = Color.rgb(72, 151, 105); c.drawCircle(x - 13, y - 57, 18f, paint)
    }

    private fun drawFountain(c: Canvas, x: Float, y: Float) {
        paint.color = Color.rgb(128, 145, 157); c.drawOval(x - 65, y - 18, x + 65, y + 22, paint)
        paint.color = Color.rgb(82, 177, 207); c.drawOval(x - 48, y - 12, x + 48, y + 13, paint)
        paint.color = Color.rgb(220, 235, 236); c.drawCircle(x, y - 22, 10f, paint)
    }

    private fun drawAvatar(c: Canvas, x: Float, y: Float) {
        paint.color = Color.argb(55, 0, 0, 0); c.drawOval(x - 20, y + 24, x + 20, y + 34, paint)
        paint.color = Color.rgb(39, 50, 72); c.drawRoundRect(x - 17, y - 4, x + 17, y + 29, 12f, 12f, paint)
        paint.color = Color.rgb(242, 190, 151); c.drawCircle(x, y - 20, 15f, paint)
        paint.color = Color.rgb(48, 38, 35); c.drawArc(x - 15, y - 36, x + 15, y - 9, 180f, 180f, true, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_MOVE -> {
                avatarX = min(.94f, max(.06f, avatarX + (event.x - downX) / width))
                avatarY = min(.90f, max(.40f, avatarY + (event.y - downY) / height))
                downX = event.x; downY = event.y; invalidate(); return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
