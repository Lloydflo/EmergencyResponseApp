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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ers.emergencyresponseapp.network.RetrofitProvider


// If you have a BottomNavItem sealed class in the project, reuse it; otherwise declare a lightweight version here.
sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavItem("home", "Home", Icons.Filled.Home)
    object CoordinationPortal : NavItem("coordination_portal", "Coordination", Icons.Filled.Groups)
    object ReviewsFeedback : NavItem("reviews_feedback", "Reviews", Icons.Filled.RateReview)
    object Analytics : NavItem("analytics", "Analytics", Icons.Filled.Timeline)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("APP_START", "MainActivity onCreate - started")
        setContent {
            val context = LocalContext.current
            val prefs = context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") {
                        isDarkMode = prefs.getBoolean("dark_mode", false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            EmergencyResponseAppTheme(darkTheme = isDarkMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val mainDestinations = setOf("coordination_portal", "reviews_feedback", "analytics")
                    // Show bottom bar for any home route (home or home/<role>) and other main destinations
                    val showBottomBar = (currentRoute?.startsWith("home") == true) || (currentRoute in mainDestinations)

                    Scaffold(
                         bottomBar = {
                             AnimatedVisibility(
                                visible = showBottomBar,
                                 enter = fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { height -> height / 2 },
                                 exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { height -> height / 2 }
                             ) {
                                 BottomNavigationBar(currentRoute = currentRoute, onNavigate = { route ->
                                     if (route != currentRoute) navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                     }
                                 })
                             }
                         }
                     ) { innerPadding ->
                         Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(navController = navController, startDestination = "entry") {
                                composable("entry") {
                                    // Single Proceed button navigates to the login flow
                                    EmergencyResponseScreen(onProceed = {
                                        navController.navigate("login") {
                                            popUpTo("entry") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    })
                                }

                                // Login flow (email + OTP) -> navigate to home on success
                                composable("login") {
                                    LoginScreen(onLoggedIn = { _email: String ->
                                        navController.navigate("home") {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    })
                                }

                                // Home route (no role) and home route accepting an optional role segment
                                composable("home") {
                                     AnimatedHomeScreen(responderRole = null)
                                 }
                                 composable("home/{role}") { backStackEntry ->
                                     val roleArg = backStackEntry.arguments?.getString("role")
                                     AnimatedHomeScreen(responderRole = roleArg?.takeIf { it.isNotBlank() })
                                     // Check for navigation trigger from HomeScreen (mark-done flow)
                                     val innerContext = LocalContext.current
                                     val innerPrefs = innerContext.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
                                     LaunchedEffect(Unit) {
                                         if (innerPrefs.getBoolean("navigate_to_reviews", false)) {
                                             // clear flag and navigate
                                             innerPrefs.edit().putBoolean("navigate_to_reviews", false).apply()
                                             navController.navigate("reviews_feedback") {
                                                 popUpTo(navController.graph.startDestinationId)
                                                 launchSingleTop = true
                                             }
                                         }
                                     }
                                 }

                                // Coordination portal screen (accessible from bottom navigation)
                                composable("coordination_portal") {
                                    val localContext = LocalContext.current
                                    val localPrefs = localContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                    val department = localPrefs.getString("department", "fire") ?: "fire"
                                    CoordinationPortalScreen(currentResponderId = "current_user", currentResponderRole = department)
                                }

                                // Reviews & Feedback screen (accessible from bottom navigation)
                                composable("reviews_feedback") {
                                    ReviewsFeedbackScreen()
                                }

                                composable("analytics") {
                                    HistoricalRouteAnalyticsScreen()
                                }
                             }
                         }
                     }
                 }
             }
         }
     }
 }

@Composable
fun BottomNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(NavItem.Home, NavItem.CoordinationPortal, NavItem.ReviewsFeedback, NavItem.Analytics)
    val context = LocalContext.current
    // registration stores department under 'user_prefs'
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val departmentPref = prefs.getString("department", null)
    // Map stored department to a home route; use lowercase role token used by nav
    val departmentHomeRoute = departmentPref?.let { "home/${it.lowercase()}" } ?: "home"
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    // Preserve department when navigating to Home
                    if (item is NavItem.Home) onNavigate(departmentHomeRoute) else onNavigate(item.route)
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

    LaunchedEffect(Unit) {
        Log.d("API_TEST", "App opened")


        try {

        } catch (e: Exception) {
            Log.e("API_TEST", "ERROR: ${e.message}", e)
        }
    }

    // Encouraging quotes to rotate
    val quotes = listOf(
        "Every call you answer makes a community safer.",
        "Courage is contagious — thank you for showing up.",
        "Small acts of care create huge impacts.",
        "You're the calm in someone else's storm.",
        "Your quick response saves lives and builds trust."
    )

    var currentIndex by remember { mutableStateOf(0) }

    // Rotate quotes every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentIndex = (currentIndex + 1) % quotes.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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

        // Show rotating encouraging quote with crossfade animation
        Crossfade(targetState = currentIndex, animationSpec = tween(durationMillis = 600)) { idx: Int ->
            Text(
                text = quotes[idx],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Single Proceed button -> registration
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { scope.launch { onProceed() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Proceed")
        }
     }
 }

// LoginScreen is defined in LoginScreen.kt
// HomeScreen(...) is defined in HomeScreen.kt

@Composable
fun AnimatedHomeScreen(responderRole: String? = null) {
    var showEntrance by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.9f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 450))
        scale.animateTo(1f, animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing))
        delay(450L)
        showEntrance = false
    }

    if (showEntrance) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = "Emergency Response",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    } else {
        // Pass the role through to HomeScreen (from HomeScreen.kt) so role-scoped UI works
        HomeScreen(responderRole = responderRole)
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencyResponsePreview() {
    EmergencyResponseAppTheme {
        EmergencyResponseScreen(onProceed = {})
    }
}
