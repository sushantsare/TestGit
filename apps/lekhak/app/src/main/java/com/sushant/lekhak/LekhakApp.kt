package com.sushant.lekhak

import android.app.Application
import com.sushant.lekhak.core.LekhakServiceManager

class LekhakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LekhakServiceManager.initialize(this)
    }
}
