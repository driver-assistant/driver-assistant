package io.github.driverassistant

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Window
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_main_screen.*

class MainScreenActivity : AppCompatActivity() {

    private lateinit var textureViewUpdater: TextureViewUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main_screen)

        textureViewUpdater = TextureViewUpdater(cameraTextureView, this)
    }

    override fun onResume() {
        super.onResume()

        textureViewUpdater.onResume()
    }

    override fun onPause() {
        textureViewUpdater.onPause()

        super.onPause()
    }
}
