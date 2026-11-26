package com.aura.player

import android.app.Application
import com.aura.player.playback.AudioEngine

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioEngine.init(this)
    }
}
