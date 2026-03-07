package com.ers.emergencyresponseapp.routing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class RouteUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RouteMonitoringService.ACTION_REROUTE) return

        val lat = intent.getDoubleExtra(RouteMonitoringService.EXTRA_REROUTE_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(RouteMonitoringService.EXTRA_REROUTE_LNG, Double.NaN)
        val label = intent.getStringExtra(RouteMonitoringService.EXTRA_REROUTE_LABEL) ?: "Incident"

        if (lat.isNaN() || lng.isNaN()) return

        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val canOpen = mapIntent.resolveActivity(context.packageManager) != null
        if (canOpen) {
            context.startActivity(mapIntent)
        } else {
            val fallback = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallback.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallback)
            }
        }
    }
}