@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
package com.ers.emergencyresponseapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.viewmodel.CoordinationViewModel
import com.ers.emergencyresponseapp.coordination.voice.VoiceRecorder
import com.ers.emergencyresponseapp.coordination.voice.VoiceRecording
import com.ers.emergencyresponseapp.coordination.voice.VoicePlaybackCache
import android.media.AudioManager
import android.media.MediaPlayer
import com.ers.emergencyresponseapp.data.OperationalRepository
import com.ers.emergencyresponseapp.notification.NotificationDestination
import com.ers.emergencyresponseapp.notification.NotificationNavigation
import com.ers.emergencyresponseapp.ui.components.AppPullToRefresh
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.lazy.itemsIndexed
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import org.json.JSONObject


// ─────────────────────────────────────────────────────────────────────────────
//  COLORS
// ─────────────────────────────────────────────────────────────────────────────

private val BrandGreen      get() = Color(0xFF4C8A89)
private val BgPage          get() = if (ThemeController.isDarkMode.value) Color(0xFF0A0A0A) else Color(0xFFF2F4F7)
private val BgCard          get() = if (ThemeController.isDarkMode.value) Color(0xFF16181D) else Color(0xFFFFFFFF)
private val BgElevated      get() = if (ThemeController.isDarkMode.value) Color(0xFF202329) else Color(0xFFFFFFFF)
private val BgMuted         get() = if (ThemeController.isDarkMode.value) Color(0xFF202329) else Color(0xFFF7F8FA)
private val BgInput         get() = if (ThemeController.isDarkMode.value) Color(0xFF111318) else Color(0xFFF0F2F5)
private val BgChat          get() = if (ThemeController.isDarkMode.value) Color(0xFF0A0A0A) else Color(0xFFF0F2F5)
private val TextPrimary     get() = if (ThemeController.isDarkMode.value) Color(0xFFFAFAFA) else Color(0xFF0D0D0D)
private val TextSecondary   get() = if (ThemeController.isDarkMode.value) Color(0xFFB2B4BC) else Color(0xFF65676B)
private val TextTertiary    get() = if (ThemeController.isDarkMode.value) Color(0xFF858A94) else Color(0xFF8A8F98)
private val DividerColor    get() = if (ThemeController.isDarkMode.value) Color(0xFF2C3037) else Color(0xFFE4E6EA)
private val BorderStrong    get() = if (ThemeController.isDarkMode.value) Color(0xFF3A3F48) else Color(0xFFD5D9E0)
private val OnlineDot       get() = Color(0xFF31A24C)
private val UnreadBadge     get() = Color(0xFFE41E3F)
private val OwnBubble       get() = Color(0xFF4C8A89)
private val PeerBubble      get() = if (ThemeController.isDarkMode.value) Color(0xFF1B1E24) else Color(0xFFFFFFFF)
private val SuccessColor    get() = if (ThemeController.isDarkMode.value) Color(0xFF74D69A) else Color(0xFF16794B)
private val SuccessSurface  get() = if (ThemeController.isDarkMode.value) Color(0xFF153425) else Color(0xFFE4F6EC)
private val WarningColor    get() = if (ThemeController.isDarkMode.value) Color(0xFFFFC36A) else Color(0xFFA45B00)
private val WarningSurface  get() = if (ThemeController.isDarkMode.value) Color(0xFF3A2A14) else Color(0xFFFFF2DE)
private val DangerColor     get() = if (ThemeController.isDarkMode.value) Color(0xFFFF8A89) else Color(0xFFB3261E)
private val DangerSurface   get() = if (ThemeController.isDarkMode.value) Color(0xFF3A1E20) else Color(0xFFFFE9E8)
private val InfoSurface     get() = if (ThemeController.isDarkMode.value) Color(0xFF152D32) else Color(0xFFEAF7F7)

// ─────────────────────────────────────────────────────────────────────────────
//  FIXES APPLIED:
//  1. Removed dead import: com.ers.emergencyresponseapp.firebase.model.ResponderProfile
//  2. Removed orphaned ResponderCard composable (wrong model + dark theme + unconnected)
//  3. Added correct FirebaseResponder model matching your actual DB fields
//  4. Added FirebaseResponderRepository listening to "users/" node
//  5. Added FirebaseResponderViewModel driving the responder directory
//  6. Opens the responder directory from the pencil action instead of a permanent tab
//  7. Reworked portal hierarchy, responsive availability chips, and dark mode
//  8. Added a structured Incident Tip form and view-only chat card
//  9. Persists every Incident Tip through the PHP/MySQL API before chat delivery
// 10. Added permission-aware "Use current location" autofill for Incident Tips
// ─────────────────────────────────────────────────────────────────────────────

data class FirebaseResponder(
    val uid         : String  = "",
    val userId      : String  = "",
    val fullName    : String  = "",
    val email       : String  = "",
    val department  : String  = "",
    val isOnline    : Boolean = false,
    val lastSeen    : Long    = 0L,
    val onlineUntil : Long    = 0L
)

data class InteragencyGroupDto(
    val id: Int = 0,
    val name: String = "",
    val displayName: String = "",
    val lastMessage: String = "",
    val unreadCount: Int = 0,
    val lastReadId: Int = 0
)

class FirebaseResponderRepository {
    private val db = FirebaseDatabase.getInstance().getReference("users")

    fun observeAllResponders(): Flow<List<FirebaseResponder>> = callbackFlow {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val list = snapshot.children.mapNotNull { child ->

                    try {

                        val isOnlineValue = child.child("isOnline").value
                        val lastSeenValue = child.child("lastSeen").value
                        val onlineUntilValue = child.child("onlineUntil").value

                        FirebaseResponder(

                            uid = child.key ?: "",

                            userId = child.child("userId")
                                .getValue(String::class.java) ?: "",

                            fullName = child.child("fullName")
                                .getValue(String::class.java) ?: "",

                            email = child.child("email")
                                .getValue(String::class.java) ?: "",

                            department = child.child("department")
                                .getValue(String::class.java) ?: "",

                            isOnline = when (isOnlineValue) {
                                is Boolean -> isOnlineValue
                                is String -> isOnlineValue.toBoolean()
                                is Number -> isOnlineValue.toInt() != 0
                                else -> false
                            },

                            lastSeen = when (lastSeenValue) {
                                is Long -> lastSeenValue
                                is Int -> lastSeenValue.toLong()
                                is Double -> lastSeenValue.toLong()
                                is String -> lastSeenValue.toLongOrNull() ?: 0L
                                else -> 0L
                            },

                            onlineUntil = when (onlineUntilValue) {
                                is Long -> onlineUntilValue
                                is Int -> onlineUntilValue.toLong()
                                is Double -> onlineUntilValue.toLong()
                                is String -> onlineUntilValue.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                        )

                    } catch (e: Exception) {

                        e.printStackTrace()
                        null
                    }
                }

                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {

                error.toException().printStackTrace()

                trySend(emptyList())
            }
        }

        db.addValueEventListener(listener)

        awaitClose {
            db.removeEventListener(listener)
        }
    }
}

class FirebaseResponderViewModel : ViewModel() {
    private val repo = FirebaseResponderRepository()

    private val _responders = MutableStateFlow<List<FirebaseResponder>>(emptyList())
    val responders: StateFlow<List<FirebaseResponder>> = _responders

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            repo.observeAllResponders().collect { list ->
                _responders.value = list
                _isLoading.value  = false
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private fun roleColor(role: String): Color = when (role.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}

private fun deptColor(dept: String): Color = when (dept.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}

private fun FirebaseResponder.toResponderBrief(): ResponderBrief {
    val effectivelyOnline = isOnline && (
        onlineUntil <= 0L || onlineUntil > System.currentTimeMillis()
    )
    return ResponderBrief(
        id = uid.ifBlank { userId },
        fullName = fullName.ifBlank { email.ifBlank { "Unknown" } },
        username = email.ifBlank { fullName },
        role = department.lowercase(),
        status = if (effectivelyOnline) "online" else "offline",
        lastMessage = "Tap to coordinate",
        lastMessageTime = 0L,
        unreadCount = 0
    )
}

private fun roleInitials(name: String) =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

private fun resolveOperationalFileUrl(rawValue: String): String {
    val value = rawValue.trim()
    if (value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
    ) {
        return value
    }
    return BuildConfig.BASE_URL.trimEnd('/') + "/" + value.trimStart('/')
}

private enum class NavState { INBOX, CHAT }


// Structured chat projection of an authoritative server-side incident-tip record.
// The complete record is written to MySQL before this card is delivered to chat.
private const val TIP_MESSAGE_PREFIX = "ERS_COORDINATION_TIP_V1:"

private data class CoordinationTipPayload(
    val serverId: Long = 0L,
    val tipId: String,
    val createdAt: Long,
    val incidentType: String,
    val priority: String,
    val location: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val contactNumber: String,
    val description: String,
    val reasonForPoliceBackup: String,
    val senderName: String,
    val status: String = "PENDING"
)

private fun CoordinationTipPayload.toWireMessage(): String {
    val json = JSONObject().apply {
        put("serverId", serverId)
        put("tipId", tipId)
        put("createdAt", createdAt)
        put("incidentType", incidentType)
        put("priority", priority)
        put("location", location)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("contactNumber", contactNumber)
        put("description", description)
        put("reasonForPoliceBackup", reasonForPoliceBackup)
        put("senderName", senderName)
        put("status", status)
    }.toString()

    val encoded = Base64.encodeToString(
        json.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP
    )
    return TIP_MESSAGE_PREFIX + encoded
}

private fun parseCoordinationTip(rawText: String?): CoordinationTipPayload? {
    val textValue = rawText?.trim().orEmpty()
    val prefixIndex = textValue.indexOf(TIP_MESSAGE_PREFIX)
    if (prefixIndex < 0) return null

    return runCatching {
        val encoded = textValue
            .substring(prefixIndex + TIP_MESSAGE_PREFIX.length)
            .trimStart()
            .takeWhile { !it.isWhitespace() }
        val jsonText = String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
        val json = JSONObject(jsonText)

        CoordinationTipPayload(
            serverId = json.optLong("serverId", 0L),
            tipId = json.optString("tipId").trim(),
            createdAt = json.optLong("createdAt", 0L),
            incidentType = json.optString("incidentType", "General").trim(),
            priority = json.optString("priority", "Medium").trim(),
            location = json.optString("location").trim(),
            latitude = json.optDouble("latitude").takeUnless { it.isNaN() },
            longitude = json.optDouble("longitude").takeUnless { it.isNaN() },
            contactNumber = json.optString("contactNumber").trim(),
            description = json.optString("description").trim(),
            reasonForPoliceBackup = json.optString("reasonForPoliceBackup").trim(),
            senderName = json.optString("senderName").trim(),
            status = json.optString("status", "PENDING").trim().ifBlank { "PENDING" }
        ).takeIf {
            it.tipId.isNotBlank() &&
                    it.location.isNotBlank() &&
                    it.description.isNotBlank()
        }
    }.getOrNull()
}

private fun coordinationMessagePreview(rawText: String): String {
    val tip = parseCoordinationTip(rawText)
    if (tip != null) return "Incident tip • ${tip.incidentType} • ${tip.location}"
    if (rawText.contains(TIP_MESSAGE_PREFIX)) return "Incident tip • Unable to display details"
    return rawText
}

private fun generateTipReference(now: Long = System.currentTimeMillis()): String {
    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(now))
    val uniquePart = UUID.randomUUID().toString().replace("-", "").take(10).uppercase(Locale.US)
    return "TIP-$year-$uniquePart"
}

private fun formatTipTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    return SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault())
        .format(Date(timestamp))
}

private fun tipTypeColor(type: String): Color = when {
    type.contains("fire", ignoreCase = true) -> Color(0xFFE45A3C)
    type.contains("medical", ignoreCase = true) || type.contains("ems", ignoreCase = true) -> Color(0xFF2E8BCE)
    type.contains("police", ignoreCase = true) || type.contains("crime", ignoreCase = true) -> Color(0xFF6B67C7)
    else -> BrandGreen
}

