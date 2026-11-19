package com.sushant.speedtile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that listens to the GPS receiver while speed is on.
 * Location is only kept in memory to work out speed; nothing is stored or sent anywhere.
 */
class SpeedService : Service() {

    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var stateJob: Job? = null
    private var lastNotifText: String? = null

    private val listener = object : LocationListener {
        private var count = 0
        override fun onLocationChanged(loc: Location) {
            if (count++ % 10 == 0) Log.i(TAG, "GPS fix #$count speed=${if (loc.hasSpeed()) loc.speed * 3.6f else -1f} acc=${loc.accuracy}")
            app.engine.onFix(loc.toFix())
        }
        override fun onProviderDisabled(provider: String) { Log.i(TAG, "GPS turned off"); app.engine.onGpsDisabled() }
        override fun onProviderEnabled(provider: String) { app.engine.onGpsEnabled() }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private var loggedSats = false

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            if (!loggedSats && status.satelliteCount > 0) {
                loggedSats = true
                Log.i(TAG, "GNSS status: ${status.satelliteCount} satellites seen")
            }
            app.engine.onSatellites(used, status.satelliteCount)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val engine = app.engine
            engine.onTick()
            if (SpeedMath.isIdleTooLong(System.currentTimeMillis(), engine.lastMovingAt, app.prefs.autoStopMin.value)) {
                stopNow(StopReason.IDLE)
                return
            }
            handler.postDelayed(this, 2_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service start command")
        if (intent?.action == ACTION_STOP) {
            stopNow(StopReason.USER)
            return START_NOT_STICKY
        }
        if (!hasLocationPermission(this)) {
            app.engine.onStopped(StopReason.NO_PERMISSION)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(app.engine.state.value),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            app.engine.onStopped(StopReason.BLOCKED)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) startListening()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        listening = true
        pendingReason = StopReason.USER
        app.engine.onStarted()
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) app.engine.onGpsDisabled()
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            Log.i(TAG, "GPS updates requested (sdk ${Build.VERSION.SDK_INT})")
            lm.registerGnssStatusCallback(gnss, handler)
        } catch (e: SecurityException) {
            stopNow(StopReason.NO_PERMISSION)
            return
        } catch (e: IllegalArgumentException) {
            // Device without a GPS receiver
            stopNow(StopReason.GPS_OFF)
            return
        }
        handler.postDelayed(ticker, 2_000L)
        stateJob = app.engine.state.onEach { updateNotification(it) }.launchIn(app.appScope)
    }

    private fun stopNow(reason: StopReason) {
        cleanup()
        app.engine.onStopped(reason)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        if (listening) {
            runCatching { lm.removeUpdates(listener) }
            runCatching { lm.unregisterGnssStatusCallback(gnss) }
        }
        listening = false
        handler.removeCallbacks(ticker)
        stateJob?.cancel()
        stateJob = null
    }

    override fun onDestroy() {
        val wasListening = listening
        cleanup()
        if (wasListening) app.engine.onStopped(pendingReason)
        pendingReason = StopReason.USER
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(s: SpeedState) {
        if (!s.running) return
        val n = buildNotification(s)
        val key = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString() + "|" +
            n.extras.getCharSequence(Notification.EXTRA_TEXT)
        if (key == lastNotifText) return
        lastNotifText = key
        // Shown where the system allows it (Android 13+ hides it unless notifications are allowed in Settings).
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) }
    }

    private fun buildNotification(s: SpeedState): Notification {
        val decimals = app.prefs.decimals.value
        val title = when (s.status) {
            Status.LIVE -> "${SpeedMath.formatSpeed(s.speedKmh, decimals)} km/h"
            Status.WEAK -> "Weak GPS signal"
            Status.GPS_OFF -> "Location is off"
            else -> "Finding GPS signal…"
        }
        val text = "Max ${SpeedMath.formatSpeed(s.trip.maxKmh, decimals)} km/h · ${SpeedMath.formatDistance(s.trip.distanceM)}"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SpeedService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "live_speed"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "com.sushant.speedtile.STOP"
        const val TAG = "SpeedTile"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpeedService::class.java))
        }

        /** Why the next stop happens (read in onDestroy). */
        @Volatile var pendingReason: StopReason = StopReason.USER

        fun stop(context: Context, reason: StopReason = StopReason.USER) {
            pendingReason = reason
            context.stopService(Intent(context, SpeedService::class.java))
        }

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            ch.description = context.getString(R.string.channel_desc)
            ch.setShowBadge(false)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** "Allow all the time": lets the tile start GPS without the hand-off screen. Not separate before Android 10. */
fun hasBackgroundLocation(context: Context): Boolean =
    hasLocationPermission(context) && (Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

fun isGpsOn(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
}

fun Location.toFix(): Fix = Fix(
    lat = latitude,
    lon = longitude,
    timeMs = time,
    speedMps = if (hasSpeed()) speed else null,
    accuracyM = if (hasAccuracy()) accuracy else null,
    speedAccMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
)
