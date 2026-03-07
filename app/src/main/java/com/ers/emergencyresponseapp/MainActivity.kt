package com.ers.emergencyresponseapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ers.emergencyresponseapp.navigation.InAppNavigationScreen
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Bottom nav items
sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavItem("home", "Home", Icons.Filled.Home)
    object CoordinationPortal : NavItem("coordination_portal", "Coordination", Icons.Filled.Groups)
    object ReviewsFeedback : NavItem("reviews_feedback", "Reviews", Icons.Filled.RateReview)
    object Analytics : NavItem("analytics", "Analytics", Icons.Filled.Timeline)
}

class MainActivity : ComponentActivity() {

    // ✅ flow para sa timeout watcher
    private val lastTouchFlow = MutableStateFlow(System.currentTimeMillis())

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchFlow.value = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current

            val prefs = context.getSharedPreferences("ers_prefs", MODE_PRIVATE)
            val authPrefs = context.getSharedPreferences("auth", MODE_PRIVATE)
            val userPrefs = context.getSharedPreferences("user_prefs", MODE_PRIVATE)

            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            // ✅ optional home/{dept}
            val dept = userPrefs.getString("department", null)?.lowercase()
            val homeRoute = if (!dept.isNullOrBlank()) "home/$dept" else "home"

            val isLoggedIn = authPrefs.getBoolean("user_verified", false)
            val DEV_BYPASS_LOGIN = true // TEMP ONLY

            val startDestination = when {
                DEV_BYPASS_LOGIN -> homeRoute
                isLoggedIn -> homeRoute
                else -> "entry"
            }

            val navController = rememberNavController()

            // listen theme changes
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") isDarkMode = prefs.getBoolean("dark_mode", false)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            EmergencyResponseAppTheme(darkTheme = isDarkMode) {

                // ✅ timeout watcher (works kahit anong screen)
                SessionTimeoutWatcher(
                    lastTouchMillis = lastTouchFlow,
                    timeoutMillis = 5 * 60 * 1000L,
                    onTimeout = {
                        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
                        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()

                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute != "entry" && currentRoute != "login"

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavigationBar(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    if (route != currentRoute) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->

                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("entry") {
                                EmergencyResponseScreen(onProceed = {
                                    navController.navigate("login") {
                                        popUpTo("entry") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                })
                            }

                            composable("login") {
                                LoginScreen(onLoggedIn = { _ ->
                                    navController.navigate(homeRoute) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                })
                            }

                            composable("home") {
                                AnimatedHomeScreen(
                                    navController = navController,
                                    responderRole = null,
                                    onLogout = {
                                        authPrefs.edit().clear().apply()
                                        userPrefs.edit().clear().apply()
                                        navController.navigate("entry") {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable("home/{role}") { backStackEntry ->
                                val roleArg = backStackEntry.arguments?.getString("role")
                                AnimatedHomeScreen(
                                    navController = navController,
                                    responderRole = roleArg,
                                    onLogout = {
                                        authPrefs.edit().clear().apply()
                                        userPrefs.edit().clear().apply()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable("inapp_nav/{lat}/{lng}/{label}") { backStackEntry ->
                                val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
                                val lng = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull() ?: 0.0
                                val label = backStackEntry.arguments?.getString("label") ?: "Destination"

                                InAppNavigationScreen(
                                    destLat = lat,
                                    destLng = lng,
                                    destLabel = label,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("coordination_portal") {
                                val department = userPrefs.getString("department", "fire") ?: "fire"
                                CoordinationPortalScreen(
                                    currentResponderId = "current_user",
                                    currentResponderRole = department
                                )
                            }

                            composable("reviews_feedback") { ReviewsFeedbackScreen() }
                            composable("analytics") { HistoricalRouteAnalyticsScreen() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(
        NavItem.Home,
        NavItem.CoordinationPortal,
        NavItem.ReviewsFeedback,
        NavItem.Analytics
    )
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val department = prefs.getString("department", null)
    val departmentHomeRoute = department?.let { "home/${it.lowercase()}" } ?: "home"

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    if (item is NavItem.Home) onNavigate(departmentHomeRoute)
                    else onNavigate(item.route)
                }
            )
        }
    }
}

@Composable
fun EmergencyResponseScreen(
    modifier: Modifier = Modifier,
    onProceed: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Button(onClick = { scope.launch { onProceed() } }) {
        Text("Proceed")
    }

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
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Emergency Response",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        Crossfade(targetState = currentIndex, animationSpec = tween(600)) { idx ->
            Text(
                text = quotes[idx],
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { scope.launch { onProceed() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Proceed")
        }
    }
}

@Composable
fun AnimatedHomeScreen(
    navController: NavHostController,
    responderRole: String? = null,
    onLogout: () -> Unit
) {
    var showEntrance by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.9f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(450))
        scale.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        delay(450L)
        showEntrance = false
    }

    if (showEntrance) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Emergency Response",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.scale(scale.value).alpha(alpha.value),
                textAlign = TextAlign.Center
            )
        }
    } else {
        HomeScreen(
            navController = navController,
            responderRole = responderRole,
            onLogout = onLogout
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencyResponsePreview() {
    EmergencyResponseAppTheme {
        EmergencyResponseScreen(onProceed = {})
    }
}

@Composable
fun SessionTimeoutWatcher(
    lastTouchMillis: MutableStateFlow<Long>,
    timeoutMillis: Long,
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        lastTouchMillis.collectLatest { last ->
            delay(timeoutMillis)
            val now = System.currentTimeMillis()
            if (now - last >= timeoutMillis) onTimeout()
        }
    }
}