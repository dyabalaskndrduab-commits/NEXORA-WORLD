package com.nexora.world

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            4 or 1024 or 4096 or 512 or 2
        )

        val root = FrameLayout(this)
        val world = NexoraWorldView(this)
        root.addView(world, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xCC101827.toInt())
        }
        val title = TextView(this).apply {
            text = "NEXORA  •  CITY PLAZA"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val coins = TextView(this).apply {
            text = "   ◆  1,250"
            textSize = 15f
            setTextColor(0xFFFFD166.toInt())
        }
        top.addView(title)
        top.addView(coins)
        root.addView(top, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC101827.toInt())
        }
        listOf("PROFILE", "FRIENDS", "ROOMS", "SHOP").forEach { label ->
            actions.addView(Button(this).apply {
                text = label
                textSize = 10f
                setOnClickListener { world.showNotice("$label  •  coming in the next milestone") }
            })
        }
        root.addView(actions, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        setContentView(root)
    }
}
