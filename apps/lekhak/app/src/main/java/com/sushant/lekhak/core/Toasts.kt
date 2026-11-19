package com.sushant.lekhak.core

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Toasts always posted on the main thread. */
fun Context.toast(msg: String, long: Boolean = false) {
    val app = applicationContext
    ContextCompat.getMainExecutor(app).execute {
        Toast.makeText(app, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
