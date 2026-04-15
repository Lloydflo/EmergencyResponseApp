package com.ers.emergencyresponseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.ers.emergencyresponseapp.firebase.ui.FirebaseChatScreen
import com.ers.emergencyresponseapp.firebase.ui.ResponderListScreen
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository

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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchTime = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentUserId = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("user_id", "") ?: ""

        Log.e("APP_START", "MainActivity onCreate - started")
        setContent {
            val context   = LocalContext.current
            val prefs     = context.getSharedPreferences("ers_prefs", MODE_PRIVATE)
            val authPrefs = context.getSharedPreferences("auth", MODE_PRIVATE)
            val isLoggedIn = authPrefs.getBoolean("user_verified", false)
            val userPrefs  = context.getSharedPreferences("user_prefs", MODE_PRIVATE)
            val dept       = userPrefs.getString("department", null)?.lowercase()
            val homeRoute  = if (!dept.isNullOrBlank()) "home/$dept" else "home"

            val DEV_BYPASS_LOGIN = false
            val startDestination = if (DEV_BYPASS_LOGIN) homeRoute
            else if (isLoggedIn) homeRoute else "entry"

            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") isDarkMode = prefs.getBoolean("dark_mode", false)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            EmergencyResponseAppTheme(darkTheme = isDarkMode) {
                Surface(color = MaterialTheme.colorScheme.background) {

                    val navController = rememberNavController()

                    // Auto-logout after 5 min of inactivity
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)
                            if (System.currentTimeMillis() - lastTouchTime >= 5 * 60 * 1000L) {
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

                    val hideBottomBarRoutes = setOf(
                        "entry",
                        "login",
                        "coordination_portal",
                        "responder_list/{myUserId}",
                        "firebase_chat/{myUserId}/{partnerUserId}"
                    )
                    val showBottomBar = currentRoute !in hideBottomBarRoutes

                    Scaffold(
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter   = fadeIn(tween(350)) + slideInVertically(tween(350))  { it / 2 },
                                exit    = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
                            ) {
                                CustomBottomNavigation(
                                    selectedRoute  = currentRoute ?: "",
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
                        Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(
                                navController    = navController,
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
                                    AnimatedHomeScreen(
                                        navController = navController,
                                        responderRole = null,
                                        onLogout = {
                                            authPrefs.edit().clear().commit()
                                            context.getSharedPreferences("user_prefs", MODE_PRIVATE)
                                                .edit().clear().commit()
                                            navController.navigate("entry") {
                                                popUpTo(0) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }

                                // Home with role
                                composable("home/{role}") { backStackEntry ->
                                    val roleArg      = backStackEntry.arguments?.getString("role")
                                    val innerContext = LocalContext.current
                                    val innerPrefs   = innerContext.getSharedPreferences("ers_prefs", MODE_PRIVATE)

                                    AnimatedHomeScreen(
                                        navController = navController,
                                        responderRole = roleArg?.takeIf { it.isNotBlank() },
                                        onLogout = {
                                            innerContext.getSharedPreferences("auth", MODE_PRIVATE)
                                                .edit().clear().commit()
                                            innerContext.getSharedPreferences("user_prefs", MODE_PRIVATE)
                                                .edit().clear().commit()
                                            navController.navigate("login") {
                                                popUpTo(0) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                    LaunchedEffect(Unit) {
                                        if (innerPrefs.getBoolean("navigate_to_reviews", false)) {
                                            innerPrefs.edit().putBoolean("navigate_to_reviews", false).commit()
                                            navController.navigate("reviews_feedback") { launchSingleTop = true }
                                        }
                                    }
                                }

                                // ── Coordination portal ───────────────────────
                                composable("coordination_portal") {
                                    val localContext = LocalContext.current
                                    val localPrefs   = localContext.getSharedPreferences("user_prefs", MODE_PRIVATE)
                                    val department   = localPrefs.getString("department", "fire") ?: "fire"
                                    val myUserId     = localPrefs.getString("user_id", "current_user") ?: "current_user"
                                    // FIX: read the saved full name — use the same key your login saves it with
                                    val myUserName   = localPrefs.getString("full_name", "Responder") ?: "Responder"

                                    CoordinationPortalScreen(
                                        currentResponderId   = myUserId,
                                        currentResponderName = myUserName,
                                        currentResponderRole = department,
                                        navController        = navController
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
                                    val myUserId = backStackEntry.arguments?.getString("myUserId") ?: ""
                                    ResponderListScreen(
                                        myUserId  = myUserId,
                                        onOpenChat = { partnerUserId ->
                                            navController.navigate(
                                                "firebase_chat/$myUserId/$partnerUserId"
                                            )
                                        }
                                    )
                                }

                                // ── One-to-one Firebase chat screen ───────────
                                composable("firebase_chat/{myUserId}/{partnerUserId}") { backStackEntry ->
                                    val myUserId      = backStackEntry.arguments?.getString("myUserId")      ?: ""
                                    val partnerUserId = backStackEntry.arguments?.getString("partnerUserId") ?: ""
                                    FirebaseChatScreen(
                                        myUserId      = myUserId,
                                        partnerUserId = partnerUserId,
                                        onBack        = { navController.popBackStack() }
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
        if (currentUserId.isNotEmpty())
            firebaseChatRepo.setOnlineStatus(currentUserId, true)
    }

    override fun onStop() {
        super.onStop()
        if (currentUserId.isNotEmpty())
            firebaseChatRepo.setOnlineStatus(currentUserId, false)
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
@Composable
fun AnimatedHomeScreen(
    navController : androidx.navigation.NavHostController,
    responderRole : String? = null,
    onLogout      : () -> Unit
) {
    var showEntrance by remember { mutableStateOf(true) }
    val alpha        = remember { Animatable(0f) }
    val scale        = remember { Animatable(0.9f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(450))
        scale.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        delay(450L)
        showEntrance = false
    }

    if (showEntrance) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text      = "Emergency Response",
                style     = MaterialTheme.typography.headlineLarge,
                color     = MaterialTheme.colorScheme.onBackground,
                modifier  = Modifier.scale(scale.value).alpha(alpha.value).fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    } else {
        HomeScreen(
            navController = navController,
            responderRole = responderRole,
            onLogout      = onLogout
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Session timeout watcher
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SessionTimeoutWatcher(
    lastTouchMillis : MutableStateFlow<Long>,
    timeoutMillis   : Long = 5 * 60 * 1000L,
    onTimeout       : () -> Unit
) {
    LaunchedEffect(Unit) {
        lastTouchMillis.collectLatest { last ->
            delay(timeoutMillis)
            if (System.currentTimeMillis() - last >= timeoutMillis) onTimeout()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencyResponsePreview() {
    EmergencyResponseAppTheme {
        EmergencyResponseScreen(onProceed = {})
    }
}