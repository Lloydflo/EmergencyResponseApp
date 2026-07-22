package com.ers.emergencyresponseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import android.view.MotionEvent
import android.view.WindowManager
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ers.emergencyresponseapp.map.LiveRouteMapScreen
import com.ers.emergencyresponseapp.firebase.ui.FirebaseChatScreen
import com.ers.emergencyresponseapp.firebase.ui.ResponderListScreen
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import androidx.lifecycle.lifecycleScope
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob




sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home              : NavItem("home",                "Home",         Icons.Filled.Home)
    object CoordinationPortal: NavItem("coordination_portal", "Coordination", Icons.Filled.Groups)
    object ReviewsFeedback   : NavItem("reviews_feedback",    "Reviews",      Icons.Filled.RateReview)
    object Analytics         : NavItem("analytics",           "Analytics",    Icons.Filled.Timeline)
}

class MainActivity : ComponentActivity() {
    private var lastTouchTime = System.currentTimeMillis()

    private val firebaseChatRepo = FirebaseChatRepository()
    private var currentUserId: String = ""
    private val presenceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun createEmergencyChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                "emergency_incidents",
                "Emergency Incidents",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description = "Emergency assignments"

                enableVibration(true)

                vibrationPattern = longArrayOf(
                    0,
                    500,
                    200,
                    500,
                    200,
                    500
                )

                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchTime = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent Android from panning the whole chat screen when the IME opens.
        // The activity is resized instead, keeping the chat header fixed and
        // placing the composer directly above the keyboard.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        createEmergencyChannel()

        currentUserId = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("user_id", "") ?: ""

