package com.gotify.client

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
