package com.sushant.grabbit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Asks Android to stop putting Grabbit's downloads to sleep (asked once, from Settings or the setup guide). */
object BatteryAccess {
    fun allowed(ctx: Context): Boolean =
        ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    @SuppressLint("BatteryLife")
    fun request(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { ctx.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { ctx.startActivity(list.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
