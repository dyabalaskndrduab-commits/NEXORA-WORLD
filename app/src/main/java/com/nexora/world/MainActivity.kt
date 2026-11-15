package com.nexora.world

import android.app.Activity
import android.os.Bundle
import android.view.View

class MainActivity : Activity() {
    private lateinit var gameView: Nexora3DView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        gameView = Nexora3DView(this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.resumeWorld()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pauseWorld()
        super.onPause()
    }
}
