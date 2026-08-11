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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.ers.emergencyresponseapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.SaveRoutePointRequest

class RouteMonitoringService : Service() {

    companion object {

        var isRunning = false

        const val ACTION_REROUTE = "com.ers.emergencyresponseapp.action.REROUTE"
        const val ACTION_OPEN_ACTIVE_ROUTE =
            "com.ers.emergencyresponseapp.action.OPEN_ACTIVE_ROUTE"

        const val EXTRA_REROUTE_LAT = "extra_reroute_lat"
        const val EXTRA_REROUTE_LNG = "extra_reroute_lng"
        const val EXTRA_REROUTE_LABEL = "extra_reroute_label"

        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val EXTRA_ASSIGNMENT_ID = "extra_assignment_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
        const val EXTRA_DEST_ADDRESS = "extra_dest_address"

        const val ACTION_OPEN_UPDATED_ROUTE = "action_open_updated_route"

        private const val CHANNEL_ID = "route_updates_channel"
        private const val FOREGROUND_ID = 4101
        private const val MAX_LOCATION_FIX_AGE_MS = 15_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationCallback: LocationCallback
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var lastSavedLat: Double? = null
    private var lastSavedLng: Double? = null
    private var lastSavedAt: Long = 0L
    private var trackedIncidentId: String? = null
    private var trackedAssignmentId: String? = null
    private var trackedResponderId: String? = null

    private val minDistanceMeters = 5f
    private val minSaveIntervalMs = 5000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        android.util.Log.d("LiveGPS", "onStartCommand called")

        android.os.Handler(mainLooper).post {
            android.widget.Toast.makeText(this, "Service started", android.widget.Toast.LENGTH_SHORT).show()
        }
        // ✅ Extract everything safely BEFORE coroutines
        val incidentId = intent?.getStringExtra(EXTRA_INCIDENT_ID).orEmpty()
        val assignmentId = intent?.getStringExtra(EXTRA_ASSIGNMENT_ID).orEmpty()
        val destinationLat = intent?.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
            ?: Double.NaN
        val destinationLng = intent?.getDoubleExtra(EXTRA_DEST_LNG, Double.NaN)
            ?: Double.NaN
        android.util.Log.d(
            "LiveGPS",
            "incidentId=$incidentId assignmentId=$assignmentId"
        )

        if (incidentId.isBlank() || !isValidCoordinate(destinationLat, destinationLng)) {
            android.util.Log.e(
                "LiveGPS",
                "Incident identity or destination is invalid. Stopping service."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            android.util.Log.e("LiveGPS", "Location permission is missing. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification(intent?.getStringExtra(EXTRA_DEST_ADDRESS))
            )
        } catch (error: Exception) {
            android.util.Log.e("LiveGPS", "Unable to promote location foreground service", error)
            stopSelf()
            return START_NOT_STICKY
        }

        // onStartCommand can be called again while the service is alive. Always
        // replace the old callback so a reassigned unit never keeps publishing
        // coordinates against the previous incident.
        val assignmentChanged = trackedIncidentId != incidentId ||
                trackedAssignmentId != assignmentId
        if (assignmentChanged && ::locationCallback.isInitialized) {
            fusedClient.removeLocationUpdates(locationCallback)
            lastSavedLat = null
            lastSavedLng = null
            lastSavedAt = 0L
        }
        if (assignmentChanged || !::locationCallback.isInitialized) {
            trackedIncidentId = incidentId
            trackedAssignmentId = assignmentId
            startLiveLocationTracking(incidentId, assignmentId)
        }

        return START_REDELIVER_INTENT
    }


    private fun startLiveLocationTracking(
        incidentId: String,
        assignmentId: String
    ) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            stopSelf()
            return
        }

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val responderId = prefs.getString("user_id", "") ?: ""
        android.util.Log.d(
            "LiveGPS",
            "responderId=$responderId incidentId=$incidentId"
        )
        val responderName =
            prefs.getString("full_name", "")
                ?: prefs.getString("account_username", "")
                ?: "Responder"
        val department = prefs.getString("department", "") ?: ""
        val unitCode = prefs.getString("unit_code", "") ?: ""
        val unitType = prefs.getString("unit_type", "") ?: ""

        if (responderId.isBlank()) {
            stopSelf()
            return
        }
        trackedResponderId = responderId

