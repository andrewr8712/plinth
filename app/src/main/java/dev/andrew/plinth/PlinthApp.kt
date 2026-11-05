package dev.andrew.plinth

import android.app.Application

class PlinthApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playbackService.start()
    }
}
