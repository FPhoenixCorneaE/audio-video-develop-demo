package com.fphoenixcorneae.audio.ui

import android.app.Application
import kotlin.properties.Delegates

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        sInstance = this
    }

    companion object {
        private var sInstance: Application by Delegates.notNull()
        fun getInstance() = sInstance
    }
}