package com.meshlink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meshlink.app.MainActivity
import com.meshlink.app.R
import com.meshlink.app.domain.repository.NearbyRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that keeps Nearby Connections advertising + discovery alive.
 *
 * Phase 5 battery optimizations:
 *  • Registers [BatteryReceiver] — pauses scanning when battery level drops below 10%,
 *    resumes when the device is plugged in or battery recovers above the threshold.
 *  • Registers [ScreenReceiver] — resumes fast scanning whenever the screen turns ON so
 *    the user sees fresh results when they open the phone.
 *  • [AdaptiveScanController] inside [NearbyRepositoryImpl] further ratchets the restart
 *    delay up to 30 s when no devices have been found for 60 s (no changes needed here).
 */
@AndroidEntryPoint
class NearbyService : Service() {

    @Inject lateinit var nearbyRepository: NearbyRepository

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,  -1)
                    val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE,  100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val percent = if (scale > 0) level * 100 / scale else 100
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == BatteryManager.BATTERY_STATUS_FULL

                    if (percent < BATTERY_PAUSE_THRESHOLD && !isCharging) {
                        Timber.w("Battery low ($percent%) — pausing scanning")
                        nearbyRepository.pauseScanning()
                        updateNotification("Battery low — discovery paused")
                    } else if (isCharging) {
                        Timber.i("Charging — resuming scanning")
                        nearbyRepository.resumeScanning()
                        updateNotification("Looking for people nearby…")
                    }
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                Timber.d("Screen ON — reset to fast scan")
                nearbyRepository.resumeScanning()
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Timber.d("NearbyService created")
        createNotificationChannel()
        startForegroundCompat()
        nearbyRepository.startAdvertisingAndDiscovery()
        registerReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        safeUnregisterReceiver(batteryReceiver)
        safeUnregisterReceiver(screenReceiver)
        nearbyRepository.stopAdvertisingAndDiscovery()
        Timber.d("NearbyService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerReceivers() {
        // Android 14+ (API 34) requires RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED flag.
        // These are system broadcasts so RECEIVER_NOT_EXPORTED is correct.
        ContextCompat.registerReceiver(
            this, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun safeUnregisterReceiver(receiver: BroadcastReceiver) {
        try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, buildNotification("Looking for people nearby…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Looking for people nearby…"))
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MeshLink Connection", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MeshLink is looking for people nearby"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshLink")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID          = 1001
        private const val CHANNEL_ID               = "meshlink_nearby_service"
        private const val BATTERY_PAUSE_THRESHOLD  = 10     // percent

        fun start(context: Context) {
            val intent = Intent(context, NearbyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) = context.stopService(Intent(context, NearbyService::class.java))
    }
}
