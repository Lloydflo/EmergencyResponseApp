package com.ers.emergencyresponseapp.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppNavigationScreen(
    destLat: Double,
    destLng: Double,
    destLabel: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val dest = remember(destLat, destLng) { LatLng(destLat, destLng) }

    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }

    // Handle MapView lifecycle
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { googleMap: GoogleMap ->
                    Log.d("MapDebug", "GoogleMap ready")
                    googleMap.uiSettings.isZoomControlsEnabled = true
                    googleMap.uiSettings.isMyLocationButtonEnabled = true

                    googleMap.clear()
                    googleMap.addMarker(MarkerOptions().position(dest).title(destLabel))
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))

                    try {
                        googleMap.isMyLocationEnabled = true
                        fused.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                val me = LatLng(loc.latitude, loc.longitude)
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15f))
                            }
                        }
                    } catch (_: SecurityException) {
                        // location permission not granted
                    }
                }
            }
        )
    }
}