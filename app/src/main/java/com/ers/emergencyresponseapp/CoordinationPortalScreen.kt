@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
package com.ers.emergencyresponseapp

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
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
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.viewmodel.CoordinationViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
import androidx.compose.foundation.lazy.itemsIndexed



// ─────────────────────────────────────────────────────────────────────────────
//  COLORS
// ─────────────────────────────────────────────────────────────────────────────
private val BrandGreen    = Color(0xFF00C07F)
private val BgPage        = Color(0xFFF2F4F7)
private val BgCard        = Color(0xFFFFFFFF)
private val BgChat        = Color(0xFFF0F2F5)
private val TextPrimary   = Color(0xFF0D0D0D)
private val TextSecondary = Color(0xFF65676B)
private val DividerColor  = Color(0xFFE4E6EA)
private val OnlineDot     = Color(0xFF31A24C)
private val UnreadBadge   = Color(0xFFE41E3F)
private val OwnBubble     = Color(0xFF00C07F)
private val PeerBubble    = Color(0xFFFFFFFF)

// ─────────────────────────────────────────────────────────────────────────────
//  FIXES APPLIED:
//  1. Removed dead import: com.ers.emergencyresponseapp.firebase.model.ResponderProfile
//  2. Removed orphaned ResponderCard composable (wrong model + dark theme + unconnected)
//  3. Added correct FirebaseResponder model matching your actual DB fields
//  4. Added FirebaseResponderRepository listening to "users/" node
//  5. Added FirebaseResponderViewModel driving the new tab
//  6. Added 3rd tab "All Responders" properly wired to Firebase
// ─────────────────────────────────────────────────────────────────────────────

data class FirebaseResponder(
    val uid        : String  = "",
    val userId     : String  = "",
    val fullName   : String  = "",
    val email      : String  = "",
    val department : String  = "",
    val isOnline   : Boolean = false,
    val lastSeen   : Long    = 0L
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
                                else -> false
                            },

                            lastSeen = when (lastSeenValue) {
                                is Long -> lastSeenValue
                                is Int -> lastSeenValue.toLong()
                                is Double -> lastSeenValue.toLong()
                                is String -> lastSeenValue.toLongOrNull() ?: 0L
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
    return ResponderBrief(
        id = uid.ifBlank { userId },
        fullName = fullName.ifBlank { email.ifBlank { "Unknown" } },
        username = email.ifBlank { fullName },
        role = department.lowercase(),
        status = if (isOnline) "online" else "offline",
        lastMessage = "Tap to coordinate",
        lastMessageTime = 0L,
        unreadCount = 0
    )
}

private fun roleInitials(name: String) =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