        Log.e("APP_START", "MainActivity onCreate - started")
        setContent {
            val context = LocalContext.current
            val prefs = context.getSharedPreferences("ers_prefs", MODE_PRIVATE)
            val authPrefs = context.getSharedPreferences("auth", MODE_PRIVATE)
            val isLoggedIn = authPrefs.getBoolean("user_verified", false)
            val userPrefs = context.getSharedPreferences("user_prefs", MODE_PRIVATE)
            val dept = userPrefs.getString("department", null)?.lowercase()
            val homeRoute = if (!dept.isNullOrBlank()) "home/$dept" else "home"

            val DEV_BYPASS_LOGIN = false
            val startDestination = if (DEV_BYPASS_LOGIN) homeRoute
            else if (isLoggedIn) homeRoute else "entry"

            LaunchedEffect(Unit) {
                ThemeController.init(context)
            }

// ADD THIS — keep system status/nav bars in sync with in-app theme
            val darkModeState = ThemeController.isDarkMode.value
            LaunchedEffect(darkModeState) {
                val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                // Light icons (white) when dark mode is on; dark icons when light mode is on
                controller.isAppearanceLightStatusBars = !darkModeState
                controller.isAppearanceLightNavigationBars = !darkModeState
            }

            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") isDarkMode = prefs.getBoolean("dark_mode", false)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            EmergencyResponseAppTheme(darkTheme = ThemeController.isDarkMode.value) {
                Surface(color = MaterialTheme.colorScheme.background) {

                    val navController = rememberNavController()
                    var isCoordinationChatOpen by remember { mutableStateOf(false) }

                    // Auto-logout after 5 min of inactivity
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)

                            val navPrefs = context.getSharedPreferences(
                                "nav_prefs",
                                Context.MODE_PRIVATE
                            )

                            val hasActiveRoute =
                                navPrefs.getBoolean(
                                    "pending_en_route_check",
                                    false
                                )

                            //
                            val serviceRunning = RouteMonitoringService.isRunning

                            if (
                                !hasActiveRoute &&
                                !serviceRunning &&
                                System.currentTimeMillis() - lastTouchTime >= 60 * 60 * 1000L
                            ) {

                                val responderId = context.getSharedPreferences(
                                    "user_prefs",
                                    Context.MODE_PRIVATE
                                )
                                    .getString("user_id", "")
                                    ?.toIntOrNull()
                                    ?: 0

                                if (responderId > 0) {
                                    val repo =
                                        com.ers.emergencyresponseapp.data.IncidentRepository()

                                    repo.setUnitPresence(
                                        responderId = responderId,
                                        presence = "offline"
                                    )

                                    firebaseChatRepo.setOnlineStatus(
                                        responderId.toString(),
                                        false
                                    )
                                }

                                context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                    .edit().clear().commit()

                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                    .edit().clear().commit()

                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }

                                break
                            }
                        }
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // ✅ Match by prefix instead
                    val hideBottomBarRoutes = setOf(
                        "entry",
                        "login",
                    )

                    val showBottomBar = currentRoute != null &&
                            !hideBottomBarRoutes.contains(currentRoute) &&
                            !currentRoute.startsWith("responder_list/") &&
                            !currentRoute.startsWith("firebase_chat/") &&
                            !currentRoute.startsWith("live_map/") &&
                            !(currentRoute == "coordination_portal" && isCoordinationChatOpen)


                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
                                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
                            ) {
                                CustomBottomNavigation(
                                    selectedRoute = currentRoute ?: "",
                                    onItemSelected = { route ->
                                        if (route != currentRoute) navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
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

                                // Entry / splash
                                composable("entry") {
                                    EmergencyResponseScreen(onProceed = {
                                        navController.navigate("login") {
                                            popUpTo("entry") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    })
                                }

                                // Login
                                composable("login") {
                                    LoginScreen(onLoggedIn = { _email: String ->
                                        navController.navigate(homeRoute) {
                                            popUpTo("entry") { inclusive = true }
                                            popUpTo("login") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    })
                                }

                                // Home (no role)
                                composable("home") {
                                    HomeScreen(
                                        navController = navController,
                                        responderRole = null,
                                        onLogout = {
                                            val responderId = context.getSharedPreferences(
                                                "user_prefs",
                                                MODE_PRIVATE
                                            )
                                                .getString("user_id", "")
                                                ?.toIntOrNull()
                                                ?: 0

                                            lifecycleScope.launch {
                                                if (responderId > 0) {
                                                    val repo =
                                                        com.ers.emergencyresponseapp.data.IncidentRepository()

                                                    repo.setUnitPresence(
                                                        responderId = responderId,
                                                        presence = "offline"
                                                    )

                                                    firebaseChatRepo.setOnlineStatus(
                                                        responderId.toString(),
                                                        false
                                                    )
                                                }

                                                authPrefs.edit().clear().commit()

                                                context.getSharedPreferences(
                                                    "user_prefs",
                                                    MODE_PRIVATE
                                                )
                                                    .edit()
                                                    .clear()
                                                    .commit()

                                                navController.navigate("entry") {
                                                    popUpTo(0) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    )
                                }

                                // Home with role
                                composable("home/{role}") { backStackEntry ->
                                    val roleArg =
                                        backStackEntry.arguments?.getString("role")
                                    val innerContext = LocalContext.current
                                    val innerPrefs =
                                        innerContext.getSharedPreferences(
                                            "ers_prefs",
                                            MODE_PRIVATE
                                        )

                                    HomeScreen(
                                        navController = navController,
                                        responderRole = roleArg?.takeIf { it.isNotBlank() },
                                        onLogout = {
                                            val responderId = innerContext.getSharedPreferences(
                                                "user_prefs",
                                                MODE_PRIVATE
                                            )
                                                .getString("user_id", "")
                                                ?.toIntOrNull()
                                                ?: 0

                                            lifecycleScope.launch {
                                                if (responderId > 0) {
                                                    val repo =
                                                        com.ers.emergencyresponseapp.data.IncidentRepository()

                                                    repo.setUnitPresence(
                                                        responderId = responderId,
                                                        presence = "offline"
                                                    )

                                                    firebaseChatRepo.setOnlineStatus(
                                                        responderId.toString(),
                                                        false
                                                    )
                                                }

                                                innerContext.getSharedPreferences(
                                                    "auth",
                                                    MODE_PRIVATE
                                                )
                                                    .edit()
                                                    .clear()
                                                    .commit()

                                                innerContext.getSharedPreferences(
                                                    "user_prefs",
                                                    MODE_PRIVATE
                                                )
                                                    .edit()
                                                    .clear()
                                                    .commit()

                                                navController.navigate("login") {
                                                    popUpTo(0) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    )
                                    LaunchedEffect(Unit) {
                                        if (innerPrefs.getBoolean(
                                                "navigate_to_reviews",
                                                false
                                            )
                                        ) {
                                            innerPrefs.edit()
                                                .putBoolean("navigate_to_reviews", false)
                                                .commit()
                                            navController.navigate("reviews_feedback") {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }

                                // ── Coordination portal ───────────────────────
                                composable("coordination_portal") {
                                    val localContext = LocalContext.current
                                    val localPrefs = localContext.getSharedPreferences(
                                        "user_prefs",
                                        MODE_PRIVATE
                                    )
                                    val department =
                                        localPrefs.getString("department", "fire") ?: "fire"
                                    val myUserId =
                                        localPrefs.getString("user_id", "current_user")
                                            ?: "current_user"
                                    // FIX: read the saved full name — use the same key your login saves it with
                                    val myUserName =
                                        localPrefs.getString("full_name", "Responder")
                                            ?: "Responder"

                                    // ADD THIS:
                                    android.util.Log.d(
                                        "COORD_DEBUG",
                                        "userId=$myUserId name=$myUserName dept=$department"
                                    )

                                    CoordinationPortalScreen(
                                        currentResponderId = myUserId,
                                        currentResponderName = myUserName,
                                        currentResponderRole = department,
                                        navController = navController,
                                        onChatModeChange = { isChat ->
                                            isCoordinationChatOpen = isChat
                                        }
                                    )
                                }

                                // Reviews & Feedback
                                composable("reviews_feedback") {
                                    ReviewsFeedbackScreen()
                                }

                                // Analytics
                                composable("analytics") {
                                    HistoricalRouteAnalyticsScreen()
                                }

                                composable("responder_list/{myUserId}") { backStackEntry ->
                                    val myUserId =
                                        backStackEntry.arguments?.getString("myUserId")
                                            ?: ""
                                    ResponderListScreen(
                                        myUserId = myUserId,
                                        onOpenChat = { partnerUserId ->
                                            navController.navigate(
                                                "firebase_chat/$myUserId/$partnerUserId"
                                            )
                                        }
                                    )
                                }

                                // ── One-to-one Firebase chat screen ───────────
                                composable("firebase_chat/{myUserId}/{partnerUserId}") { backStackEntry ->
                                    val myUserId =
                                        backStackEntry.arguments?.getString("myUserId")
                                            ?: ""
                                    val partnerUserId =
                                        backStackEntry.arguments?.getString("partnerUserId")
                                            ?: ""
                                    FirebaseChatScreen(
                                        myUserId = myUserId,
                                        partnerUserId = partnerUserId,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                // ── Live MapLibre navigation screen ───────────
                                composable(
                                    "live_map/{lat}/{lng}/{address}?incidentId={incidentId}&assignmentId={assignmentId}&responderId={responderId}&viewOnly={viewOnly}",
                                    arguments = listOf(
                                        navArgument("lat") { type = NavType.StringType },
                                        navArgument("lng") { type = NavType.StringType },
                                        navArgument("address") {
                                            type = NavType.StringType
                                        },
                                        navArgument("incidentId") {
                                            type = NavType.StringType; nullable =
                                            true; defaultValue = null
                                        },
                                        navArgument("assignmentId") {
                                            type = NavType.StringType; nullable =
                                            true; defaultValue = null
                                        }, // ADD
                                        navArgument("responderId") {
                                            type = NavType.IntType; defaultValue = 0
                                        },
                                        navArgument("viewOnly") {
                                            type = NavType.BoolType; defaultValue = false
                                        }
                                    )
                                ) { backStackEntry ->
                                    val lat =
                                        backStackEntry.arguments?.getString("lat")
                                            ?.toDoubleOrNull()
                                    val lng =
                                        backStackEntry.arguments?.getString("lng")
                                            ?.toDoubleOrNull()
                                    val address =
                                        backStackEntry.arguments?.getString("address")
                                    val incidentIdArg =
                                        backStackEntry.arguments?.getString("incidentId")
                                    val assignmentIdArg =
                                        backStackEntry.arguments?.getString("assignmentId") // ADD
                                    val responderIdArg =
                                        backStackEntry.arguments?.getInt("responderId") ?: 0
                                    val viewOnlyArg =
                                        backStackEntry.arguments?.getBoolean("viewOnly")
                                            ?: false

                                    LiveRouteMapScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        destinationLat = lat,
                                        destinationLng = lng,
                                        destinationAddress = address,
                                        incidentId = incidentIdArg,
                                        assignmentId = assignmentIdArg,  // ADD
                                        responderId = responderIdArg,
                                        viewOnly = viewOnlyArg,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                            } // end NavHost
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        AppState.isForeground = true

        val userPrefs = getSharedPreferences(
            "user_prefs",
            MODE_PRIVATE
        )

        val responderId = userPrefs
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0

        if (responderId > 0) {
            lifecycleScope.launch {
                try {
                    firebaseChatRepo.setOnlineStatus(
                        responderId.toString(),
                        true
                    )

                    val repo =
                        com.ers.emergencyresponseapp.data.IncidentRepository()

                    val latestStatus = repo.setUnitPresence(
                        responderId = responderId,
                        presence = "online"
                    )

                    if (!latestStatus.isNullOrBlank()) {
                        userPrefs.edit()
                            .putString(
                                "unit_status",
                                latestStatus
                            )
                            .apply()
                    }

                    Log.d(
                        "UNIT_PRESENCE",
                        "Responder online, status=$latestStatus"
                    )

                } catch (e: Exception) {
                    Log.e(
                        "UNIT_PRESENCE",
                        "Failed to mark responder online",
                        e
                    )
                }
            }
        }
    }


    override fun onStop() {
        AppState.isForeground = false

        val responderId = getSharedPreferences(
            "user_prefs",
            MODE_PRIVATE
        )
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0

        if (responderId > 0) {
            presenceScope.launch {
                try {
                    val repo =
                        com.ers.emergencyresponseapp.data.IncidentRepository()

                    val status = repo.setUnitPresence(
                        responderId = responderId,
                        presence = "offline"
                    )

                    firebaseChatRepo.setOnlineStatus(
                        responderId.toString(),
                        false
                    )

                    Log.d(
                        "UNIT_PRESENCE",
                        "onStop responder=$responderId result=$status"
                    )
                } catch (e: Exception) {
                    Log.e(
                        "UNIT_PRESENCE",
                        "Offline update failed",
                        e
                    )
                }
            }
        }

        super.onStop()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom navigation bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BottomNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(
        NavItem.Home, NavItem.CoordinationPortal,
        NavItem.ReviewsFeedback, NavItem.Analytics
    )
    val context             = LocalContext.current
    val prefs               = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    val departmentPref      = prefs.getString("department", null)
    val departmentHomeRoute = departmentPref?.let { "home/${it.lowercase()}" } ?: "home"

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon     = { Icon(item.icon, contentDescription = item.title) },
                label    = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick  = {
                    if (item is NavItem.Home) onNavigate(departmentHomeRoute)
                    else onNavigate(item.route)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Entry / splash screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyResponseScreen(modifier: Modifier = Modifier, onProceed: () -> Unit) {
    val scope  = rememberCoroutineScope()
    val quotes = listOf(
        "Every call you answer makes a community safer.",
        "Courage is contagious — thank you for showing up.",
        "Small acts of care create huge impacts.",
        "You're the calm in someone else's storm.",
        "Your quick response saves lives and builds trust."
    )
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        Log.d("API_TEST", "App opened")
        while (true) {
            delay(5000L)
            currentIndex = (currentIndex + 1) % quotes.size
        }
    }

    Column(
        modifier            = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text      = "Emergency Response",
            style     = MaterialTheme.typography.headlineSmall,
            color     = MaterialTheme.colorScheme.onBackground,
            modifier  = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Crossfade(targetState = currentIndex, animationSpec = tween(600)) { idx: Int ->
            Text(
                text      = quotes[idx],
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f),
                modifier  = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick  = { scope.launch { onProceed() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Proceed") }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
//  Animated home screen wrapper
// ─────────────────────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────────────────────
//  Session timeout watcher
// ─────────────────────────────────────────────────────────────────────────────


@Preview(showBackground = true)
@Composable
fun EmergencyResponsePreview() {
    EmergencyResponseAppTheme {
        EmergencyResponseScreen(onProceed = {})
    }
}