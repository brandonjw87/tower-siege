package com.brandonwallace.towersiege

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.brandonwallace.towersiege.game.Profile
import com.brandonwallace.towersiege.game.SAVE_PREFS
import com.brandonwallace.towersiege.ui.Audio
import com.brandonwallace.towersiege.ui.TowerSiegeApp

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences(SAVE_PREFS, Context.MODE_PRIVATE)
    fun load(): Profile = Profile.decode(prefs.getString("profile", null))
    fun save(p: Profile) { prefs.edit().putString("profile", p.encode()).apply() }
}

class MainActivity : ComponentActivity() {
    private lateinit var audio: Audio
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val store = Store(this)
        audio = Audio()
        setContent { TowerSiegeApp(store, audio) }
    }
    override fun onPause() { super.onPause(); audio.pauseMusic() }
    override fun onResume() { super.onResume(); audio.resumeMusic() }
    override fun onDestroy() { super.onDestroy(); audio.release() }
}