private enum class NavState { INBOX, CHAT }

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
    var navState by remember { mutableStateOf(NavState.INBOX) }
    // Keep inbox tab across CHAT <-> INBOX transitions.
    var inboxTabIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(navState) {
        AppScreenTracker.currentScreen = "COORDINATION"
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

    LaunchedEffect(currentResponderId) {
        val db = FirebaseDatabase.getInstance().reference
        val userRef = db.child("users").child(currentResponderId)

        userRef.updateChildren(
            mapOf(
                "isOnline" to true,
                "lastSeen" to System.currentTimeMillis()
            )
        )

        userRef.child("isOnline").onDisconnect().setValue(false)
        userRef.child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
    }

    DisposableEffect(currentResponderId) {
        onDispose {
            val db = FirebaseDatabase.getInstance().reference
            db.child("users").child(currentResponderId).updateChildren(
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to System.currentTimeMillis()
                )
            )

            vm.disconnectRealtime()
        }
    }

    BackHandler(enabled = navState == NavState.CHAT) {
        navState = NavState.INBOX
    }

    AnimatedContent(
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
                vm                 = vm,
                currentResponderId = currentResponderId,
                onBack             = {
                    inboxTabIndex = if (vm.selectedDepartment.value != null) 1 else 0
                    navState = NavState.INBOX
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  INBOX SCREEN  — now has 3 tabs: Chats | Departments | All Responders
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
    var tabIndex    by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 2)) }
    // If parent updates desired tab (e.g., returning from chat), reflect it.
    LaunchedEffect(initialTabIndex) {
        val desired = initialTabIndex.coerceIn(0, 2)
        if (tabIndex != desired) tabIndex = desired
    }
    LaunchedEffect(tabIndex) { onTabIndexChanged(tabIndex) }
    var searchQuery by remember { mutableStateOf("") }
    val responders  = vm.responders
    val departments = vm.departments
    val totalUnread = responders.sumOf { it.unreadCount } + departments.sumOf { it.unreadCount }
    val fbVm: FirebaseResponderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val firebaseResponders by fbVm.responders.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(BgCard)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (navController != null) {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TextPrimary
                                    )
                                }
                            }
                            Column {
                                Text(
                                    "Coordination",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = TextPrimary
                                )
                                if (totalUnread > 0) Text(
                                    "$totalUnread unread",
                                    fontSize = 12.sp,
                                    color = BrandGreen
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search responders…",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgPage,
                            unfocusedContainerColor = BgPage,
                            focusedBorderColor = BrandGreen,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    DepartmentStatusCard(
                        responders = firebaseResponders.filter { responder ->
                            val name = responder.fullName.trim()
                            val dept = responder.department.trim()

                            val isMe =
                                responder.uid == currentResponderId ||
                                        responder.userId == currentResponderId

                            val isValidResponder =
                                name.isNotBlank() &&
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
                        Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Chats",
                                    fontWeight = if (tabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                val rUnread = responders.sumOf { it.unreadCount }
                                if (rUnread > 0) UnreadPill(count = rUnread)
                            }
                        })
                        Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Departments",
                                    fontWeight = if (tabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                val dUnread = departments.sumOf { it.unreadCount }
                                if (dUnread > 0) UnreadPill(count = dUnread)
                            }
                        })
                        Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = {
                            Text(
                                "All Responders",
                                fontWeight = if (tabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        })
                    }
                }
            },
            containerColor = BgPage
        ) { padding ->
            when (tabIndex) {
                0 -> {
                    val filtered = responders
                        .filter { responder ->
                            val name = responder.fullName.trim()
                            val role = responder.role.trim()

                            val isValidResponder =
                                name.isNotBlank() &&
                                        !name.equals("Unknown", ignoreCase = true)

                            val matchesSearch =
                                name.contains(searchQuery, ignoreCase = true) ||
                                        role.contains(searchQuery, ignoreCase = true)

                            isValidResponder && matchesSearch
                        }
                        .sortedByDescending { it.lastMessageTime }

                    if (filtered.isEmpty()) EmptySearch(modifier = Modifier.padding(padding))
                    else LazyColumn(
                        modifier = Modifier.padding(padding),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { it.id.ifBlank { UUID.randomUUID().toString() } }
                        ) { r -> ResponderRow(responder = r, onClick = { onOpenChat(r, null) }) }
                    }
                }


                1 -> {
                    val filtered = departments
                        .filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                    it.name.contains(searchQuery, ignoreCase = true)
                        }
                    if (filtered.isEmpty()) EmptySearch(modifier = Modifier.padding(padding))
                    else LazyColumn(
                        modifier = Modifier.padding(padding),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { it.name.ifBlank { UUID.randomUUID().toString() } }
                        ) { d -> DepartmentRow(
                            dept = d,
                            currentResponderId = currentResponderId,
                            vm = vm,
                            onOpenChat = { onOpenChat(null, d) }
                        ) }
                    }
                }


                2 -> AllRespondersTab(
                    vm = fbVm,
                    searchQuery = searchQuery,
                    currentResponderId = currentResponderId,
                    modifier = Modifier.padding(padding),
                    onResponderClick = { responder ->
                        onOpenChat(
                            responder.toResponderBrief(),
                            null
                        )
                    }
                )
            }
        }
        // Floating notification overlay on top of everything
        NotificationOverlay(
            vm    = vm,
            onTap = { responder, dept -> onOpenChat(responder, dept) }
        )

        FloatingActionButton(
            onClick = {
                navController?.navigate("responder_list/$currentResponderId")
            },
            containerColor = BrandGreen,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = 40.dp
                )
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "New message"
            )
        }
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
        filtered.isEmpty() -> EmptySearch(modifier = modifier)
        else -> {
            val onlineCount = filtered.count { it.isOnline }
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
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
                    key = { it.uid.ifBlank { UUID.randomUUID().toString() } }
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
                    Text(text = lastSeenText, fontSize = 11.sp, color = Color(0xFFB0BEC5))
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (responder.isOnline) Color(0xFFDFF7EB) else Color(0xFFF0F0F0)
            ) {
                Text(
                    text     = if (responder.isOnline) "● Online" else "○ Offline",
                    color    = if (responder.isOnline) Color(0xFF0F6E56) else TextSecondary,
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
                        text       = if (responder.lastMessage.isNotBlank()) responder.lastMessage else "Tap to chat",
                        fontSize   = 13.sp,
                        color      = if (responder.unreadCount > 0) TextPrimary else TextSecondary,
                        fontWeight = if (responder.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        maxLines   = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (responder.unreadCount > 0) UnreadBadgeCircle(count = responder.unreadCount)
            else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFBFC2C8), modifier = Modifier.size(20.dp))
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
    val isMember = dept.lastMessage == "Tap to open group chat"
    val isPending = dept.lastMessage == "Request pending approval"

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
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFBFC2C8))
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
private fun ChatScreen(vm: CoordinationViewModel, currentResponderId: String, onBack: () -> Unit) {
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
    var isRecordingVoice by remember { mutableStateOf(false) }
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
                    liveIsOnline = when (val value = snapshot.child("isOnline").value) {
                        is Boolean -> value
                        is String -> value.toBoolean()
                        else -> false
                    }

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

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                isRecordingVoice = true
                Toast.makeText(ctx, "Recording started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    fun doSend() {
        val text = messageInput.value.trim()
        if (text.isEmpty()) return
        when {
            selectedResponder  != null -> vm.sendMockPrivateMessage(currentResponderId, selectedResponder, text)
            selectedDepartment != null -> vm.sendMockDepartmentMessage(currentResponderId, selectedDepartment.name, text)
            else -> Toast.makeText(ctx, "Select a chat to send", Toast.LENGTH_SHORT).show()
        }
        messageInput.value = ""
    }

    LaunchedEffect(messages.lastOrNull()?.id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect

        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val isNearBottom = lastVisibleIndex >= messages.lastIndex - 1

        when {
            !didInitialAutoScroll -> {
                // First open: jump to latest message once.
                listState.scrollToItem(messages.lastIndex)
                unseenCount.intValue = 0
                didInitialAutoScroll = true
            }
            isNearBottom -> {
                // While user is already near bottom, keep following new messages.
                listState.animateScrollToItem(messages.lastIndex)
                unseenCount.intValue = 0
            }
            else -> {
                // User is reading older messages; don't force scroll.
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
        messages.filter {
            it.text?.contains(chatSearchQuery, ignoreCase = true) == true ||
                    it.attachmentName?.contains(chatSearchQuery, ignoreCase = true) == true
        }
    }

    Scaffold(
        topBar = {
            Surface(color = BgCard, shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically)
                {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                    Box(modifier = Modifier.size(40.dp)) {
                        if (selectedResponder != null) {
                            AvatarCircle(name = selectedResponder.fullName, role = selectedResponder.role, size = 40.dp)
                            if (isOnline) Box(modifier = Modifier.size(12.dp).align(Alignment.BottomEnd).clip(CircleShape).background(BgCard).padding(2.dp).clip(CircleShape).background(OnlineDot))
                        } else if (selectedDepartment != null) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(roleColor(selectedDepartment.name).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(selectedDepartment.emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chatName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            isPeerTyping               -> TypingSubtitle()
                            selectedResponder != null  -> Text(if (isOnline) "Active now" else "Offline", fontSize = 12.sp, color = if (isOnline) BrandGreen else TextSecondary)
                            selectedDepartment != null -> Text("Group channel", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    IconButton(onClick = { showInfoDialog.value = true }) { Icon(Icons.Default.Info, contentDescription = "Info", tint = TextSecondary) }
                }
                if (showChatSearch) {
                    OutlinedTextField(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        placeholder = {
                            Text("Search messages...")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    chatSearchQuery = ""
                                    showChatSearch = false
                                }
                            ) {
                                Icon(Icons.Default.Close, null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = BrandGreen,
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                }
            }
        },
        containerColor = BgChat
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ChatMessagesPanel(messages = visibleMessages, timeFmt = timeFmt, listState = listState, currentResponderId = currentResponderId, onReact = { id, emoji -> vm.addReaction(id, emoji, currentResponderId) }, modifier = Modifier.fillMaxSize().padding(bottom = 72.dp))
            if (unseenCount.intValue > 0) {
                Button(onClick = { scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1); unseenCount.intValue = 0 } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), shape = RoundedCornerShape(20.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF323232))) {
                    Text("↓ ${unseenCount.intValue} new", color = Color.White, fontSize = 13.sp)
                }
            }
            ChatComposer(
                modifier      = Modifier.align(Alignment.BottomCenter),
                text          = messageInput.value,
                onTextChange  = { messageInput.value = it },
                onSend        = { doSend() },
                onAttachClick = { showAttach.value = true },
                onLike        = {                              // <-- add this
                    when {
                        selectedResponder  != null -> vm.sendMockPrivateMessage(currentResponderId, selectedResponder, "👍")
                        selectedDepartment != null -> vm.sendMockDepartmentMessage(currentResponderId, selectedDepartment.name, "👍")
                        else -> Toast.makeText(ctx, "Select a chat first", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    val latest = vm.latestNotification.value
    AnimatedVisibility(visible = latest != null, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            NotificationToast(text = latest ?: "", onDismiss = { vm.clearNotification() })
        }
    }
    LaunchedEffect(latest) { if (latest != null) { delay(5000L); vm.clearNotification() } }

    if (showInfoDialog.value) {
        AlertDialog(
            onDismissRequest = { showInfoDialog.value = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog.value = false }) {
                    Text("Close", color = BrandGreen)
                }
            },
            title = { Text("Chat Info", fontWeight = FontWeight.Medium) },
            text  = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    when {
                        selectedResponder  != null -> ChatInfoContent(
                            r = selectedResponder,
                            messages = messages,
                            currentResponderId = currentResponderId,
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
                        else -> Text("No info available")
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
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
            }
        )
    }
}

@Composable
private fun ChatMessagesPanel(messages: List<ChatMessage>, timeFmt: SimpleDateFormat, listState: LazyListState, currentResponderId: String, onReact: (String, String) -> Unit, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Say hello 👋", fontSize = 15.sp, color = TextSecondary)
            }
        }
        return
    }
    val dayLabelFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dayKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    fun dayLabel(ts: Long): String = dayLabelFormat.format(Date(ts))
    fun dayKey(ts: Long): String = dayKeyFormat.format(Date(ts))

    val orderedMessages = remember(messages) {
        messages.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
    }

    var revealedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revealedMessageId) {
        val selected = revealedMessageId ?: return@LaunchedEffect
        delay(2500)
        if (revealedMessageId == selected) revealedMessageId = null
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {

        itemsIndexed(
            items = orderedMessages,
            key = { index, item ->
                "${item.id}_${item.createdAt}_$index"
            }
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
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFFDDE1E7), shape = RoundedCornerShape(99.dp)) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF606770), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    timeLabel: String,
    currentResponderId: String,
    onReact: (String, String) -> Unit,
    isTimeVisible: Boolean,
    onToggleTime: () -> Unit
) {
    val isOwn = msg.isOwn || msg.senderId == currentResponderId; val bubbleColor = if (isOwn) OwnBubble else PeerBubble; val textColor = if (isOwn) Color.White else TextPrimary
    val alignment = if (isOwn) Alignment.End else Alignment.Start; val horizontalArr = if (isOwn) Arrangement.End else Arrangement.Start
    var showEmojiPicker by remember(msg.id) { mutableStateOf(false) }; var showLightbox by remember(msg.id) { mutableStateOf(false) }
    val bubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp)
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = horizontalArr, verticalAlignment = Alignment.Bottom) {
        if (!isOwn) AvatarCircle(name = msg.senderName, role = msg.role, size = 28.dp, modifier = Modifier.padding(end = 6.dp, bottom = 18.dp))
        Column(horizontalAlignment = alignment, modifier = Modifier.widthIn(max = 280.dp)) {
            AnimatedVisibility(visible = showEmojiPicker) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 10.dp, modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("👍","❤️","😮","🔥","🚨").forEach { emoji -> Text(emoji, fontSize = 22.sp, modifier = Modifier.clickable { onReact(msg.id, emoji); showEmojiPicker = false }) }
                    }
                }
            }
            when (msg.type) {
                MessageType.TEXT -> Surface(color = bubbleColor, shape = bubbleShape, shadowElevation = if (isOwn) 0.dp else 1.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onToggleTime()
                            msg.attachmentUri?.let { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        onLongClick = {
                            onToggleTime()
                            showEmojiPicker = !showEmojiPicker
                        }
                    )) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (!isOwn && msg.senderName.isNotBlank()) { Text(msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role)); Spacer(Modifier.height(2.dp)) }
                        Text(msg.text ?: "", color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                MessageType.IMAGE -> Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    shadowElevation = if (isOwn) 0.dp else 1.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onToggleTime()
                            msg.attachmentUri?.let { url ->
                                try {
                                    val finalUrl = if (url.startsWith("http")) {
                                        url
                                    } else {
                                        "https://emergency-response.alertaraqc.com/$url"
                                    }

                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to open image", Toast.LENGTH_SHORT).show()
                                }
                            }
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

                        msg.attachmentUri?.let { url ->
                            val finalUrl = if (url.startsWith("http")) {
                                url
                            } else {
                                "https://emergency-response.alertaraqc.com/$url"
                            }

                            UriImage(
                                uriString = finalUrl,
                                contentDescription = "Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .widthIn(min = 180.dp, max = 260.dp)
                                    .heightIn(min = 140.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }

                        if (!msg.text.isNullOrBlank() && msg.text != "Image") {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                msg.text,
                                color = textColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                MessageType.FILE -> Surface(color = bubbleColor, shape = bubbleShape, shadowElevation = if (isOwn) 0.dp else 1.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onToggleTime()
                            Toast.makeText(context, "Opening file...", Toast.LENGTH_SHORT).show()

                            msg.attachmentUri?.let { url ->
                                try {
                                    val finalUrl = if (url.startsWith("http")) {
                                        url
                                    } else {
                                        "https://emergency-response.alertaraqc.com/$url"
                                    }

                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    context.startActivity(intent)
                                } catch (e: Exception) {
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
                    )) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (isOwn) Color.White.copy(alpha = 0.2f) else Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = if (isOwn) Color.White else Color(0xFF3498DB), modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (!isOwn && msg.senderName.isNotBlank()) Text(msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role))
                            Text(msg.attachmentName ?: "File", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Tap to open", color = textColor.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                    }
                }
                else -> Surface(color = bubbleColor, shape = bubbleShape) { Text(msg.text ?: "", color = textColor, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
            }
            if (msg.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    msg.reactions.groupingBy { it.emoji }.eachCount().forEach { (emoji, count) ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF0F2F5), modifier = Modifier.clickable { onReact(msg.id, emoji) }) {
                            Text("$emoji $count", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 11.sp, color = TextPrimary)
                        }
                    }
                }
            }
            if (isTimeVisible || isOwn) {
                Row(modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isTimeVisible) {
                        Text(timeLabel, fontSize = 10.sp, color = TextSecondary)
                    }
                    if (isOwn) {
                        val (tick, color) = when (msg.status) { MessageStatus.SENT -> "✓" to TextSecondary; MessageStatus.DELIVERED -> "✓✓" to TextSecondary; MessageStatus.READ -> "✓✓" to BrandGreen; else -> "" to TextSecondary }
                        if (tick.isNotEmpty()) Text(tick, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showLightbox && msg.attachmentUri != null) {
        Dialog(onDismissRequest = { showLightbox = false }) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black).clickable { showLightbox = false }, contentAlignment = Alignment.Center) {
                UriImage(uriString = msg.attachmentUri, contentDescription = "Full image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(28.dp))
            }
        }
    }
}

@Composable
private fun ChatComposer(modifier: Modifier = Modifier, text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onAttachClick: () -> Unit, onLike: () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = BgCard, shadowElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttachClick) { Icon(Icons.Default.AddCircleOutline, contentDescription = "Attach", tint = BrandGreen, modifier = Modifier.size(26.dp)) }
            OutlinedTextField(value = text, onValueChange = onTextChange, placeholder = { Text("Aa", fontSize = 15.sp, color = TextSecondary) }, modifier = Modifier.weight(1f), singleLine = false, maxLines = 4, shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = BgPage, unfocusedContainerColor = BgPage, focusedBorderColor = BrandGreen, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.width(8.dp))
            val hasText = text.trim().isNotEmpty()
            AnimatedContent(targetState = hasText, label = "send_btn") { active ->
                if (active) Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(BrandGreen).clickable { onSend() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp)) }
                else Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE4E6EA))
                        .clickable { onLike() },   // <-- call onLike
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ThumbUp, contentDescription = "Like", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
            }
        }
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
private fun EmptySearch(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandGreen.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "No conversations found",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Try searching another responder, department, or email.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
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
            val matchedResponder  = vm.responders.firstOrNull  { latest.contains(it.fullName, ignoreCase = true) }
            val matchedDepartment = vm.departments.firstOrNull { latest.contains(it.displayName, ignoreCase = true) }

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
                            latest,
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

@Composable private fun ChatInfoContent(
    r: ResponderBrief,
    messages: List<ChatMessage>,
    currentResponderId: String,
    liveIsOnline: Boolean,
    liveLastSeen: Long,
    onSearchClick: () -> Unit,
    onFilesClick: () -> Unit
) {
    val online = liveIsOnline
    val messageCount = messages.count { it.type == MessageType.TEXT }
    val fileCount = messages.count {
        it.type == MessageType.FILE || it.type == MessageType.IMAGE
    }

    val firstMessageTime = messages.minOfOrNull { it.createdAt } ?: 0L

    val chatAge = if (firstMessageTime > 0L) {
        val days = ((System.currentTimeMillis() - firstMessageTime) / 86_400_000).coerceAtLeast(0)
        if (days == 0L) "Today" else "${days}d"
    } else {
        "—"
    }
    val ctx     = LocalContext.current
    var priority by remember { mutableStateOf(false) }
    var priorityLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(r.id, currentResponderId) {
        FirebaseDatabase.getInstance().reference
            .child("priority_chats")
            .child(currentResponderId)
            .child(r.id)
            .get()
            .addOnSuccessListener { snapshot ->
                priority = snapshot.getValue(Boolean::class.java) ?: false
                priorityLoaded = true
            }
            .addOnFailureListener {
                priorityLoaded = true
            }
    }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    val realLastSeen = liveLastSeen

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── HEADER BANNER + FLOATING AVATAR ────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1D9E75), Color(0xFF0F6E56))),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.BottomCenter)
                    .clip(CircleShape)
                    .background(roleColor(r.role))
                    .border(3.dp, BgCard, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    roleInitials(r.fullName).ifEmpty { "?" },
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color      = Color.White
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── NAME + ROLE + STATUS ────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            Text(r.fullName, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("${r.role.replaceFirstChar { it.uppercase() }} Responder",
                fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = if (online) Color(0xFFDFF7EB) else Color(0xFFFFEBEB),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                        .background(if (online) Color(0xFF1D9E75) else Color(0xFFE24B4A)))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (online) "Active now" else "Offline",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color    = if (online) Color(0xFF0F6E56) else Color(0xFFA32D2D)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── STATS ROW ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        Spacer(Modifier.height(16.dp))

        // ── RESPONDER INFO SECTION ──────────────────────────────────────
        SectionLabel("Responder info")
        InfoGroup {
            InfoRow(icon = Icons.Default.Badge, label = "Responder ID", value = r.id.ifBlank { "—" })
            InfoRowDivider()
            InfoRowDept(dept = r.role)
            if (!online) {
                InfoRowDivider()
                InfoRow(
                    icon = Icons.Default.AccessTime,
                    label = "Last seen",
                    value = formatLastSeenTime(realLastSeen)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── QUICK ACTIONS SECTION ───────────────────────────────────────
        SectionLabel("Quick actions")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Copy
            ActionTile(
                icon  = Icons.Default.ContentCopy,
                label = "Copy name",
                tint  = TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    val clip = android.content.ClipData.newPlainText("Responder", r.fullName)
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
            // Priority
            ActionTile(
                icon  = if (priority) Icons.Default.Star else Icons.Default.StarBorder,
                label = if (priority) "Priority ★" else "Priority",
                tint  = if (priority) Color(0xFFBA7517) else TextSecondary,
                bg    = if (priority) Color(0xFFFAEEDA) else Color(0xFFF7F8FA),
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
                                ctx,
                                if (newValue) "Marked as priority" else "Removed from priority",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                ctx,
                                "Failed to update priority",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            )
            // Search
            ActionTile(
                icon  = Icons.Default.Search,
                label = "Search",
                tint  = TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = onSearchClick
            )
        }
        Spacer(Modifier.height(16.dp))

        Surface(
            color = Color(0xFFFFF3E0),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showReportDialog = true
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.Report,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    "Report communication issue",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100)
                )
            }
        }

        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = {
                    showReportDialog = false
                    reportReason = ""
                },
                title = { Text("Report communication issue") },
                text = {
                    Column {
                        Text(
                            "This will be submitted to admin/dispatcher for review.",
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
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = reportReason.trim().isNotEmpty(),
                        onClick = {
                            val db = FirebaseDatabase.getInstance().reference
                            val reportId = db.child("communication_reports").push().key ?: return@TextButton

                            val report = mapOf(
                                "reportId" to reportId,
                                "reporterId" to currentResponderId,
                                "reportedResponderId" to r.id,
                                "reportedResponderName" to r.fullName,
                                "reportedResponderRole" to r.role,
                                "reason" to reportReason.trim(),
                                "status" to "pending",
                                "createdAt" to System.currentTimeMillis()
                            )

                            db.child("communication_reports")
                                .child(reportId)
                                .setValue(report)
                                .addOnSuccessListener {
                                    Toast.makeText(ctx, "Report submitted", Toast.LENGTH_SHORT).show()
                                    showReportDialog = false
                                    reportReason = ""
                                }
                                .addOnFailureListener {
                                    Toast.makeText(ctx, "Failed to submit report", Toast.LENGTH_SHORT).show()
                                }
                        }
                    ) {
                        Text("Submit", color = Color(0xFFE65100))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showReportDialog = false
                        reportReason = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
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
    Surface(color = Color(0xFFF7F8FA), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp), content = content)
    }
}

@Composable private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
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

@Composable private fun ActionTile(
    icon: ImageVector, label: String, tint: Color,
    modifier: Modifier = Modifier,
    bg: Color = Color(0xFFF7F8FA),
    onClick: () -> Unit
) {
    Surface(color = bg, shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
@Composable private fun AttachSheet(onDismiss: () -> Unit, onPickImage: () -> Unit, onPickFile: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Send attachment", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 20.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPickImage() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(26.dp)) }
                    Column { Text("Photo / Image", fontWeight = FontWeight.Medium, fontSize = 15.sp); Text("Send from gallery", fontSize = 12.sp, color = TextSecondary) }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPickFile() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = Color(0xFF3498DB), modifier = Modifier.size(26.dp)) }
                    Column { Text("Document / File", fontWeight = FontWeight.Medium, fontSize = 15.sp); Text("PDF, Word, zip, and more", fontSize = 12.sp, color = TextSecondary) }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel", color = TextSecondary) }
            }
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
            modifier = modifier.background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = Color(0xFFBBBBBB),
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
    val fireCount = responders.count { it.isOnline && it.department.contains("fire", true) }
    val medicalCount = responders.count {
        it.isOnline && (it.department.contains("medical", true) || it.department.contains("ems", true))
    }
    val policeCount = responders.count {
        it.isOnline && (it.department.contains("police", true) || it.department.contains("crime", true))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactStatusChip(Icons.Default.Groups, "$onlineCount Online", OnlineDot, Modifier.weight(1f))
        CompactStatusChip(Icons.Default.LocalFireDepartment, "$fireCount Fire", Color(0xFFE53935), Modifier.weight(1f))
        CompactStatusChip(Icons.Default.LocalHospital, "$medicalCount Medical", Color(0xFF1E88E5), Modifier.weight(1f))
        CompactStatusChip(Icons.Default.Security, "$policeCount Police", Color(0xFF6D4C41), Modifier.weight(1f))
    }
}

@Composable
private fun CompactStatusChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
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
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
            .height(74.dp)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        color = Color(0xFFF7F8FA),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            Spacer(Modifier.height(2.dp))

            Text(
                label,
                fontSize = 11.sp,
                color = TextSecondary
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(roleColor(department.displayName).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(department.emoji, fontSize = 34.sp)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            department.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Text(
            "Inter-agency group channel",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))

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
                label = "Files",
                modifier = Modifier.weight(1f),
                onClick = onFilesClick
            )
        }

        Spacer(Modifier.height(16.dp))

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
}