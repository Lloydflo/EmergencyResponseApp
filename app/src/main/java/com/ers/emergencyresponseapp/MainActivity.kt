package com.ers.emergencyresponseapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ers.emergencyresponseapp.data.HomeDataCache
import com.ers.emergencyresponseapp.map.LiveRouteMapScreen
import com.ers.emergencyresponseapp.network.ConnectivityObserver
import com.ers.emergencyresponseapp.network.ConnectivityStatus
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.notification.NotificationDestination
import com.ers.emergencyresponseapp.notification.NotificationNavigation
import com.ers.emergencyresponseapp.notification.PushTokenManager
import com.ers.emergencyresponseapp.presence.ResponderPresenceManager
import com.ers.emergencyresponseapp.ui.components.ConnectivityStatusBanner
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var presenceHeartbeatJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        AppNotificationManager.createChannels(this)
        NotificationNavigation.publishFromIntent(intent)

        setContent {
            val context = LocalContext.current
            val authPrefs = remember {
                context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            }
            val userPrefs = remember {
                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            }

            LaunchedEffect(Unit) {
                ThemeController.init(context)
            }

            val darkMode = ThemeController.isDarkMode.value
            LaunchedEffect(darkMode) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkMode
                controller.isAppearanceLightNavigationBars = !darkMode
            }

            EmergencyResponseAppTheme(darkTheme = darkMode) {
                val navController = rememberNavController()
                val uiScope = rememberCoroutineScope()
                val notificationDestination by NotificationNavigation.destination.collectAsState()
                val connectivityObserver = remember { ConnectivityObserver(context) }
                val connectivityStatus by connectivityObserver.status.collectAsState()
                var isCoordinationChatOpen by remember { mutableStateOf(false) }

                DisposableEffect(connectivityObserver) {
                    onDispose { connectivityObserver.close() }
                }

                val storedResponderId = userPrefs.getString("user_id", "")
                    ?.toIntOrNull()
                    ?: 0
                val hasValidSession = authPrefs.getBoolean("user_verified", false) &&
                        storedResponderId > 0

                LaunchedEffect(connectivityStatus, storedResponderId) {
                    if (
                        connectivityStatus == ConnectivityStatus.Online &&
                        storedResponderId > 0
                    ) {
                        runCatching {
                            PushTokenManager.registerCurrentToken(
                                context.applicationContext,
                                storedResponderId
                            )
                        }.onFailure { error ->
                            Log.w("CONNECTIVITY", "Push-token sync after reconnect failed", error)
                        }
                        // Re-sync the existing lease without turning an expired
                        // background responder back online. FCM registration remains
                        // active independently of availability.
                        ResponderPresenceManager.onConnectivityRestored(
                            context.applicationContext,
                            storedResponderId
                        )
                    }
                }

                val openCoordination = intent?.getBooleanExtra("open_coordination", false) == true
                val initialDepartment = userPrefs.getString("department", "")
                    .orEmpty()
                    .trim()
                    .lowercase()
                val initialHomeRoute = if (initialDepartment.isBlank()) {
                    "home"
                } else {
                    "home/$initialDepartment"
                }
                val startDestination = when {
                    !hasValidSession -> "entry"
                    openCoordination -> "coordination_portal"
                    else -> initialHomeRoute
                }

                fun clearLocalSession() {
                    val responderIdToClear = userPrefs.getString("user_id", "")
                        ?.toIntOrNull()
                        ?: 0
                    if (responderIdToClear > 0) {
                        HomeDataCache(context).clearForResponder(responderIdToClear)
                    }
                    NotificationNavigation.clear()
                    AppNotificationManager.clearEventHistory(context.applicationContext)
                    authPrefs.edit().clear().apply()
                    userPrefs.edit().clear().apply()
                    context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                }

                fun logoutAndNavigate(destination: String = "login") {
                    val responderId = userPrefs.getString("user_id", "")
                        ?.toIntOrNull()
                        ?: 0

                    uiScope.launch {
                        if (responderId > 0) {
                            markResponderOffline(responderId)
                        }
                        clearLocalSession()
                        navController.navigate(destination) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                // Authentication and operational presence are separate. Leaving
                // the app never clears the verified session or FCM token. The
                // process-level presence policy marks an idle background session
                // offline after one hour and restores it on the next foreground.

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Keep the process-level tracker aligned with actual Compose navigation.
                // This is intentionally driven by the NavController instead of individual
                // screens so leaving Home/Coordination can never suppress a later FCM alert.
                LaunchedEffect(currentRoute) {
                    AppScreenTracker.currentScreen = when {
                        currentRoute == null -> "NONE"
                        currentRoute == "coordination_portal" -> "COORDINATION"
                        currentRoute == "reviews_feedback" -> "REPORTS"
                        currentRoute == "entry" -> "ENTRY"
                        currentRoute == "login" -> "LOGIN"
                        currentRoute == "how_to_use" -> "HOW_TO_USE"
                        currentRoute?.startsWith("home") == true -> "HOME"
                        currentRoute?.startsWith("live_map/") == true -> "LIVE_MAP"
                        else -> currentRoute.orEmpty().uppercase()
                    }
                    if (currentRoute != "coordination_portal") {
                        AppScreenTracker.currentThreadId = null
                    }
                }

                LaunchedEffect(notificationDestination, currentRoute) {
                    val destination = notificationDestination ?: return@LaunchedEffect
                    // SharedPreferences are not Compose state. Re-read the session here so
                    // a notification received immediately after OTP sign-in is not ignored
                    // because startDestination captured the pre-login value.
                    val sessionResponderId = userPrefs.getString("user_id", "")
                        ?.toIntOrNull()
                        ?: 0
                    val sessionValid = authPrefs.getBoolean("user_verified", false) &&
                            sessionResponderId > 0
                    if (!sessionValid) return@LaunchedEffect

                    val route = when (destination) {
                        is NotificationDestination.PrivateChat,
                        is NotificationDestination.DepartmentChat -> "coordination_portal"

                        is NotificationDestination.Broadcast,
                        is NotificationDestination.AssignedIncident -> initialHomeRoute
                    }

                    if (currentRoute != route) {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
                val showBottomBar = currentRoute != null &&
                        currentRoute !in setOf("entry", "login", "how_to_use") &&
                        !currentRoute.startsWith("live_map/") &&
                        !(currentRoute == "coordination_portal" && isCoordinationChatOpen)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = fadeIn(tween(350)) +
                                    slideInVertically(tween(350)) { it / 2 },
                            exit = fadeOut(tween(200)) +
                                    slideOutVertically(tween(200)) { it / 2 }
                        ) {
                            CustomBottomNavigation(
                                selectedRoute = when {
                                    currentRoute?.startsWith("home/") == true -> "home"
                                    else -> currentRoute.orEmpty()
                                },
                                onItemSelected = { route ->
                                    val destination = if (route == "home") {
                                        val department = userPrefs
                                            .getString("department", "")
                                            .orEmpty()
                                            .trim()
                                            .lowercase()
                                        if (department.isBlank()) "home" else "home/$department"
                                    } else {
                                        route
                                    }

                                    if (destination != currentRoute) {
                                        navController.navigate(destination) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("entry") {
                                EmergencyResponseScreen(
                                    onProceed = {
                                        navController.navigate("login") {
                                            popUpTo("entry") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onHowToUse = {
                                        navController.navigate("how_to_use") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable("how_to_use") {
                                HowToUseScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("login") {
                                LoginScreen(
                                    networkAvailable = connectivityStatus == ConnectivityStatus.Online,
                                    onLoggedIn = {
                                        val department = userPrefs
                                            .getString("department", "")
                                            .orEmpty()
                                            .trim()
                                            .lowercase()
                                        val destination = if (department.isBlank()) {
                                            "home"
                                        } else {
                                            "home/$department"
                                        }
                                        val loggedResponderId = userPrefs
                                            .getString("user_id", "")
                                            ?.toIntOrNull()
                                            ?: 0
                                        startPresenceSession(loggedResponderId)
                                        navController.navigate(destination) {
                                            popUpTo("entry") { inclusive = true }
                                            popUpTo("login") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                HomeScreen(
                                    navController = navController,
                                    responderRole = null,
                                    networkStatus = connectivityStatus,
                                    onLogout = { logoutAndNavigate("entry") }
                                )
                            }

                            composable(
                                route = "home/{role}",
                                arguments = listOf(
                                    navArgument("role") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val role = backStackEntry.arguments
                                    ?.getString("role")
                                    ?.takeIf { it.isNotBlank() }
                                HomeScreen(
                                    navController = navController,
                                    responderRole = role,
                                    networkStatus = connectivityStatus,
                                    onLogout = { logoutAndNavigate("login") }
                                )

                                LaunchedEffect(Unit) {
                                    val appPrefs = context.getSharedPreferences(
                                        "ers_prefs",
                                        Context.MODE_PRIVATE
                                    )
                                    if (appPrefs.getBoolean("navigate_to_reviews", false)) {
                                        appPrefs.edit()
                                            .putBoolean("navigate_to_reviews", false)
                                            .apply()
                                        navController.navigate("reviews_feedback") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }

                            composable("coordination_portal") {
                                val responderId = userPrefs.getString("user_id", "").orEmpty()
                                val responderName = userPrefs
                                    .getString("full_name", "Responder")
                                    .orEmpty()
                                    .ifBlank { "Responder" }
                                val department = userPrefs
                                    .getString("department", "")
                                    .orEmpty()

                                if (responderId.toIntOrNull()?.let { it > 0 } != true) {
                                    LaunchedEffect(Unit) {
                                        clearLocalSession()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    CoordinationPortalScreen(
                                        currentResponderId = responderId,
                                        currentResponderName = responderName,
                                        currentResponderRole = department,
                                        navController = navController,
                                        onChatModeChange = { isCoordinationChatOpen = it }
                                    )
                                }
                            }

                            composable("reviews_feedback") {
                                ReviewsFeedbackScreen()
                            }

                            composable("analytics") {
                                HistoricalRouteAnalyticsScreen()
                            }

                            composable(
                                route = "live_map/{lat}/{lng}/{address}" +
                                        "?incidentId={incidentId}" +
                                        "&assignmentId={assignmentId}" +
                                        "&responderId={responderId}" +
                                        "&viewOnly={viewOnly}",
                                arguments = listOf(
                                    navArgument("lat") { type = NavType.StringType },
                                    navArgument("lng") { type = NavType.StringType },
                                    navArgument("address") { type = NavType.StringType },
                                    navArgument("incidentId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("assignmentId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("responderId") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    },
                                    navArgument("viewOnly") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    }
                                )
                            ) { backStackEntry ->
                                LiveRouteMapScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    destinationLat = backStackEntry.arguments
                                        ?.getString("lat")
                                        ?.toDoubleOrNull(),
                                    destinationLng = backStackEntry.arguments
                                        ?.getString("lng")
                                        ?.toDoubleOrNull(),
                                    destinationAddress = backStackEntry.arguments
                                        ?.getString("address"),
                                    incidentId = backStackEntry.arguments
                                        ?.getString("incidentId"),
                                    assignmentId = backStackEntry.arguments
                                        ?.getString("assignmentId"),
                                    responderId = backStackEntry.arguments
                                        ?.getInt("responderId")
                                        ?: 0,
                                    viewOnly = backStackEntry.arguments
                                        ?.getBoolean("viewOnly")
                                        ?: false,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        val bannerEligibleRoute = currentRoute != null &&
                                currentRoute !in setOf("entry", "how_to_use")
                        if (bannerEligibleRoute) {
                            ConnectivityStatusBanner(
                                status = connectivityStatus,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationNavigation.publishFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.isForeground = true

        val responderId = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0

        startPresenceSession(responderId)
    }

    private fun startPresenceSession(responderId: Int) {
        if (responderId <= 0) return

        // Back/Home no longer changes availability. Foregrounding the app or
        // completing OTP cancels any pending timeout and renews the lease.
        ResponderPresenceManager.onAppForeground(applicationContext, responderId)

        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = lifecycleScope.launch {
            runCatching {
                PushTokenManager.registerCurrentToken(applicationContext, responderId)
            }.onFailure { error ->
                Log.w("PUSH_TOKEN", "Unable to register push token", error)
            }

            while (isActive && AppState.isForeground) {
                delay(ResponderPresenceManager.FOREGROUND_HEARTBEAT_MS)
                if (isActive && AppState.isForeground) {
                    ResponderPresenceManager.heartbeatForeground(
                        applicationContext,
                        responderId
                    )
                }
            }
        }
    }

    override fun onStop() {
        AppState.isForeground = false
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null

        val responderId = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0

        if (responderId > 0) {
            // Keep the responder online and assignable for one hour. A delayed
            // worker owns the later offline transition; the FCM token stays active.
            ResponderPresenceManager.onAppBackground(applicationContext, responderId)
        }

        super.onStop()
    }

    private suspend fun markResponderOffline(responderId: Int) {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        // Explicit logout is the only path that also unregisters the push token.
        // Background timeout/task removal changes presence only, so chat and
        // broadcast notifications can still reach the signed-in device.
        runCatching {
            ResponderPresenceManager.markExplicitlyOffline(
                context = applicationContext,
                responderId = responderId,
                reason = "logout"
            )
        }.onFailure { error ->
            Log.w("LOGOUT", "Presence cleanup failed", error)
        }

        runCatching {
            PushTokenManager.unregisterCurrentToken(applicationContext, responderId)
        }.onFailure { error ->
            Log.w("LOGOUT", "Push-token unregister failed", error)
        }

        runCatching {
            RetrofitProvider.authApi.logout(responderId)
        }.onFailure { error ->
            Log.w("LOGOUT", "Server logout endpoint failed", error)
        }
    }


}
