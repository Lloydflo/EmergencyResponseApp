package com.ers.emergencyresponseapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ers.emergencyresponseapp.data.IncidentRepository
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import com.ers.emergencyresponseapp.map.LiveRouteMapScreen
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.notification.NotificationDestination
import com.ers.emergencyresponseapp.notification.NotificationNavigation
import com.ers.emergencyresponseapp.notification.PushTokenManager
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastTouchTime = System.currentTimeMillis()
    private val firebaseChatRepository = FirebaseChatRepository()
    private val presenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastTouchTime = System.currentTimeMillis()
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        createEmergencyChannel()
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
                var isCoordinationChatOpen by remember { mutableStateOf(false) }

                val storedResponderId = userPrefs.getString("user_id", "")
                    ?.toIntOrNull()
                    ?: 0
                val hasValidSession = authPrefs.getBoolean("user_verified", false) &&
                        storedResponderId > 0
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

                // A responder stays signed in during an active route. Outside an
                // active response, one hour without interaction closes the session.
                LaunchedEffect(navController) {
                    while (true) {
                        delay(10_000L)
                        val routeActive = context
                            .getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                            .getBoolean("pending_en_route_check", false)
                        val sessionActive = authPrefs.getBoolean("user_verified", false)
                        val timedOut = System.currentTimeMillis() - lastTouchTime >= 60L * 60L * 1_000L

                        if (sessionActive && !routeActive && !RouteMonitoringService.isRunning && timedOut) {
                            logoutAndNavigate("login")
                            break
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

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

                        is NotificationDestination.Broadcast -> initialHomeRoute
                    }

                    if (currentRoute != route) {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
                val showBottomBar = currentRoute != null &&
                        currentRoute !in setOf("entry", "login") &&
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
                                    }
                                )
                            }

                            composable("login") {
                                LoginScreen(
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
                                        lastTouchTime = System.currentTimeMillis()
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

        if (responderId > 0) {
            lifecycleScope.launch {
                PushTokenManager.registerCurrentToken(applicationContext, responderId)
                runCatching {
                    firebaseChatRepository.setOnlineStatus(responderId.toString(), true)
                    IncidentRepository().setUnitPresence(responderId, "online")
                }.onFailure { error ->
                    Log.e("UNIT_PRESENCE", "Unable to mark responder online", error)
                }
            }
        }
    }

    override fun onStop() {
        AppState.isForeground = false

        val responderId = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0

        if (responderId > 0 && !RouteMonitoringService.isRunning) {
            presenceScope.launch {
                runCatching {
                    firebaseChatRepository.setOnlineStatus(responderId.toString(), false)
                    IncidentRepository().setUnitPresence(responderId, "offline")
                }.onFailure { error ->
                    Log.e("UNIT_PRESENCE", "Unable to mark responder offline", error)
                }
            }
        }

        super.onStop()
    }

    private suspend fun markResponderOffline(responderId: Int) {
        PushTokenManager.unregisterCurrentToken(applicationContext, responderId)

        runCatching {
            RetrofitProvider.authApi.logout(responderId)
        }.onFailure { error ->
            Log.w("LOGOUT", "Server logout endpoint failed", error)
        }

        runCatching {
            IncidentRepository().setUnitPresence(responderId, "offline")
        }

        runCatching {
            firebaseChatRepository.setOnlineStatus(responderId.toString(), false)
        }
    }

    private fun createEmergencyChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            "emergency_incidents",
            "Emergency Incidents",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency assignments"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

@Composable
fun EmergencyResponseScreen(
    modifier: Modifier = Modifier,
    onProceed: () -> Unit
) {
    val quotes = listOf(
        "Every call you answer makes a community safer.",
        "Courage is contagious — thank you for showing up.",
        "Small acts of care create huge impacts.",
        "You're the calm in someone else's storm.",
        "Your quick response saves lives and builds trust."
    )
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            currentIndex = (currentIndex + 1) % quotes.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Emergency Response",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Crossfade(
            targetState = currentIndex,
            animationSpec = tween(600),
            label = "response_quote"
        ) { index ->
            Text(
                text = quotes[index],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Proceed")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmergencyResponsePreview() {
    EmergencyResponseAppTheme {
        EmergencyResponseScreen(onProceed = {})
    }
}
