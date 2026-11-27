package com.sushant.grabbit

import android.app.Application
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Library
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.Notifs
import com.sushant.grabbit.engine.Engine

class GrabbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Cookies.init(this)
        Library.init(this)
        com.sushant.grabbit.data.Paused.init(this)
        com.sushant.grabbit.data.Folders.init(this)
        Notifs.createChannels(this)
        Engine.start(this)
    }
}