private fun tipPriorityColor(priority: String): Color = when {
    priority.equals("high", ignoreCase = true) -> Color(0xFFD14343)
    priority.equals("low", ignoreCase = true) -> Color(0xFF2E8B57)
    else -> Color(0xFFB86B12)
}

private fun hasTipLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

private fun isTipLocationServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return false

    return runCatching {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
}

private fun formatTipCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

@Suppress("DEPRECATION")
private fun reverseGeocodeTipLocation(
    context: Context,
    latitude: Double,
    longitude: Double
): String? {
    if (!Geocoder.isPresent()) return null

    return runCatching {
        val address = Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?: return@runCatching null

        address.getAddressLine(0)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                address.featureName,
                address.subThoroughfare,
                address.thoroughfare,
                address.subLocality,
                address.locality,
                address.adminArea,
                address.postalCode,
                address.countryName
            )
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun buildCurrentTipLocationText(
    address: String?,
    latitude: Double,
    longitude: Double
): String {
    val coordinates = formatTipCoordinates(latitude, longitude)
    return if (address.isNullOrBlank()) {
        "GPS: $coordinates"
    } else {
        "$address • GPS: $coordinates"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ROOT SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CoordinationPortalScreen(
    currentResponderId  : String,
    currentResponderName: String,
    currentResponderRole: String,
    navController       : NavHostController? = null,
    onChatModeChange    : (Boolean) -> Unit = {}
) {
    val vm: CoordinationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val notificationDestination by NotificationNavigation.destination.collectAsState()
    var navState by remember { mutableStateOf(NavState.INBOX) }
    // Keep inbox tab across CHAT <-> INBOX transitions.
    var inboxTabIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(navState) {
        AppScreenTracker.currentScreen = "COORDINATION"
        if (navState == NavState.INBOX) {
            AppScreenTracker.currentThreadId = null
        }
        onChatModeChange(navState == NavState.CHAT)
    }

    val onOpenChat: (ResponderBrief?, DepartmentInfo?) -> Unit = { res, dept ->
        // Remember where the chat was opened from so Back returns to that tab.
        if (dept != null) inboxTabIndex = 1 else if (res != null) inboxTabIndex = 0
        if (res != null)       vm.selectResponderAndLoadHistory(currentResponderId, res)
        else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
        navState = NavState.CHAT
    }

    LaunchedEffect(currentResponderId) {
        vm.connectRealtime(currentResponderId, currentResponderName, currentResponderRole)
    }

    // Consume notification taps only after the requested responder/group exists
    // in the live inbox. This makes PM and department alerts open the exact chat.
    LaunchedEffect(
        notificationDestination,
        vm.responders.size,
        vm.departments.size,
        currentResponderId
    ) {
        when (val destination = notificationDestination) {
            is NotificationDestination.PrivateChat -> {
                val responder = vm.responders.firstOrNull { it.id == destination.peerId }
                if (responder != null) {
                    onOpenChat(responder, null)
                    NotificationNavigation.clear(destination)
                }
            }

            is NotificationDestination.DepartmentChat -> {
                val department = vm.departments.firstOrNull {
                    it.name == destination.groupId.toString() && it.isMember
                }
                if (department != null) {
                    onOpenChat(null, department)
                    NotificationNavigation.clear(destination)
                }
            }

            else -> Unit
        }
    }

    DisposableEffect(currentResponderId) {
        onDispose {
            // Leaving Coordination removes only its listeners. Online/offline
            // availability is managed at app-session level so Back/Home cannot
            // accidentally make the responder unassignable.
            vm.disconnectRealtime()
        }
    }

    BackHandler(enabled = navState == NavState.CHAT) {
        navState = NavState.INBOX
    }

    AnimatedContent(
        modifier       = Modifier.fillMaxSize(),
        targetState    = navState,
        transitionSpec = {
            if (targetState == NavState.CHAT)
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
            else
                slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
        },
        label = "nav"
    ) { state ->
        when (state) {
            NavState.INBOX -> InboxScreen(
                vm                   = vm,
                onOpenChat           = onOpenChat,
                currentResponderRole = currentResponderRole,
                currentResponderId   = currentResponderId,
                navController        = navController,
                initialTabIndex      = inboxTabIndex,
                onTabIndexChanged    = { inboxTabIndex = it }
            )
            NavState.CHAT -> ChatScreen(
                vm                   = vm,
                currentResponderId   = currentResponderId,
                currentResponderName = currentResponderName,
                onBack               = {
                    inboxTabIndex = if (vm.selectedDepartment.value != null) 1 else 0
                    navState = NavState.INBOX
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  INBOX SCREEN  — Chats and Departments; responder directory opens from the pencil FAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InboxScreen(
    vm                  : CoordinationViewModel,
    currentResponderRole: String,
    currentResponderId  : String,
    navController       : NavHostController?,
    onOpenChat          : (ResponderBrief?, DepartmentInfo?) -> Unit,
    initialTabIndex     : Int,
    onTabIndexChanged   : (Int) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 1)) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var showResponderDirectory by remember { mutableStateOf(false) }
    var responderDirectoryQuery by remember { mutableStateOf("") }
    val responderDirectorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    fun refreshInbox() {
        if (isRefreshing) return
        isRefreshing = true
        vm.refreshInbox(currentResponderId) { errorMessage ->
            isRefreshing = false
            if (!errorMessage.isNullOrBlank()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val chatListState = rememberLazyListState()
    val departmentListState = rememberLazyListState()

    LaunchedEffect(initialTabIndex) {
        val desired = initialTabIndex.coerceIn(0, 1)
        if (tabIndex != desired) tabIndex = desired
    }
    LaunchedEffect(tabIndex) { onTabIndexChanged(tabIndex) }

    val responders = vm.responders
    val departments = vm.departments
    val totalUnread = responders.sumOf { it.unreadCount } + departments.sumOf { it.unreadCount }

    val fbVm: FirebaseResponderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val firebaseResponders by fbVm.responders.collectAsState()

    val searchHint = if (tabIndex == 0) {
        "Search recent chats"
    } else {
        "Search departments"
    }

    val portalSubtitle = currentResponderRole
        .takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
        ?.let { "$it responder network" }
        ?: "Inter-agency responder network"

    // Chats contains only actual conversation threads. The pencil action opens
    // the responder directory for starting a new private conversation.
    val recentConversations = responders
        .filter { responder ->
            val name = responder.fullName.trim()
            val role = responder.role.trim()
            val hasConversation = responder.lastMessageTime > 0L ||
                    responder.lastMessage.isNotBlank() ||
                    responder.unreadCount > 0
            val matchesSearch = searchQuery.isBlank() ||
                    name.contains(searchQuery, ignoreCase = true) ||
                    role.contains(searchQuery, ignoreCase = true) ||
                    coordinationMessagePreview(responder.lastMessage)
                        .contains(searchQuery, ignoreCase = true)

            name.isNotBlank() &&
                    !name.equals("Unknown", ignoreCase = true) &&
                    hasConversation &&
                    matchesSearch
        }
        .sortedWith(
            compareByDescending<ResponderBrief> { it.lastMessageTime }
                .thenByDescending { it.unreadCount }
                .thenBy { it.fullName.lowercase(Locale.getDefault()) }
        )

    val latestConversationKey = recentConversations.firstOrNull()?.let {
        "${it.id}:${it.lastMessageTime}:${it.lastMessage.hashCode()}"
    }

    // Lazy lists keep the same keyed row visible when an item is inserted above
    // it. Explicitly return to index zero whenever the newest conversation
    // changes so the latest thread is always the first visible chat.
    LaunchedEffect(tabIndex, latestConversationKey) {
        if (tabIndex == 0 && recentConversations.isNotEmpty()) {
            chatListState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = BgCard,
                    shadowElevation = 1.dp,
                    border = BorderStroke(0.5.dp, DividerColor)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (navController != null) {
                                IconButton(
                                    onClick = { navController.navigateUp() },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TextPrimary
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(BrandGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = BrandGreen,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Coordination Portal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (totalUnread > 0) {
                                        "$totalUnread unread • $portalSubtitle"
                                    } else {
                                        portalSubtitle
                                    },
                                    fontSize = 11.sp,
                                    color = if (totalUnread > 0) BrandGreen else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = SuccessSurface,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.30f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(OnlineDot)
                                    )
                                    Text(
                                        "LIVE",
                                        color = SuccessColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(searchHint, fontSize = 13.sp, color = TextSecondary)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 7.dp),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput,
                                focusedBorderColor = BrandGreen,
                                unfocusedBorderColor = DividerColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = BrandGreen,
                                focusedPlaceholderColor = TextSecondary,
                                unfocusedPlaceholderColor = TextSecondary
                            )
                        )

                        DepartmentStatusCard(
                            responders = firebaseResponders.filter { responder ->
                                val name = responder.fullName.trim()
                                val dept = responder.department.trim()
                                val isMe = responder.uid == currentResponderId ||
                                        responder.userId == currentResponderId
                                val isValidResponder = name.isNotBlank() &&
                                        !name.equals("Unknown", ignoreCase = true) &&
                                        dept.isNotBlank()

                                !isMe && isValidResponder
                            }
                        )

                        TabRow(
                            selectedTabIndex = tabIndex,
                            containerColor = BgCard,
                            contentColor = BrandGreen,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                                    height = 3.dp,
                                    color = BrandGreen
                                )
                            },
                            divider = { HorizontalDivider(color = DividerColor) }
                        ) {
                            Tab(
                                selected = tabIndex == 0,
                                onClick = {
                                    tabIndex = 0
                                    searchQuery = ""
                                },
                                selectedContentColor = BrandGreen,
                                unselectedContentColor = TextSecondary,
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            "Chats",
                                            fontWeight = if (tabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        val unread = responders.sumOf { it.unreadCount }
                                        if (unread > 0) UnreadPill(count = unread)
                                    }
                                }
                            )

                            Tab(
                                selected = tabIndex == 1,
                                onClick = {
                                    tabIndex = 1
                                    searchQuery = ""
                                },
                                selectedContentColor = BrandGreen,
                                unselectedContentColor = TextSecondary,
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Groups,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            "Departments",
                                            fontWeight = if (tabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        val unread = departments.sumOf { it.unreadCount }
                                        if (unread > 0) UnreadPill(count = unread)
                                    }
                                }
                            )

                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        responderDirectoryQuery = ""
                        showResponderDirectory = true
                    },
                    containerColor = BrandGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Open responder directory")
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            containerColor = BgPage
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = ::refreshInbox,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                indicatorTopPadding = 10.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgPage)
                        .clipToBounds()
                ) {
                    when (tabIndex) {
                        0 -> {
                            if (recentConversations.isEmpty()) {
                                EmptySearch(
                                    modifier = Modifier.fillMaxSize(),
                                    title = if (searchQuery.isBlank()) {
                                        "No recent conversations"
                                    } else {
                                        "No matching conversations"
                                    },
                                    message = if (searchQuery.isBlank()) {
                                        "Tap the pencil button to start a responder chat."
                                    } else {
                                        "Try another responder name, role, or message."
                                    }
                                )
                            } else {
                                LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 92.dp)
                                ) {
                                    item(key = "recent_conversations_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgPage)
                                                .padding(horizontal = 16.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Recent conversations",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Latest first",
                                                color = BrandGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    items(
                                        items = recentConversations,
                                        key = {
                                            it.id.ifBlank {
                                                "responder_${it.username}_${it.fullName}"
                                            }
                                        }
                                    ) { responder ->
                                        ResponderRow(
                                            responder = responder,
                                            onClick = { onOpenChat(responder, null) }
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            val filteredDepartments = departments
                                .filter {
                                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                                            it.name.contains(searchQuery, ignoreCase = true)
                                }
                                .sortedWith(
                                    compareByDescending<DepartmentInfo> { it.unreadCount }
                                        .thenByDescending { it.lastMessageTime }
                                        .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                                )

                            if (filteredDepartments.isEmpty()) {
                                EmptySearch(
                                    modifier = Modifier.fillMaxSize(),
                                    title = "No departments found",
                                    message = "Try another department name."
                                )
                            } else {
                                LazyColumn(
                                    state = departmentListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp)
                                ) {
                                    items(
                                        items = filteredDepartments,
                                        key = { it.name.ifBlank { "department_${it.displayName}" } }
                                    ) { department ->
                                        DepartmentRow(
                                            dept = department,
                                            currentResponderId = currentResponderId,
                                            vm = vm,
                                            onOpenChat = { onOpenChat(null, department) }
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }

        if (showResponderDirectory) {
            ModalBottomSheet(
                onDismissRequest = {
                    showResponderDirectory = false
                    responderDirectoryQuery = ""
                },
                sheetState = responderDirectorySheetState,
                containerColor = BgPage,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(BrandGreen.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PersonSearch,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Start responder chat",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                "Choose a responder from the directory",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                showResponderDirectory = false
                                responderDirectoryQuery = ""
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close responder directory",
                                tint = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = responderDirectoryQuery,
                        onValueChange = { responderDirectoryQuery = it },
                        placeholder = {
                            Text("Search responders or email", color = TextSecondary)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                        },
                        trailingIcon = if (responderDirectoryQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { responderDirectoryQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear responder search",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput,
                            focusedBorderColor = BrandGreen,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandGreen
                        )
                    )

                    HorizontalDivider(color = DividerColor)

                    AllRespondersTab(
                        vm = fbVm,
                        searchQuery = responderDirectoryQuery,
                        currentResponderId = currentResponderId,
                        modifier = Modifier.weight(1f),
                        onResponderClick = { responder ->
                            showResponderDirectory = false
                            responderDirectoryQuery = ""
                            onOpenChat(responder.toResponderBrief(), null)
                        }
                    )
                }
            }
        }

        NotificationOverlay(
            vm = vm,
            onTap = { responder, department -> onOpenChat(responder, department) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ALL RESPONDERS TAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AllRespondersTab(
    vm          : FirebaseResponderViewModel,
    searchQuery : String,
    currentResponderId: String,
    modifier    : Modifier = Modifier,
    onResponderClick: (FirebaseResponder) -> Unit
) {
    val allResponders by vm.responders.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()

    val filtered = allResponders.filter { responder ->
        val name = responder.fullName.trim()
        val dept = responder.department.trim()

        val isMe =
            responder.uid == currentResponderId ||
                    responder.userId == currentResponderId

        val isValidResponder =
            name.isNotBlank() &&
                    !name.equals("Unknown", ignoreCase = true) &&
                    dept.isNotBlank()

        val matchesSearch =
            name.contains(searchQuery, ignoreCase = true) ||
                    dept.contains(searchQuery, ignoreCase = true) ||
                    responder.email.contains(searchQuery, ignoreCase = true)

        !isMe && isValidResponder && matchesSearch
    }

    when {
        isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandGreen, strokeWidth = 2.dp)
        }
        filtered.isEmpty() -> EmptySearch(
            modifier = modifier,
            title = "No responders found",
            message = "Try another name, department, or email."
        )
        else -> {
            val onlineCount = filtered.count { it.isOnline }
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "${filtered.size} responder${if (filtered.size != 1) "s" else ""}",
                            fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OnlineDot))
                            Text("$onlineCount online", fontSize = 13.sp, color = OnlineDot, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(color = DividerColor)
                }
                items(
                    items = filtered,
                    key = {
                        it.uid.ifBlank {
                            it.userId.ifBlank { "responder_${it.email}_${it.fullName}" }
                        }
                    }
                ) { responder ->
                    FirebaseResponderRow(
                        responder = responder,
                        onClick = { onResponderClick(responder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FirebaseResponderRow(
    responder: FirebaseResponder,
    onClick: () -> Unit
) {
    val lastSeenText = remember(responder.lastSeen) {

        try {

            if (responder.lastSeen > 0L) {

                "Last seen ${
                    SimpleDateFormat(
                        "MMM dd, hh:mm a",
                        Locale.getDefault()
                    ).format(Date(responder.lastSeen))
                }"

            } else {

                "Last seen: unknown"
            }

        } catch (e: Exception) {

            "Last seen: unknown"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = BgCard
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(deptColor(responder.department).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = roleInitials(responder.fullName).ifEmpty { "?" },
                        fontSize   = (52 * 0.34f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = deptColor(responder.department)
                    )
                }
                if (responder.isOnline) {
                    Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).clip(CircleShape).background(BgCard).padding(2.dp).clip(CircleShape).background(OnlineDot))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = responder.fullName.ifBlank { "Unknown" },
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Surface(color = deptColor(responder.department).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = responder.department.replaceFirstChar { it.uppercase() }.ifBlank { "No dept." },
                        fontSize = 10.sp, color = deptColor(responder.department),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(text = responder.email, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!responder.isOnline) {
                    Text(text = lastSeenText, fontSize = 11.sp, color = TextTertiary)
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (responder.isOnline) SuccessSurface else BgMuted,
                border = BorderStroke(
                    1.dp,
                    if (responder.isOnline) SuccessColor.copy(alpha = 0.24f) else DividerColor
                )
            ) {
                Text(
                    text = if (responder.isOnline) "● Online" else "○ Offline",
                    color = if (responder.isOnline) SuccessColor else TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = DividerColor, thickness = 0.5.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
//  ALL BELOW UNCHANGED FROM ORIGINAL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResponderRow(responder: ResponderBrief, onClick: () -> Unit) {
    val online = responder.status.contains("online", ignoreCase = true)
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = BgCard) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp)) {
                AvatarCircle(name = responder.fullName, role = responder.role, size = 52.dp)
                if (online) {
                    Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).clip(CircleShape).background(BgCard).padding(2.dp).clip(CircleShape).background(OnlineDot))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        responder.fullName,
                        fontWeight = if (responder.unreadCount > 0)
                            FontWeight.Bold
                        else
                            FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        formatChatTime(responder.lastMessageTime),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoleBadge(role = responder.role)
                    Text(
                        text = if (responder.lastMessage.isNotBlank()) {
                            coordinationMessagePreview(responder.lastMessage)
                        } else {
                            "Tap to chat"
                        },
                        fontSize   = 13.sp,
                        color      = if (responder.unreadCount > 0) TextPrimary else TextSecondary,
                        fontWeight = if (responder.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        maxLines   = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (responder.unreadCount > 0) UnreadBadgeCircle(count = responder.unreadCount)
            else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = DividerColor, thickness = 0.5.dp)
}

@Composable
private fun DepartmentRow(
    dept: DepartmentInfo,
    currentResponderId: String,
    vm: CoordinationViewModel,
    onOpenChat: () -> Unit
) {
    val isMember = dept.isMember
    val isPending = dept.requestPending

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isMember) {
                        onOpenChat()
                    } else if (!isPending) {
                        vm.requestGroupAccess(
                            groupId = dept.name.toInt(),
                            userId = currentResponderId.toInt()
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(roleColor(dept.displayName).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        dept.displayName.contains("fire", true) -> Icons.Default.LocalFireDepartment
                        dept.displayName.contains("medical", true) -> Icons.Default.LocalHospital
                        dept.displayName.contains("police", true) -> Icons.Default.Security
                        else -> Icons.Default.Groups
                    },
                    contentDescription = null,
                    tint = roleColor(dept.displayName),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dept.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )

                Text(
                    dept.lastMessage,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (isMember) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
            } else {
                Text(
                    if (isPending) "Pending" else "Request",
                    color = BrandGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

@Composable
private fun ChatScreen(
    vm: CoordinationViewModel,
    currentResponderId: String,
    currentResponderName: String,
    onBack: () -> Unit
) {
    val selectedResponder  = vm.selectedResponder.value
    val selectedDepartment = vm.selectedDepartment.value
    val messages           = vm.messages
    val isPeerTyping       = vm.isPeerTyping
    val messageInput       = remember { mutableStateOf("") }
    val listState          = rememberLazyListState()
    val scope              = rememberCoroutineScope()
    val unseenCount        = remember { mutableIntStateOf(0) }
    var didInitialAutoScroll by remember(selectedResponder?.id, selectedDepartment?.name) { mutableStateOf(false) }
    val timeFmt            = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showAttach         = remember { mutableStateOf(false) }
    var showQuickReplies by remember { mutableStateOf(false) }
    var showTipForm by remember { mutableStateOf(false) }
    var isSendingTip by remember { mutableStateOf(false) }
    var selectedTipDetails by remember { mutableStateOf<CoordinationTipPayload?>(null) }
    val operationalRepository = remember { OperationalRepository() }
    val showInfoDialog     = remember { mutableStateOf(false) }
    val showSharedFilesDialog = remember { mutableStateOf(false) }
    var showChatSearch by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    val ctx                = LocalContext.current
    val chatName           = selectedResponder?.fullName ?: selectedDepartment?.displayName ?: "Chat"
    var liveIsOnline by remember(selectedResponder?.id) {
        mutableStateOf(selectedResponder?.status?.contains("online", ignoreCase = true) == true)
    }

    var liveLastSeen by remember(selectedResponder?.id) {
        mutableLongStateOf(0L)
    }

    DisposableEffect(vm.activeThreadId) {
        val visibleThread = vm.activeThreadId
        AppScreenTracker.currentThreadId = visibleThread
        onDispose {
            if (AppScreenTracker.currentThreadId == visibleThread) {
                AppScreenTracker.currentThreadId = null
            }
        }
    }

    DisposableEffect(selectedResponder?.id) {
        val responderId = selectedResponder?.id

        if (responderId.isNullOrBlank()) {
            onDispose { }
        } else {
            val userRef = FirebaseDatabase.getInstance()
                .reference
                .child("users")
                .child(responderId)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val onlineFlag = when (val value = snapshot.child("isOnline").value) {
                        is Boolean -> value
                        is String -> value.toBoolean()
                        is Number -> value.toInt() != 0
                        else -> false
                    }
                    val onlineUntil = when (val value = snapshot.child("onlineUntil").value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    liveIsOnline = onlineFlag && (
                        onlineUntil <= 0L || onlineUntil > System.currentTimeMillis()
                    )

                    liveLastSeen = when (val value = snapshot.child("lastSeen").value) {
                        is Long -> value
                        is Int -> value.toLong()
                        is Double -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            userRef.addValueEventListener(listener)

            onDispose {
                userRef.removeEventListener(listener)
            }
        }
    }

    val isOnline = liveIsOnline

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "file"
            when {
                selectedResponder  != null -> vm.sendFileMessage(currentResponderId, selectedResponder, uri, name, isImage = false)
                selectedDepartment != null -> vm.sendFileToDepartment(currentResponderId, selectedDepartment.name, uri, name, isImage = false)
                else -> Toast.makeText(ctx, "Select a chat first", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            when {
                selectedResponder  != null -> vm.sendFileMessage(currentResponderId, selectedResponder, uri, "image", isImage = true)
                selectedDepartment != null -> vm.sendFileToDepartment(currentResponderId, selectedDepartment.name, uri, "image", isImage = true)
                else -> Toast.makeText(ctx, "Select a chat first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Voice message recording ────────────────────────────────────────────
    val voiceRecorder = remember { VoiceRecorder(ctx) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var pendingVoiceStart by remember { mutableStateOf(false) }

    fun sendRecording(recording: VoiceRecording) {
        when {
            selectedResponder  != null -> vm.sendVoiceMessage(currentResponderId, selectedResponder, recording)
            selectedDepartment != null -> vm.sendVoiceMessageToDepartment(currentResponderId, selectedDepartment.name, recording)
            else -> {
                recording.file.delete()
                Toast.makeText(ctx, "Select a chat first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun beginVoiceRecording() {
        if (selectedResponder == null && selectedDepartment == null) {
            Toast.makeText(ctx, "Select a chat first", Toast.LENGTH_SHORT).show()
            return
        }
        val started = voiceRecorder.start()
        started.onSuccess {
            isRecordingVoice = true
            recordingElapsedMs = 0L
        }.onFailure { error ->
            Toast.makeText(ctx, error.message ?: "Could not start recording", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancel()
        isRecordingVoice = false
        recordingElapsedMs = 0L
    }

    fun finishVoiceRecording() {
        val result = voiceRecorder.stop()
        isRecordingVoice = false
        recordingElapsedMs = 0L
        result.onSuccess { recording -> sendRecording(recording) }
            .onFailure { error -> Toast.makeText(ctx, error.message ?: "Recording could not be sent", Toast.LENGTH_LONG).show() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingVoiceStart) {
            beginVoiceRecording()
        } else if (!granted) {
            Toast.makeText(ctx, "Microphone permission is required to send voice messages", Toast.LENGTH_LONG).show()
        }
        pendingVoiceStart = false
    }

    fun onMicPressed() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMicPermission) {
            beginVoiceRecording()
        } else {
            pendingVoiceStart = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            val startedAt = System.currentTimeMillis()
            while (isRecordingVoice) {
                recordingElapsedMs = System.currentTimeMillis() - startedAt
                if (recordingElapsedMs >= 120_000L) {
                    finishVoiceRecording()
                    break
                }
                delay(200)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }

    fun doSend() {
        val text = messageInput.value.trim()
        if (text.isEmpty()) return
        when {
            selectedResponder  != null -> vm.sendPrivateMessage(currentResponderId, selectedResponder, text)
            selectedDepartment != null -> vm.sendDepartmentMessage(currentResponderId, selectedDepartment.name, text)
            else -> Toast.makeText(ctx, "Select a chat to send", Toast.LENGTH_SHORT).show()
        }
        messageInput.value = ""
    }

    fun sendTipToCurrentChat(payload: CoordinationTipPayload) {
        if (isSendingTip) return

        val senderUserId = currentResponderId.toIntOrNull()
        val targetResponder = selectedResponder
        val targetDepartment = selectedDepartment
        val recipientType = if (targetDepartment != null) "group" else "private"
        val recipientId = targetDepartment?.name ?: targetResponder?.id

        if (senderUserId == null || senderUserId <= 0 || recipientId.isNullOrBlank()) {
            Toast.makeText(ctx, "A valid responder account and chat are required", Toast.LENGTH_SHORT).show()
            return
        }

        isSendingTip = true
        scope.launch {
            operationalRepository.createCoordinationTip(
                senderUserId = senderUserId,
                senderName = payload.senderName,
                recipientType = recipientType,
                recipientId = recipientId,
                clientReference = payload.tipId,
                incidentType = payload.incidentType,
                priority = payload.priority,
                location = payload.location,
                latitude = payload.latitude,
                longitude = payload.longitude,
                contactNumber = payload.contactNumber,
                description = payload.description,
                policeBackupReason = payload.reasonForPoliceBackup
            ).onSuccess { record ->
                val authoritativePayload = payload.copy(
                    serverId = record.id,
                    tipId = record.referenceNo,
                    createdAt = record.createdAtMillis,
                    latitude = record.latitude,
                    longitude = record.longitude,
                    status = record.status.uppercase(Locale.US)
                )
                val wireMessage = authoritativePayload.toWireMessage()

                when {
                    targetResponder != null ->
                        vm.sendPrivateMessage(currentResponderId, targetResponder, wireMessage)

                    targetDepartment != null ->
                        vm.sendDepartmentMessage(currentResponderId, targetDepartment.name, wireMessage)
                }

                showTipForm = false
                showQuickReplies = false
                Toast.makeText(ctx, "Incident tip saved and sent", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    ctx,
                    "Tip was not sent: ${error.message ?: "server error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
            isSendingTip = false
        }
    }

    val latestMessageKey = messages
        .maxWithOrNull(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
        ?.let { "${it.id}:${it.createdAt}" }

    LaunchedEffect(
        selectedResponder?.id,
        selectedDepartment?.name,
        latestMessageKey,
        messages.size,
        chatSearchQuery
    ) {
        if (messages.isEmpty() || chatSearchQuery.isNotBlank()) return@LaunchedEffect

        // Give LazyColumn one frame to expose the newly loaded item count.
        delay(16L)

        val targetIndex = messages.lastIndex
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val isNearLatest = lastVisibleIndex >= targetIndex - 1 ||
                listState.layoutInfo.totalItemsCount == 0

        when {
            !didInitialAutoScroll -> {
                // Opening a thread always lands on the newest message.
                listState.scrollToItem(targetIndex)
                unseenCount.intValue = 0
                didInitialAutoScroll = true
            }

            isNearLatest -> {
                // Continue following the conversation only while the user is
                // already reading its newest messages.
                listState.animateScrollToItem(targetIndex)
                unseenCount.intValue = 0
            }

            else -> {
                // Preserve the user's position when reading older history.
                unseenCount.intValue += 1
            }
        }
    }
    LaunchedEffect(messages.size, selectedResponder?.id) {
        vm.markMessagesAsRead(
            currentResponderId,
            selectedResponder?.id
        )
    }

    val visibleMessages = if (chatSearchQuery.isBlank()) {
        messages
    } else {
        messages.filter { message ->
            val tip = parseCoordinationTip(message.text)
            val searchableText = if (tip != null) {
                listOf(
                    tip.tipId,
                    tip.incidentType,
                    tip.priority,
                    tip.location,
                    tip.contactNumber,
                    tip.description,
                    tip.reasonForPoliceBackup,
                    tip.senderName
                ).joinToString(" ")
            } else {
                message.text.orEmpty()
            }

            searchableText.contains(chatSearchQuery, ignoreCase = true) ||
                    message.attachmentName?.contains(chatSearchQuery, ignoreCase = true) == true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = BgCard,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }

                        Box(modifier = Modifier.size(40.dp)) {
                            if (selectedResponder != null) {
                                AvatarCircle(
                                    name = selectedResponder.fullName,
                                    role = selectedResponder.role,
                                    size = 40.dp
                                )

                                if (isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(BgCard)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(OnlineDot)
                                    )
                                }
                            } else if (selectedDepartment != null) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            roleColor(selectedDepartment.name)
                                                .copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        selectedDepartment.emoji,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chatName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            when {
                                isPeerTyping -> TypingSubtitle()

                                selectedResponder != null -> Text(
                                    if (isOnline) "Active now"
                                    else formatLastSeenTime(liveLastSeen)
                                        .takeIf { it != "Unknown" }
                                        ?.let { "Last seen $it" }
                                        ?: "Offline",
                                    fontSize = 12.sp,
                                    color = if (isOnline) BrandGreen else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                selectedDepartment != null -> Text(
                                    "Group channel",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showInfoDialog.value = true }
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Chat info",
                                tint = TextSecondary
                            )
                        }
                    }

                    AnimatedVisibility(visible = showChatSearch) {
                        OutlinedTextField(
                            value = chatSearchQuery,
                            onValueChange = { chatSearchQuery = it },
                            placeholder = {
                                Text(
                                    "Search messages...",
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        chatSearchQuery = ""
                                        showChatSearch = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close search",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput,
                                focusedBorderColor = BrandGreen,
                                unfocusedBorderColor = DividerColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = BrandGreen
                            )
                        )
                    }
                    if (!showChatSearch) {
                        CoordinationChannelStrip(
                            recipientLabel = chatName,
                            isDepartment = selectedDepartment != null,
                            onCreateTip = { showTipForm = true }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Keep the composer above the keyboard or system navigation inset
            // without stretching its themed surface into a blank bottom panel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.ime
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom)
                    )
            ) {
                AnimatedVisibility(visible = showQuickReplies) {
                    QuickReplyBar(
                        onSelect = { reply ->
                            when {
                                selectedResponder != null ->
                                    vm.sendPrivateMessage(
                                        currentResponderId,
                                        selectedResponder,
                                        reply
                                    )

                                selectedDepartment != null ->
                                    vm.sendDepartmentMessage(
                                        currentResponderId,
                                        selectedDepartment.name,
                                        reply
                                    )

                                else -> Toast.makeText(
                                    ctx,
                                    "Select a chat first",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                ChatComposer(
                    text = messageInput.value,
                    onTextChange = { messageInput.value = it },
                    onSend = { doSend() },
                    onAttachClick = { showAttach.value = true },
                    onQuickReplyToggle = {
                        showQuickReplies = !showQuickReplies
                    },
                    isRecording = isRecordingVoice,
                    recordingElapsedMs = recordingElapsedMs,
                    onMicClick = { onMicPressed() },
                    onCancelRecording = { cancelVoiceRecording() },
                    onSendRecording = { finishVoiceRecording() },
                    onLike = {
                        when {
                            selectedResponder != null ->
                                vm.sendPrivateMessage(
                                    currentResponderId,
                                    selectedResponder,
                                    "👍"
                                )

                            selectedDepartment != null ->
                                vm.sendDepartmentMessage(
                                    currentResponderId,
                                    selectedDepartment.name,
                                    "👍"
                                )

                            else -> Toast.makeText(
                                ctx,
                                "Select a chat first",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        },
        containerColor = BgChat
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            ChatMessagesPanel(
                messages = visibleMessages,
                timeFmt = timeFmt,
                listState = listState,
                currentResponderId = currentResponderId,
                onReact = { id, emoji ->
                    vm.addReaction(id, emoji, currentResponderId)
                },
                onOpenTip = { payload ->
                    selectedTipDetails = payload
                },
                modifier = Modifier.fillMaxSize()
            )

            if (unseenCount.intValue > 0 && chatSearchQuery.isBlank()) {
                Button(
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                            unseenCount.intValue = 0
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF323232)
                    )
                ) {
                    Text(
                        "↓ ${unseenCount.intValue} new",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    val latest = vm.latestNotification.value
    AnimatedVisibility(visible = latest != null, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            NotificationToast(text = coordinationMessagePreview(latest ?: ""), onDismiss = { vm.clearNotification() })
        }
    }
    LaunchedEffect(latest) { if (latest != null) { delay(5000L); vm.clearNotification() } }

    if (showInfoDialog.value) {
        CoordinationChatInfoDialog(
            onDismiss = { showInfoDialog.value = false }
        ) {
            when {
                selectedResponder != null -> ChatInfoContent(
                    r = selectedResponder,
                    messages = messages,
                    currentResponderId = currentResponderId,
                    operationalRepository = operationalRepository,
                    liveIsOnline = liveIsOnline,
                    liveLastSeen = liveLastSeen,
                    onSearchClick = {
                        showInfoDialog.value = false
                        showChatSearch = true
                    },
                    onFilesClick = {
                        showInfoDialog.value = false
                        showSharedFilesDialog.value = true
                    }
                )

                selectedDepartment != null -> DepartmentChatInfoContent(
                    department = selectedDepartment,
                    messages = messages,
                    onFilesClick = {
                        showInfoDialog.value = false
                        showSharedFilesDialog.value = true
                    }
                )

                else -> Text("No chat information is available.", color = TextSecondary)
            }
        }
    }
    if (showSharedFilesDialog.value) {
        SharedFilesDialog(
            messages = messages,
            onDismiss = {
                showSharedFilesDialog.value = false
            }
        )
    }
    if (showAttach.value) {
        AttachSheet(
            onDismiss = {
                showAttach.value = false
            },
            onPickImage = {
                showAttach.value = false
                imageLauncher.launch("image/*")
            },
            onPickFile = {
                showAttach.value = false
                fileLauncher.launch("*/*")
            },
            onCreateTip = {
                showAttach.value = false
                showTipForm = true
            }
        )
    }

    if (showTipForm) {
        CoordinationTipFormDialog(
            senderName = currentResponderName.ifBlank { "Responder" },
            recipientLabel = chatName,
            isSending = isSendingTip,
            onDismiss = { if (!isSendingTip) showTipForm = false },
            onSend = ::sendTipToCurrentChat
        )
    }

    selectedTipDetails?.let { payload ->
        TipDetailsDialog(
            payload = payload,
            requesterUserId = currentResponderId.toIntOrNull() ?: 0,
            repository = operationalRepository,
            onDismiss = { selectedTipDetails = null }
        )
    }
}

@Composable
private fun ChatMessagesPanel(
    messages: List<ChatMessage>,
    timeFmt: SimpleDateFormat,
    listState: LazyListState,
    currentResponderId: String,
    onReact: (String, String) -> Unit,
    onOpenTip: (CoordinationTipPayload) -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BrandGreen.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = BrandGreen,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "No messages yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Send an update or share a structured incident tip.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val dayLabelFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dayKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    fun dayLabel(ts: Long): String = dayLabelFormat.format(Date(ts))
    fun dayKey(ts: Long): String = dayKeyFormat.format(Date(ts))

    // SnapshotStateList is mutated in place, so derive the ordered list on each
    // recomposition instead of remembering the list reference.
    val orderedMessages = messages.sortedWith(
        compareBy<ChatMessage> { it.createdAt }.thenBy { it.id }
    )

    var revealedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revealedMessageId) {
        val selected = revealedMessageId ?: return@LaunchedEffect
        delay(2500)
        if (revealedMessageId == selected) revealedMessageId = null
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = orderedMessages,
            key = { index, item -> "${item.id}_${item.createdAt}_$index" }
        ) { index, msg ->
            val showDayDivider = index == 0 ||
                    dayKey(msg.createdAt) != dayKey(orderedMessages[index - 1].createdAt)

            if (showDayDivider) {
                DayDivider(label = dayLabel(msg.createdAt))
            }

            ChatBubble(
                msg = msg,
                timeLabel = timeFmt.format(Date(msg.createdAt)),
                currentResponderId = currentResponderId,
                onReact = onReact,
                onOpenTip = onOpenTip,
                isTimeVisible = revealedMessageId == msg.id,
                onToggleTime = {
                    revealedMessageId = if (revealedMessageId == msg.id) null else msg.id
                }
            )
        }
    }
}

private fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

@Composable
private fun DayDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = BgMuted,
            shape = RoundedCornerShape(99.dp),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    timeLabel: String,
    currentResponderId: String,
    onReact: (String, String) -> Unit,
    onOpenTip: (CoordinationTipPayload) -> Unit,
    isTimeVisible: Boolean,
    onToggleTime: () -> Unit
) {
    val isOwn = msg.isOwn || msg.senderId == currentResponderId
    val bubbleColor = if (isOwn) OwnBubble else PeerBubble
    val textColor = if (isOwn) Color.White else TextPrimary
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    val tipPayload = remember(msg.text) { parseCoordinationTip(msg.text) }
    val maximumBubbleWidth = if (tipPayload != null) 360.dp else 280.dp

    var showEmojiPicker by remember(msg.id) { mutableStateOf(false) }
    var showLightbox by remember(msg.id) { mutableStateOf(false) }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = if (isOwn) 4.dp else 18.dp,
        bottomStart = if (isOwn) 18.dp else 4.dp
    )
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            AvatarCircle(
                name = msg.senderName,
                role = msg.role,
                size = 28.dp,
                modifier = Modifier.padding(end = 6.dp, bottom = 18.dp)
            )
        }

        Column(
            horizontalAlignment = alignment,
            modifier = Modifier.widthIn(max = maximumBubbleWidth)
        ) {
            AnimatedVisibility(visible = showEmojiPicker) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, DividerColor),
                    shadowElevation = 10.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("👍", "❤️", "😮", "🔥", "🚨").forEach { emoji ->
                            Text(
                                emoji,
                                fontSize = 22.sp,
                                modifier = Modifier.clickable {
                                    onReact(msg.id, emoji)
                                    showEmojiPicker = false
                                }
                            )
                        }
                    }
                }
            }

            when (msg.type) {
                MessageType.TEXT -> {
                    if (tipPayload != null) {
                        CoordinationTipCard(
                            payload = tipPayload,
                            senderLabel = if (isOwn) "Sent by you" else msg.senderName,
                            modifier = Modifier.combinedClickable(
                                onClick = onToggleTime,
                                onLongClick = {
                                    onToggleTime()
                                    showEmojiPicker = !showEmojiPicker
                                }
                            ),
                            onView = { onOpenTip(tipPayload) }
                        )
                    } else {
                        Surface(
                            color = bubbleColor,
                            shape = bubbleShape,
                            shadowElevation = if (isOwn) 0.dp else 1.dp,
                            modifier = Modifier.combinedClickable(
                                onClick = onToggleTime,
                                onLongClick = {
                                    onToggleTime()
                                    showEmojiPicker = !showEmojiPicker
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                if (!isOwn && msg.senderName.isNotBlank()) {
                                    Text(
                                        msg.senderName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = roleColor(msg.role)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Text(
                                    text = if (msg.text.orEmpty().contains(TIP_MESSAGE_PREFIX)) {
                                        "Incident tip could not be displayed. Please update or resend it."
                                    } else {
                                        msg.text.orEmpty()
                                    },
                                    color = textColor,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                MessageType.IMAGE -> {
                    Surface(
                        color = bubbleColor,
                        shape = bubbleShape,
                        shadowElevation = if (isOwn) 0.dp else 1.dp,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                onToggleTime()
                                showLightbox = true
                            },
                            onLongClick = {
                                onToggleTime()
                                showEmojiPicker = !showEmojiPicker
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (!isOwn && msg.senderName.isNotBlank()) {
                                Text(
                                    msg.senderName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = roleColor(msg.role)
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            msg.attachmentUri?.let { rawUrl ->
                                val finalUrl = resolveOperationalFileUrl(rawUrl)

                                UriImage(
                                    uriString = finalUrl,
                                    contentDescription = "Shared image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .widthIn(min = 180.dp, max = 260.dp)
                                        .heightIn(min = 140.dp, max = 220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }

                            if (!msg.text.isNullOrBlank() && msg.text != "Image") {
                                Spacer(Modifier.height(6.dp))
                                Text(msg.text, color = textColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                MessageType.FILE -> {
                    Surface(
                        color = bubbleColor,
                        shape = bubbleShape,
                        shadowElevation = if (isOwn) 0.dp else 1.dp,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                onToggleTime()
                                msg.attachmentUri?.let { rawUrl ->
                                    try {
                                        val finalUrl = resolveOperationalFileUrl(rawUrl)
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            "No app found to open this file",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onLongClick = {
                                onToggleTime()
                                showEmojiPicker = !showEmojiPicker
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isOwn) Color.White.copy(alpha = 0.2f)
                                        else InfoSurface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (isOwn) Color.White else Color(0xFF3498DB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (!isOwn && msg.senderName.isNotBlank()) {
                                    Text(
                                        msg.senderName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = roleColor(msg.role)
                                    )
                                }
                                Text(
                                    msg.attachmentName ?: "File",
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Tap to open",
                                    color = textColor.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                MessageType.AUDIO -> {
                    VoiceMessageBubble(
                        msg = msg,
                        isOwn = isOwn,
                        bubbleColor = bubbleColor,
                        textColor = textColor,
                        bubbleShape = bubbleShape,
                        onToggleTime = onToggleTime,
                        onLongPress = { showEmojiPicker = !showEmojiPicker }
                    )
                }

                else -> {
                    Surface(color = bubbleColor, shape = bubbleShape) {
                        Text(
                            msg.text.orEmpty(),
                            color = textColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (msg.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    msg.reactions.groupingBy { it.emoji }.eachCount().forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgMuted,
                            border = BorderStroke(1.dp, DividerColor),
                            modifier = Modifier.clickable { onReact(msg.id, emoji) }
                        ) {
                            Text(
                                "$emoji $count",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (tipPayload != null || isTimeVisible || isOwn) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (tipPayload != null || isTimeVisible) {
                        Text(timeLabel, fontSize = 10.sp, color = TextSecondary)
                    }
                    if (isOwn) {
                        val (tick, tickColor) = when (msg.status) {
                            MessageStatus.SENT -> "✓" to TextSecondary
                            MessageStatus.DELIVERED -> "✓✓" to TextSecondary
                            MessageStatus.READ -> "✓✓" to BrandGreen
                            else -> "" to TextSecondary
                        }
                        if (tick.isNotEmpty()) {
                            Text(
                                tick,
                                fontSize = 10.sp,
                                color = tickColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLightbox && msg.attachmentUri != null) {
        val finalUrl = resolveOperationalFileUrl(msg.attachmentUri)

        Dialog(onDismissRequest = { showLightbox = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable { showLightbox = false },
                contentAlignment = Alignment.Center
            ) {
                UriImage(
                    uriString = finalUrl,
                    contentDescription = "Full image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceMessageBubble(
    msg: ChatMessage,
    isOwn: Boolean,
    bubbleColor: Color,
    textColor: Color,
    bubbleShape: RoundedCornerShape,
    onToggleTime: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackCache = remember { VoicePlaybackCache(context) }

    var isLoading by remember(msg.id) { mutableStateOf(false) }
    var isPlaying by remember(msg.id) { mutableStateOf(false) }
    var errorMessage by remember(msg.id) { mutableStateOf<String?>(null) }
    var playbackProgress by remember(msg.id) { mutableFloatStateOf(0f) }
    var mediaPlayer by remember(msg.id) { mutableStateOf<MediaPlayer?>(null) }

    val declaredDurationMs = msg.audioDurationMs ?: 0L

    fun releasePlayer() {
        mediaPlayer?.let { player ->
            try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
            player.release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    DisposableEffect(msg.id) {
        onDispose { releasePlayer() }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        val player = mediaPlayer
        if (isPlaying && player != null) {
            while (isPlaying) {
                try {
                    val duration = player.duration.takeIf { it > 0 } ?: declaredDurationMs.toInt()
                    if (duration > 0) {
                        playbackProgress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                    }
                } catch (_: Exception) { }
                delay(150)
            }
        }
    }

    fun togglePlayback() {
        val existingPlayer = mediaPlayer
        if (existingPlayer != null) {
            if (existingPlayer.isPlaying) {
                existingPlayer.pause()
                isPlaying = false
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                    Toast.makeText(context, "Media volume is muted", Toast.LENGTH_SHORT).show()
                }
                existingPlayer.start()
                isPlaying = true
            }
            return
        }

        val rawUrl = msg.attachmentUri ?: return
        val finalUrl = resolveOperationalFileUrl(rawUrl)
        if (!finalUrl.startsWith("http", ignoreCase = true)) {
            errorMessage = "Voice message is not yet available"
            return
        }

        isLoading = true
        errorMessage = null
        scope.launch {
            val result = playbackCache.resolve(finalUrl, msg.attachmentMimeType)
            isLoading = false
            result.onSuccess { cachedFile ->
                try {
                    val player = MediaPlayer().apply {
                        setDataSource(cachedFile.absolutePath)
                        setOnCompletionListener {
                            isPlaying = false
                            playbackProgress = 0f
                        }
                        prepare()
                    }
                    mediaPlayer = player
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        Toast.makeText(context, "Media volume is muted", Toast.LENGTH_SHORT).show()
                    }
                    player.start()
                    isPlaying = true
                } catch (e: Exception) {
                    errorMessage = "Could not play voice message"
                }
            }.onFailure { error ->
                errorMessage = error.message ?: "Could not download voice message"
            }
        }
    }

    Surface(
        color = bubbleColor,
        shape = bubbleShape,
        shadowElevation = if (isOwn) 0.dp else 1.dp,
        modifier = Modifier.combinedClickable(
            onClick = onToggleTime,
            onLongClick = onLongPress
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            if (!isOwn && msg.senderName.isNotBlank()) {
                Text(
                    msg.senderName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = roleColor(msg.role)
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.widthIn(min = 170.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOwn) Color.White.copy(alpha = 0.2f) else InfoSurface
                        )
                        .clickable(enabled = !isLoading) { togglePlayback() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (isOwn) Color.White else BrandGreen
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play voice message",
                            tint = if (isOwn) Color.White else Color(0xFF3498DB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { playbackProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (isOwn) Color.White else BrandGreen,
                        trackColor = (if (isOwn) Color.White else BrandGreen).copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(4.dp))
                    val label = errorMessage ?: run {
                        val totalSeconds = declaredDurationMs / 1000
                        if (totalSeconds > 0) {
                            String.format(Locale.US, "Voice message · %d:%02d", totalSeconds / 60, totalSeconds % 60)
                        } else {
                            "Voice message"
                        }
                    }
                    Text(
                        label,
                        color = if (errorMessage != null) Color(0xFFFFCDD2) else textColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinationChannelStrip(
    recipientLabel: String,
    isDepartment: Boolean,
    onCreateTip: () -> Unit
) {
    Surface(color = BgCard) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(InfoSurface)
                .border(1.dp, BrandGreen.copy(alpha = 0.18f), RoundedCornerShape(13.dp))
                .padding(start = 11.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isDepartment) "Operational group channel" else "Direct coordination channel",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Share verified updates with $recipientLabel",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onCreateTip,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.NoteAdd,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Add tip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CoordinationTipFormDialog(
    senderName: String,
    recipientLabel: String,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (CoordinationTipPayload) -> Unit
) {
    val createdAt = remember { System.currentTimeMillis() }
    val tipId = remember(createdAt) { generateTipReference(createdAt) }
    val context = LocalContext.current
    val locationScope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var incidentType by remember { mutableStateOf("Police") }
    var priority by remember { mutableStateOf("Medium") }
    var location by remember { mutableStateOf("") }
    var capturedLatitude by remember { mutableStateOf<Double?>(null) }
    var capturedLongitude by remember { mutableStateOf<Double?>(null) }
    var contactNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reasonForPoliceBackup by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isResolvingCurrentLocation by remember { mutableStateOf(false) }
    var currentLocationMessage by remember { mutableStateOf<String?>(null) }
    var currentLocationMessageIsError by remember { mutableStateOf(false) }

    fun showLocationError(message: String) {
        isResolvingCurrentLocation = false
        currentLocationMessage = message
        currentLocationMessageIsError = true
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun applyDeviceLocation(
        deviceLocation: Location,
        usedLastKnownLocation: Boolean = false
    ) {
        locationScope.launch {
            val address = withContext(Dispatchers.IO) {
                reverseGeocodeTipLocation(
                    context = context.applicationContext,
                    latitude = deviceLocation.latitude,
                    longitude = deviceLocation.longitude
                )
            }

            capturedLatitude = deviceLocation.latitude
            capturedLongitude = deviceLocation.longitude
            location = buildCurrentTipLocationText(
                address = address,
                latitude = deviceLocation.latitude,
                longitude = deviceLocation.longitude
            )

            val accuracyLabel = if (deviceLocation.hasAccuracy()) {
                " • accuracy ±${deviceLocation.accuracy.toInt()} m"
            } else {
                ""
            }

            currentLocationMessage = when {
                usedLastKnownLocation && address.isNullOrBlank() ->
                    "Recent GPS fix added$accuracyLabel"

                usedLastKnownLocation ->
                    "Recent address and GPS fix added$accuracyLabel"

                address.isNullOrBlank() ->
                    "Current GPS coordinates added$accuracyLabel"

                else ->
                    "Current address and GPS coordinates added$accuracyLabel"
            }
            currentLocationMessageIsError = false
            isResolvingCurrentLocation = false
        }
    }

    fun useLastKnownTipLocation(failureMessage: String) {
        // Keep the permission check in the same function as the protected API
        // call. Android Lint cannot prove that a separate helper already checked
        // runtime permission.
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            showLocationError(
                "Location permission is required to use your current location."
            )
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLocation ->
                    val ageMillis = lastLocation?.time
                        ?.takeIf { it > 0L }
                        ?.let { System.currentTimeMillis() - it }
                        ?: Long.MAX_VALUE

                    if (lastLocation != null && ageMillis <= 5 * 60 * 1000L) {
                        applyDeviceLocation(
                            deviceLocation = lastLocation,
                            usedLastKnownLocation = true
                        )
                    } else {
                        showLocationError(failureMessage)
                    }
                }
                .addOnFailureListener {
                    showLocationError(failureMessage)
                }
        } catch (_: SecurityException) {
            showLocationError(
                "Location permission is required to use your current location."
            )
        }
    }

    fun fetchCurrentTipLocation() {
        // Explicit, local runtime-permission guard for getCurrentLocation().
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            showLocationError(
                "Location permission is required to use your current location."
            )
            return
        }

        if (!isTipLocationServiceEnabled(context)) {
            showLocationError(
                "Location is turned off. Enable GPS, then tap Use current location again."
            )
            runCatching {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }

        isResolvingCurrentLocation = true
        currentLocationMessage = "Getting a fresh GPS location…"
        currentLocationMessageIsError = false

        try {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                )
                .addOnSuccessListener { currentLocation ->
                    if (currentLocation != null) {
                        applyDeviceLocation(currentLocation)
                    } else {
                        useLastKnownTipLocation(
                            "Unable to get your location. Move to an open area and try again."
                        )
                    }
                }
                .addOnFailureListener {
                    useLastKnownTipLocation(
                        "Unable to get your location. Check GPS and try again."
                    )
                }
        } catch (_: SecurityException) {
            showLocationError(
                "Location permission is required to use your current location."
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasTipLocationPermission(context)

        if (granted) {
            fetchCurrentTipLocation()
        } else {
            showLocationError(
                "Location permission was denied. You can still enter the location manually."
            )
        }
    }

    fun requestCurrentTipLocation() {
        if (isResolvingCurrentLocation) return

        if (hasTipLocationPermission(context)) {
            fetchCurrentTipLocation()
        } else {
            currentLocationMessage = "Allow location access to fill this field automatically."
            currentLocationMessageIsError = false
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    val locationError = attemptedSubmit && location.isBlank()
    val descriptionError = attemptedSubmit && description.isBlank()
    val canSend = location.isNotBlank() && description.isNotBlank()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = BgInput,
        unfocusedContainerColor = BgInput,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = BrandGreen,
        unfocusedBorderColor = DividerColor,
        focusedLabelColor = BrandGreen,
        unfocusedLabelColor = TextSecondary,
        focusedLeadingIconColor = BrandGreen,
        unfocusedLeadingIconColor = TextSecondary,
        cursorColor = BrandGreen,
        errorBorderColor = DangerColor,
        errorLabelColor = DangerColor,
        errorSupportingTextColor = DangerColor
    )

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.ime
                        .union(WindowInsets.navigationBars)
                        .only(WindowInsetsSides.Bottom)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val availableHeight = maxHeight.coerceAtMost(760.dp)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .height(availableHeight),
                color = BgElevated,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, DividerColor),
                shadowElevation = 14.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarningSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AddAlert,
                                contentDescription = null,
                                tint = WarningColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Create Incident Tip",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "View-only card • $recipientLabel",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close form",
                                tint = TextSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 12.dp,
                            bottom = 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "tip_meta") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TipReadOnlyTile(
                                    icon = Icons.Default.Badge,
                                    label = "Tip ID",
                                    value = tipId,
                                    modifier = Modifier.weight(1f)
                                )
                                TipReadOnlyTile(
                                    icon = Icons.Default.AccessTime,
                                    label = "Created",
                                    value = SimpleDateFormat(
                                        "MM/dd/yy HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(createdAt)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item(key = "tip_classification") {
                            Surface(
                                color = BgMuted,
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, DividerColor)
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TipFormSectionTitle("Incident type")
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        contentPadding = PaddingValues(end = 2.dp)
                                    ) {
                                        items(listOf("Police", "Medical", "Fire", "General")) { option ->
                                            TipChoiceChip(
                                                label = option,
                                                selected = incidentType == option,
                                                tint = tipTypeColor(option),
                                                onClick = { incidentType = option }
                                            )
                                        }
                                    }

                                    TipFormSectionTitle("Priority")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        listOf("High", "Medium", "Low").forEach { option ->
                                            TipChoiceChip(
                                                label = option,
                                                selected = priority == option,
                                                tint = tipPriorityColor(option),
                                                modifier = Modifier.weight(1f),
                                                onClick = { priority = option }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "tip_location") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                OutlinedTextField(
                                    value = location,
                                    onValueChange = { value ->
                                        location = value
                                        capturedLatitude = null
                                        capturedLongitude = null
                                        if (!isResolvingCurrentLocation) {
                                            currentLocationMessage = null
                                            currentLocationMessageIsError = false
                                        }
                                    },
                                    label = { Text("Location *") },
                                    placeholder = {
                                        Text("Street, barangay, landmark, or full address")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, contentDescription = null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 1,
                                    maxLines = 3,
                                    isError = locationError,
                                    supportingText = if (locationError) {
                                        { Text("Location is required") }
                                    } else null,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(14.dp)
                                )

                                OutlinedButton(
                                    onClick = ::requestCurrentTipLocation,
                                    enabled = !isResolvingCurrentLocation && !isSending,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        BrandGreen.copy(
                                            alpha = if (isResolvingCurrentLocation) 0.24f else 0.55f
                                        )
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = BrandGreen,
                                        disabledContentColor = TextSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    if (isResolvingCurrentLocation) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = BrandGreen
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MyLocation,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(7.dp))

                                    Text(
                                        if (isResolvingCurrentLocation) {
                                            "Getting current location…"
                                        } else {
                                            "Use current location"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                currentLocationMessage?.let { message ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (currentLocationMessageIsError) {
                                                Icons.Default.LocationOff
                                            } else if (isResolvingCurrentLocation) {
                                                Icons.Default.GpsFixed
                                            } else {
                                                Icons.Default.CheckCircle
                                            },
                                            contentDescription = null,
                                            tint = if (currentLocationMessageIsError) {
                                                DangerColor
                                            } else {
                                                SuccessColor
                                            },
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = message,
                                            color = if (currentLocationMessageIsError) {
                                                DangerColor
                                            } else {
                                                TextSecondary
                                            },
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "tip_contact") {
                            OutlinedTextField(
                                value = contactNumber,
                                onValueChange = { contactNumber = it },
                                label = { Text("Contact number") },
                                placeholder = { Text("Optional caller or witness number") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = fieldColors,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        item(key = "tip_description") {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description *") },
                                placeholder = { Text("What was observed or reported?") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                isError = descriptionError,
                                supportingText = if (descriptionError) {
                                    { Text("Description is required") }
                                } else null,
                                colors = fieldColors,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        item(key = "tip_backup_reason") {
                            OutlinedTextField(
                                value = reasonForPoliceBackup,
                                onValueChange = { reasonForPoliceBackup = it },
                                label = { Text("Reason for Police Backup") },
                                placeholder = { Text("Why police assistance or coordination is needed") },
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 3,
                                colors = fieldColors,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        item(key = "tip_view_only_note") {
                            Surface(
                                color = InfoSurface,
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.20f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = BrandGreen,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Recipients can view the complete tip. Accept and Decline are intentionally not shown.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSending,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderStrong),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                attemptedSubmit = true
                                if (canSend) {
                                    onSend(
                                        CoordinationTipPayload(
                                            tipId = tipId,
                                            createdAt = createdAt,
                                            incidentType = incidentType,
                                            priority = priority,
                                            location = location.trim(),
                                            latitude = capturedLatitude,
                                            longitude = capturedLongitude,
                                            contactNumber = contactNumber.trim(),
                                            description = description.trim(),
                                            reasonForPoliceBackup = reasonForPoliceBackup.trim(),
                                            senderName = senderName.trim(),
                                            status = "PENDING"
                                        )
                                    )
                                }
                            },
                            enabled = !isSending,
                            modifier = Modifier
                                .weight(1.25f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGreen,
                                contentColor = Color.White
                            )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Saving…", fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Send Tip", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipFormSectionTitle(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
}

@Composable
private fun TipReadOnlyTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = BgMuted,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(label, color = TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TipChoiceChip(
    label: String,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) tint.copy(alpha = 0.16f) else BgElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) tint.copy(alpha = 0.72f) else DividerColor
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected) tint else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun CoordinationTipCard(
    payload: CoordinationTipPayload,
    senderLabel: String,
    modifier: Modifier = Modifier,
    onView: () -> Unit
) {
    val accent = tipTypeColor(payload.incidentType)
    val priorityAccent = tipPriorityColor(payload.priority)
    val cardColor = if (ThemeController.isDarkMode.value) Color(0xFF172329) else Color(0xFFF1FCFC)

    Surface(
        modifier = modifier
            .widthIn(min = 250.dp, max = 350.dp),
        color = cardColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "INCIDENT TIP",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        senderLabel.ifBlank { payload.senderName.ifBlank { "Responder" } },
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = WarningSurface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.30f))
                ) {
                    Text(
                        payload.status.uppercase(Locale.getDefault()),
                        color = WarningColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                payload.tipId,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TipMetaPill(payload.incidentType, accent)
                TipMetaPill(payload.priority, priorityAccent)
            }

            Surface(
                color = BgElevated.copy(alpha = if (ThemeController.isDarkMode.value) 0.82f else 0.90f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        payload.location,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                payload.description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedButton(
                onClick = onView,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("View details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Text(
                "RESPONDER VIEW • VIEW ONLY",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun TipMetaPill(label: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            label.replaceFirstChar { it.uppercase() },
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TipDetailsDialog(
    payload: CoordinationTipPayload,
    requesterUserId: Int,
    repository: OperationalRepository,
    onDismiss: () -> Unit
) {
    var displayedPayload by remember(payload.serverId, payload.createdAt) {
        mutableStateOf(payload)
    }
    var isRefreshingFromServer by remember(payload.serverId) { mutableStateOf(false) }
    var serverLoadError by remember(payload.serverId) { mutableStateOf<String?>(null) }

    LaunchedEffect(payload.serverId, requesterUserId) {
        if (payload.serverId <= 0L || requesterUserId <= 0) return@LaunchedEffect

        isRefreshingFromServer = true
        serverLoadError = null
        repository.getCoordinationTip(
            tipId = payload.serverId,
            requesterUserId = requesterUserId
        ).onSuccess { record ->
            displayedPayload = payload.copy(
                serverId = record.id,
                tipId = record.referenceNo,
                createdAt = record.createdAtMillis,
                incidentType = record.incidentType.replaceFirstChar { it.uppercase() },
                priority = record.priority.replaceFirstChar { it.uppercase() },
                location = record.location,
                latitude = record.latitude,
                longitude = record.longitude,
                contactNumber = record.contactNumber,
                description = record.description,
                reasonForPoliceBackup = record.policeBackupReason,
                senderName = record.senderName,
                status = record.status.uppercase(Locale.US)
            )
        }.onFailure { error ->
            serverLoadError = error.message ?: "Unable to refresh the server record"
        }
        isRefreshingFromServer = false
    }

    val accent = tipTypeColor(displayedPayload.incidentType)
    val hasAuthoritativeRecord = displayedPayload.serverId > 0L
    val verificationColor = when {
        serverLoadError != null -> DangerColor
        hasAuthoritativeRecord -> SuccessColor
        else -> WarningColor
    }
    val verificationSurface = when {
        serverLoadError != null -> DangerSurface
        hasAuthoritativeRecord -> SuccessSurface
        else -> WarningSurface
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = BgElevated,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DividerColor),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.10f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tip Details",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            displayedPayload.tipId,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        color = WarningSurface,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            displayedPayload.status.uppercase(Locale.getDefault()),
                            color = WarningColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = InfoSurface,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "This responder card is view-only. It does not include Accept or Decline actions.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Surface(
                        color = verificationSurface,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(
                            1.dp,
                            verificationColor.copy(alpha = 0.24f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRefreshingFromServer) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGreen
                                )
                            } else {
                                Icon(
                                    if (hasAuthoritativeRecord && serverLoadError == null) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = verificationColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when {
                                    isRefreshingFromServer -> "Refreshing authoritative server record…"
                                    serverLoadError != null -> "Cached chat copy shown • $serverLoadError"
                                    displayedPayload.serverId > 0L -> "Verified against server record #${displayedPayload.serverId}"
                                    else -> "Legacy chat copy; no server record ID"
                                },
                                color = verificationColor,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    TipDetailRow(
                        icon = Icons.Default.Badge,
                        label = "Tip ID",
                        value = displayedPayload.tipId
                    )
                    TipDetailRow(
                        icon = Icons.Default.AccessTime,
                        label = "Timestamp",
                        value = formatTipTimestamp(displayedPayload.createdAt)
                    )
                    TipDetailRow(
                        icon = Icons.Default.Category,
                        label = "Classification",
                        value = "${displayedPayload.incidentType} • ${displayedPayload.priority} priority"
                    )
                    TipDetailRow(
                        icon = Icons.Default.LocationOn,
                        label = "Location",
                        value = displayedPayload.location
                    )
                    TipDetailRow(
                        icon = Icons.Default.Phone,
                        label = "Contact Number",
                        value = displayedPayload.contactNumber.ifBlank { "Not provided" }
                    )
                    TipDetailBlock(
                        icon = Icons.Default.Description,
                        label = "Description",
                        value = displayedPayload.description
                    )
                    TipDetailBlock(
                        icon = Icons.Default.Security,
                        label = "Reason for Police Backup",
                        value = displayedPayload.reasonForPoliceBackup.ifBlank { "Not provided" }
                    )
                    TipDetailRow(
                        icon = Icons.Default.Person,
                        label = "Submitted by",
                        value = displayedPayload.senderName.ifBlank { "Responder" }
                    )
                }

                HorizontalDivider(color = DividerColor)

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TipDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Surface(
        color = BgMuted,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun TipDetailBlock(
    icon: ImageVector,
    label: String,
    value: String
) {
    TipDetailRow(icon = icon, label = label, value = value)
}

// ─────────────────────────────────────────────────────────────────────────────
//  QUICK REPLIES — fast, one-tap responses for responders on scene / driving
// ─────────────────────────────────────────────────────────────────────────────
private val QuickReplies = listOf(
    "Copy",
    "En route",
    "Arrived on scene",
    "Need backup",
    "Situation clear",
    "Stand by",
    "Received",
    "Negative"
)

@Composable
private fun QuickReplyBar(onSelect: (String) -> Unit) {
    Surface(
        color = BgCard,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(QuickReplies) { reply ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BrandGreen.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.18f)),
                    modifier = Modifier.clickable { onSelect(reply) }
                ) {
                    Text(
                        reply,
                        color = BrandGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onLike: () -> Unit,
    onQuickReplyToggle: () -> Unit,
    isRecording: Boolean = false,
    recordingElapsedMs: Long = 0L,
    onMicClick: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSendRecording: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BgCard,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ComposerIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Cancel recording",
                    onClick = onCancelRecording
                )

                val infiniteTransition = rememberInfiniteTransition(label = "rec_dot")
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_dot_alpha"
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935).copy(alpha = dotAlpha))
                    )
                    val totalSeconds = recordingElapsedMs / 1000
                    val label = String.format(Locale.US, "Recording… %d:%02d", totalSeconds / 60, totalSeconds % 60)
                    Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrandGreen)
                        .clickable { onSendRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send voice message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ComposerIconButton(
                icon = Icons.Default.AddCircleOutline,
                contentDescription = "Add attachment or incident tip",
                onClick = onAttachClick
            )
            ComposerIconButton(
                icon = Icons.Default.Bolt,
                contentDescription = "Quick replies",
                onClick = onQuickReplyToggle
            )

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("Message…", fontSize = 14.sp, color = TextSecondary)
                },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = DividerColor,
                    cursorColor = BrandGreen
                )
            )

            val hasText = text.trim().isNotEmpty()
            AnimatedContent(targetState = hasText, label = "send_btn") { active ->
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BrandGreen)
                            .clickable { onSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BgMuted)
                                .border(1.dp, DividerColor, CircleShape)
                                .clickable { onMicClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Record voice message",
                                tint = BrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BgMuted)
                                .border(1.dp, DividerColor, CircleShape)
                                .clickable { onLike() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Send like",
                                tint = BrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(BrandGreen.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = BrandGreen,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable private fun AvatarCircle(name: String, role: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(roleColor(role).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Text(text = roleInitials(name).ifEmpty { "?" }, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold, color = roleColor(role))
    }
}
@Composable private fun RoleBadge(role: String) {
    Surface(color = roleColor(role).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) { Text(role.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = roleColor(role), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) }
}
@Composable private fun UnreadBadgeCircle(count: Int) {
    Box(modifier = Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape).background(UnreadBadge), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
@Composable private fun UnreadPill(count: Int) {
    Box(modifier = Modifier.clip(CircleShape).background(UnreadBadge).padding(horizontal = 5.dp, vertical = 2.dp), contentAlignment = Alignment.Center) { Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun TypingSubtitle() {
    val inf = rememberInfiniteTransition(label = "typing"); val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(BrandGreen.copy(alpha = if (i == 1) alpha else 0.5f))) }
        Spacer(Modifier.width(4.dp)); Text("typing…", fontSize = 12.sp, color = BrandGreen)
    }
}
@Composable
private fun EmptySearch(
    modifier: Modifier = Modifier,
    title: String = "No conversations found",
    message: String = "Try searching another responder, department, or email."
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // A scrollable empty state lets Material pull-to-refresh receive the
            // downward gesture even when there are no conversation rows yet.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(BrandGreen.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(31.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                message,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
@Composable private fun NotificationToast(text: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), elevation = CardDefaults.cardElevation(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
            Text(text = text, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun NotificationOverlay(
    vm: CoordinationViewModel,
    onTap: (ResponderBrief?, DepartmentInfo?) -> Unit
) {
    val latest = vm.latestNotification.value

    LaunchedEffect(latest) {
        if (latest != null) {
            delay(5000L)
            vm.clearNotification()
        }
    }

    AnimatedVisibility(
        visible       = latest != null,
        enter         = slideInVertically(
            animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            initialOffsetY = { -it }
        ) + fadeIn(),
        exit          = slideOutVertically(
            animationSpec  = tween(250),
            targetOffsetY  = { -it }
        ) + fadeOut(),
        modifier      = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .zIndex(10f)
    ) {
        if (latest != null) {
            // Find which responder or dept this notification belongs to
            val tipPayload = parseCoordinationTip(latest)
            val matchedResponder = vm.responders.firstOrNull { responder ->
                latest.contains(responder.fullName, ignoreCase = true) ||
                        tipPayload?.senderName.equals(responder.fullName, ignoreCase = true)
            }
            val matchedDepartment = vm.departments.firstOrNull { department ->
                latest.contains(department.displayName, ignoreCase = true)
            }

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                elevation = CardDefaults.cardElevation(12.dp),
                onClick   = {
                    vm.clearNotification()
                    onTap(matchedResponder, matchedDepartment)
                }
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated green dot pulse
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        val pulse = rememberInfiniteTransition(label = "pulse")
                        val scale by pulse.animateFloat(
                            initialValue  = 0.8f,
                            targetValue   = 1.3f,
                            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                            label         = "scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp * scale)
                                .clip(CircleShape)
                                .background(BrandGreen.copy(alpha = 0.18f))
                        )
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint               = BrandGreen,
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "New message",
                            fontSize   = 11.sp,
                            color      = BrandGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            coordinationMessagePreview(latest),
                            color    = Color.White,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Tap-to-open hint
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Open",
                            color    = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick  = { vm.clearNotification() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint               = Color.White.copy(alpha = 0.5f),
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinationChatInfoDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxHeight = maxHeight.coerceAtMost(760.dp)
            val bodyMaxHeight = (dialogMaxHeight - 122.dp).coerceAtLeast(180.dp)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = dialogMaxHeight),
                color = BgElevated,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, DividerColor),
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Chat Info",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Conversation details and controls",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close chat info",
                                tint = TextSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        content()
                    }

                    HorizontalDivider(color = DividerColor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                "Close",
                                color = BrandGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInfoContent(
    r: ResponderBrief,
    messages: List<ChatMessage>,
    currentResponderId: String,
    operationalRepository: OperationalRepository,
    liveIsOnline: Boolean,
    liveLastSeen: Long,
    onSearchClick: () -> Unit,
    onFilesClick: () -> Unit
) {
    val online = liveIsOnline
    val messageCount = messages.size
    val fileCount = messages.count {
        it.type == MessageType.FILE || it.type == MessageType.IMAGE
    }
    val firstMessageTime = messages.minOfOrNull { it.createdAt } ?: 0L
    val chatAge = if (firstMessageTime > 0L) {
        val days = ((System.currentTimeMillis() - firstMessageTime) / 86_400_000L)
            .coerceAtLeast(0L)
        if (days == 0L) "Today" else "${days}d"
    } else {
        "—"
    }

    val context = LocalContext.current
    val reportScope = rememberCoroutineScope()
    var priority by remember(r.id) { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var isSubmittingReport by remember { mutableStateOf(false) }

    LaunchedEffect(r.id, currentResponderId) {
        FirebaseDatabase.getInstance().reference
            .child("priority_chats")
            .child(currentResponderId)
            .child(r.id)
            .get()
            .addOnSuccessListener { snapshot ->
                priority = snapshot.getValue(Boolean::class.java) ?: false
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val accent = roleColor(r.role)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = accent.copy(alpha = if (ThemeController.isDarkMode.value) 0.16f else 0.08f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(58.dp)) {
                    AvatarCircle(
                        name = r.fullName,
                        role = r.role,
                        size = 58.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(BgElevated)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (online) OnlineDot else DangerColor)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        r.fullName,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${r.role.replaceFirstChar { it.uppercase() }} Responder",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = if (online) SuccessSurface else DangerSurface,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (online) SuccessColor.copy(alpha = 0.25f)
                            else DangerColor.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (online) SuccessColor else DangerColor)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (online) "Active now" else "Offline",
                                color = if (online) SuccessColor else DangerColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ChatStatTile(
                value = messageCount.toString(),
                label = "Messages",
                modifier = Modifier.weight(1f)
            )
            ChatStatTile(
                value = fileCount.toString(),
                label = "Files",
                modifier = Modifier.weight(1f),
                onClick = onFilesClick
            )
            ChatStatTile(
                value = chatAge,
                label = "Chat age",
                modifier = Modifier.weight(1f)
            )
        }

        Column {
            SectionLabel("Responder info")
            InfoGroup {
                InfoRow(
                    icon = Icons.Default.Badge,
                    label = "Responder ID",
                    value = r.id.ifBlank { "—" }
                )
                InfoRowDivider()
                InfoRowDept(dept = r.role)
                if (!online) {
                    InfoRowDivider()
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Last seen",
                        value = formatLastSeenTime(liveLastSeen)
                    )
                }
            }
        }

        Column {
            SectionLabel("Quick actions")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy name",
                    tint = TextSecondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("Responder", r.fullName)
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "Name copied", Toast.LENGTH_SHORT).show()
                    }
                )

                ActionTile(
                    icon = if (priority) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (priority) "Priority" else "Prioritize",
                    tint = if (priority) WarningColor else TextSecondary,
                    bg = if (priority) WarningSurface else BgMuted,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val newValue = !priority
                        FirebaseDatabase.getInstance().reference
                            .child("priority_chats")
                            .child(currentResponderId)
                            .child(r.id)
                            .setValue(newValue)
                            .addOnSuccessListener {
                                priority = newValue
                                Toast.makeText(
                                    context,
                                    if (newValue) "Marked as priority" else "Removed from priority",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Failed to update priority",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                )

                ActionTile(
                    icon = Icons.Default.Search,
                    label = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.weight(1f),
                    onClick = onSearchClick
                )
            }
        }

        Surface(
            color = WarningSurface,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showReportDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Report,
                    contentDescription = null,
                    tint = WarningColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Report communication issue",
                        color = WarningColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Send a report to the admin or dispatcher",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = WarningColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingReport) {
                    showReportDialog = false
                    reportReason = ""
                }
            },
            title = {
                Text(
                    "Report communication issue",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "This will be submitted to the admin or dispatcher for review.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Describe the issue...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BrandGreen,
                            unfocusedBorderColor = DividerColor,
                            cursorColor = BrandGreen
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reportReason.trim().isNotEmpty() && !isSubmittingReport,
                    onClick = {
                        val reporterId = currentResponderId.toIntOrNull()
                        val reportedResponderId = r.id.toIntOrNull()
                        if (reporterId == null || reporterId <= 0 ||
                            reportedResponderId == null || reportedResponderId <= 0
                        ) {
                            Toast.makeText(
                                context,
                                "A valid responder account is required.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }

                        isSubmittingReport = true
                        reportScope.launch {
                            operationalRepository.submitCommunicationReport(
                                reporterId = reporterId,
                                reportedResponderId = reportedResponderId,
                                reason = reportReason.trim()
                            ).onSuccess { result ->
                                Toast.makeText(
                                    context,
                                    result.message.ifBlank { "Report submitted for review" },
                                    Toast.LENGTH_SHORT
                                ).show()
                                showReportDialog = false
                                reportReason = ""
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Failed to submit report: ${error.message ?: "server error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            isSubmittingReport = false
                        }
                    }
                ) {
                    if (isSubmittingReport) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = WarningColor
                        )
                    } else {
                        Text("Submit", color = WarningColor)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingReport,
                    onClick = {
                        showReportDialog = false
                        reportReason = ""
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ── HELPER COMPOSABLES ──────────────────────────────────────────────────────

@Composable private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium,
        color = TextSecondary, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable private fun InfoGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = BgMuted,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp), content = content)
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = BrandGreen,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }
}

@Composable private fun InfoRowDept(dept: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Shield, contentDescription = null,
            tint = roleColor(dept), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text("Department", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        RoleBadge(role = dept)
    }
}

@Composable private fun InfoRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 29.dp), color = DividerColor, thickness = 0.5.dp)
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    bg: Color = BgMuted,
    onClick: () -> Unit
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DividerColor),
        modifier = modifier
            .height(66.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
@Composable
private fun AttachSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onCreateTip: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BgElevated,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, DividerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Share with chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            "Choose a message type",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                AttachmentOptionRow(
                    icon = Icons.Default.AddAlert,
                    title = "Incident / Tip",
                    subtitle = "Send a structured view-only incident card",
                    tint = WarningColor,
                    background = WarningSurface,
                    onClick = onCreateTip
                )

                Spacer(Modifier.height(8.dp))

                AttachmentOptionRow(
                    icon = Icons.Default.Image,
                    title = "Photo / Image",
                    subtitle = "Send an image from the gallery",
                    tint = SuccessColor,
                    background = SuccessSurface,
                    onClick = onPickImage
                )

                Spacer(Modifier.height(8.dp))

                AttachmentOptionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    title = "Document / File",
                    subtitle = "PDF, Word, ZIP, and other files",
                    tint = Color(0xFF3498DB),
                    background = InfoSurface,
                    onClick = onPickFile
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "Incident cards are sent as view-only coordination updates.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = BgMuted,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UriImage(
    uriString: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream =
                    if (uriString.startsWith("http", ignoreCase = true)) {
                        java.net.URL(uriString).openStream()
                    } else {
                        ctx.contentResolver.openInputStream(Uri.parse(uriString))
                    }

                bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (_: Exception) {
                bitmap = null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(BgMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun DepartmentStatusCard(
    responders: List<FirebaseResponder>
) {
    val onlineCount = responders.count { it.isOnline }
    val fireCount = responders.count {
        it.isOnline && it.department.contains("fire", ignoreCase = true)
    }
    val medicalCount = responders.count {
        it.isOnline && (
                it.department.contains("medical", ignoreCase = true) ||
                        it.department.contains("ems", ignoreCase = true)
                )
    }
    val policeCount = responders.count {
        it.isOnline && (
                it.department.contains("police", ignoreCase = true) ||
                        it.department.contains("crime", ignoreCase = true)
                )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 7.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Responder availability",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$onlineCount online",
                color = SuccessColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactStatusChip(
                icon = Icons.Default.Groups,
                text = "$onlineCount All",
                tint = OnlineDot,
                modifier = Modifier.weight(1f)
            )
            CompactStatusChip(
                icon = Icons.Default.LocalFireDepartment,
                text = "$fireCount Fire",
                tint = Color(0xFFE45A3C),
                modifier = Modifier.weight(1f)
            )
            CompactStatusChip(
                icon = Icons.Default.LocalHospital,
                text = "$medicalCount EMS",
                tint = Color(0xFF2E8BCE),
                modifier = Modifier.weight(1f)
            )
            CompactStatusChip(
                icon = Icons.Default.Security,
                text = "$policeCount Police",
                tint = Color(0xFF6B67C7),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactStatusChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val dark = ThemeController.isDarkMode.value
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = if (dark) 0.18f else 0.09f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (dark) 0.30f else 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatChatTime(time: Long): String {
    if (time <= 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - time

    return when {
        diff < 60_000 -> "Now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(time))
    }
}

@Composable
private fun SharedFilesDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val files = messages.filter {
        it.type == MessageType.FILE || it.type == MessageType.IMAGE
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BrandGreen)
            }
        },
        title = {
            Text("Shared Files", fontWeight = FontWeight.Bold)
        },
        text = {
            if (files.isEmpty()) {
                Text(
                    "No shared files yet.",
                    color = TextSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    file.attachmentUri?.let { url ->
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(resolveOperationalFileUrl(url))
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Unable to open file",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.type == MessageType.IMAGE)
                                    Icons.Default.Image
                                else
                                    Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.attachmentName ?: file.text ?: "Attachment",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    SimpleDateFormat(
                                        "MMM dd, hh:mm a",
                                        Locale.getDefault()
                                    ).format(Date(file.createdAt)),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = DividerColor)
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun ChatStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = BgMuted,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
private fun formatLastSeenTime(time: Long): String {
    if (time <= 0L) return "Unknown"

    val now = System.currentTimeMillis()
    val diff = now - time

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat(
            "MMM dd, hh:mm a",
            Locale.getDefault()
        ).format(Date(time))
    }
}

@Composable
private fun DepartmentChatInfoContent(
    department: DepartmentInfo,
    messages: List<ChatMessage>,
    onFilesClick: () -> Unit
) {
    val messageCount = messages.size
    val fileCount = messages.count {
        it.type == MessageType.FILE || it.type == MessageType.IMAGE
    }
    val accent = roleColor(department.displayName)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = accent.copy(alpha = if (ThemeController.isDarkMode.value) 0.16f else 0.08f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(department.emoji, fontSize = 28.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        department.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Inter-agency operational channel",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Surface(
                        color = SuccessSurface,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.22f))
                    ) {
                        Text(
                            "Approved access",
                            color = SuccessColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatStatTile(
                value = messageCount.toString(),
                label = "Messages",
                modifier = Modifier.weight(1f)
            )
            ChatStatTile(
                value = fileCount.toString(),
                label = "Shared files",
                modifier = Modifier.weight(1f),
                onClick = onFilesClick
            )
        }

        Column {
            SectionLabel("Channel information")
            InfoGroup {
                InfoRow(
                    icon = Icons.Default.Groups,
                    label = "Channel",
                    value = "Group chat"
                )
                InfoRowDivider()
                InfoRow(
                    icon = Icons.Default.Security,
                    label = "Access",
                    value = "Approved members"
                )
            }
        }

        Surface(
            color = InfoSurface,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier.padding(11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Use this channel for verified inter-agency updates, shared files, and view-only incident tips.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}