        val dbRef = FirebaseDatabase
            .getInstance()
            .getReference("live_locations")
            .child("responder_$responderId")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val fixAgeMillis = (
                    SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                ).coerceAtLeast(0L) / 1_000_000L
                if (
                    !isValidCoordinate(location.latitude, location.longitude) ||
                    fixAgeMillis > MAX_LOCATION_FIX_AGE_MS
                ) {
                    android.util.Log.w(
                        "LiveGPS",
                        "Rejected invalid or stale location fix (ageMs=$fixAgeMillis)"
                    )
                    return
                }
                val now = SystemClock.elapsedRealtime()

                val movedEnough = if (lastSavedLat != null && lastSavedLng != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        lastSavedLat!!,
                        lastSavedLng!!,
                        location.latitude,
                        location.longitude,
                        results
                    )
                    results[0] >= minDistanceMeters
                } else {
                    true
                }

                val intervalPassed = now - lastSavedAt >= minSaveIntervalMs

                if (!intervalPassed) {
                    android.util.Log.d(
                        "LiveGPS",
                        "Skipped location update: heartbeat interval has not elapsed"
                    )
                    return
                }

                lastSavedAt = now
                if (movedEnough) {
                    lastSavedLat = location.latitude
                    lastSavedLng = location.longitude
                }

                val data = mapOf(
                    "responderId" to responderId,
                    "responderName" to responderName,
                    "department" to department,
                    "unitCode" to unitCode,
                    "unitType" to unitType,
                    "incidentId" to incidentId,
                    "assignmentId" to assignmentId,
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "speed" to location.speed,
                    "heading" to location.bearing,
                    "accuracy" to location.accuracy
                        .takeIf { location.hasAccuracy() && it.isFinite() && it > 0f },
                    "status" to "en_route",
                    "updatedAt" to System.currentTimeMillis()
                )

                android.util.Log.d(
                    "LiveGPS",
                    "Uploading location: ${location.latitude}, ${location.longitude}"
                )

                dbRef.updateChildren(data)
                    .addOnSuccessListener {
                        android.util.Log.d("LiveGPS", "Firebase location uploaded")
                    }
                    .addOnFailureListener {
                        android.util.Log.e("LiveGPS", "Firebase upload failed: ${it.message}")
                    }

                // Firebase receives a heartbeat even while stationary. The SQL
                // route history still records only meaningful movement.
                if (movedEnough) serviceScope.launch {
                    try {
                        val numericIncidentId = incidentId.toIntOrNull()
                        val numericResponderId = responderId.toIntOrNull()
                        if (numericIncidentId == null || numericResponderId == null) {
                            android.util.Log.e(
                                "LiveGPS",
                                "Skipped route history save because an operational ID is invalid"
                            )
                            return@launch
                        }
                        val response = RetrofitProvider.incidentApi.saveRoutePoint(
                            SaveRoutePointRequest(
                                incident_id = numericIncidentId,
                                assignment_id = assignmentId.toIntOrNull(),
                                responder_id = numericResponderId,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                speed = location.speed,
                                heading = location.bearing,
                                status = "en_route"
                            )
                        )

                        android.util.Log.d(
                            "LiveGPS",
                            "MySQL route save: success=${response.success}, message=${response.message}"
                        )

                    } catch (e: Exception) {
                        android.util.Log.e("LiveGPS", "MySQL save failed: ${e.message}")
                    }
                }
            }
        }

        val request = LocationRequest.Builder(5000L)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(3000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
            android.util.Log.d("LiveGPS", "Location updates requested")
        } catch (error: SecurityException) {
            android.util.Log.e("LiveGPS", "Location permission was revoked", error)
            stopSelf()
        }
    }

    private fun buildForegroundNotification(destinationAddress: String?): Notification {
        val openAppIntent = Intent(this, com.ers.emergencyresponseapp.MainActivity::class.java).apply {
            action = ACTION_OPEN_ACTIVE_ROUTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setContentTitle("Live responder tracking active")
            .setContentText(
                destinationAddress?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { "Navigating to $it" }
                    ?: "Tap to return to the incident route"
            )
            .setContentIntent(openAppPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
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
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setContentTitle("ERS: Route Updated")
            .setContentText("Tap to reroute in Google Maps")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending) // tap notif = reroute agad
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }

    override fun onDestroy() {
        isRunning = false

        if (::locationCallback.isInitialized) {
            fusedClient.removeLocationUpdates(locationCallback)
        }

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val responderId = trackedResponderId
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_id", "").orEmpty()

        if (responderId.isNotBlank()) {
            FirebaseDatabase.getInstance()
                .getReference("live_locations")
                .child("responder_$responderId")
                .removeValue()
        }

        serviceScope.cancel()
        super.onDestroy()
    }
}
