package com.ers.emergencyresponseapp.routing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ers.emergencyresponseapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RouteMonitoringService : Service() {

    companion object {
        const val ACTION_REROUTE = "com.ers.emergencyresponseapp.action.REROUTE"

        const val EXTRA_REROUTE_LAT = "extra_reroute_lat"
        const val EXTRA_REROUTE_LNG = "extra_reroute_lng"
        const val EXTRA_REROUTE_LABEL = "extra_reroute_label"

        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
        const val EXTRA_DEST_ADDRESS = "extra_dest_address"

        const val ACTION_OPEN_UPDATED_ROUTE = "action_open_updated_route"

        private const val CHANNEL_ID = "route_updates_channel"
        private const val FOREGROUND_ID = 4101
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.os.Handler(mainLooper).post {
            android.widget.Toast.makeText(this, "Service started", android.widget.Toast.LENGTH_SHORT).show()
        }
        // ✅ Extract everything safely BEFORE coroutines
        val incidentId = intent?.getStringExtra(EXTRA_INCIDENT_ID).orEmpty()
        if (incidentId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val destLat = intent?.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN) ?: Double.NaN
        val destLng = intent?.getDoubleExtra(EXTRA_DEST_LNG, Double.NaN) ?: Double.NaN

        // ✅ Start foreground immediately
        startForeground(FOREGROUND_ID, buildForegroundNotification())

        // ✅ Only start loop once
        if (running.compareAndSet(false, true)) {
            serviceScope.launch {
                // TEST: wait 5s then create dummy update + notification
                delay(5000)

                val store = RouteUpdateStore(this@RouteMonitoringService)

                if (!destLat.isNaN() && !destLng.isNaN()) {
                    val dummy = RouteUpdatePayload(
                        version = 1,
                        summary = "TEST: Faster route via waypoint",
                        destLat = destLat,
                        destLng = destLng,
                        waypoints = listOf(
                            Waypoint(14.12345, 120.54321),
                            Waypoint(14.12400, 120.54000)
                        )
                    )

                    store.saveLatest(incidentId, dummy)
                    store.setLastVersion(incidentId, dummy.version)

                    showUpdateNotification(
                        incidentId = incidentId,
                        message = dummy.summary,
                        destLat = dummy.destLat,
                        destLng = dummy.destLng,
                        label = "Incident"
                    )
                } else {
                    showUpdateNotification(
                        incidentId = incidentId,
                        message = "TEST: Route update received (no coords).",
                        destLat = null,
                        destLng = null,
                        label = "Incident"
                    )
                }

                // Placeholder loop (later: API polling)
                while (running.get()) {
                    delay(30_000)
                }
            }
        }

        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, com.ers.emergencyresponseapp.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val openAppPending = PendingIntent.getActivity(
            this,
            9001,
            openAppIntent,
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ERS: Route Update")
            .setContentText("Tap to return to ERS App")
            .setContentIntent(openAppPending) // ✅ tap notif returns to app
            .setOngoing(true)
            .build()
    }

    private fun showUpdateNotification(
        incidentId: String,
        message: String,
        destLat: Double?,
        destLng: Double?,
        label: String?
    ) {
        val actionIntent = Intent(this, RouteUpdateActionReceiver::class.java).apply {
            action = ACTION_REROUTE
            putExtra(EXTRA_INCIDENT_ID, incidentId)
            putExtra(EXTRA_REROUTE_LAT, destLat ?: Double.NaN)
            putExtra(EXTRA_REROUTE_LNG, destLng ?: Double.NaN)
            putExtra(EXTRA_REROUTE_LABEL, label ?: "Incident")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pending = PendingIntent.getBroadcast(
            this,
            incidentId.hashCode(),
            actionIntent,
            flags
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ERS: Route Updated")
            .setContentText("Tap to reroute in Google Maps")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending) // tap notif = reroute agad
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(incidentId.hashCode(), notif)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Route updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}