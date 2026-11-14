package com.killswitch.sheer

import android.app.Application

/** Holds the open tabs for the whole process, so rebuilding the screen never closes a page. */
class SheerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    val browser: Browser by lazy { Browser(this) }
}
