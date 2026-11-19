package com.sushant.gymmanager.util

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Shows a toast from any thread. */
fun Context.toast(message: String, long: Boolean = false) {
    val app = applicationContext
    ContextCompat.getMainExecutor(app).execute {
        Toast.makeText(app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
