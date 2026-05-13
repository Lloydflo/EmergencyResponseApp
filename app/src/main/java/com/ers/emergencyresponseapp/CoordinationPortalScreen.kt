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
import androidx.navigation.NavHostController
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.viewmodel.CoordinationViewModel
import com.ers.emergencyresponseapp.firebase.model.ResponderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


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

private fun roleColor(role: String): Color = when (role.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}

private fun roleInitials(name: String) =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

private enum class NavState { INBOX, CHAT }

// ─────────────────────────────────────────────────────────────────────────────
//  ROOT SCREEN
//  FIX 1: Added navController parameter (nullable — default callers still work)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CoordinationPortalScreen(
    currentResponderId  : String,
    currentResponderName: String,
    currentResponderRole: String,
    navController       : NavHostController? = null   // ← ADDED
) {
    val vm: CoordinationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var navState by remember { mutableStateOf(NavState.INBOX) }

    val onOpenChat: (ResponderBrief?, DepartmentInfo?) -> Unit = { res, dept ->
        if (res != null)       vm.selectResponderAndLoadHistory(currentResponderId, res)
        else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
        navState = NavState.CHAT
    }

    LaunchedEffect(currentResponderId) {
        vm.connectRealtime(currentResponderId, currentResponderName, currentResponderRole)
    }
    DisposableEffect(Unit) { onDispose { vm.disconnectRealtime() } }

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
                navController        = navController
            )
            NavState.CHAT -> ChatScreen(
                vm                 = vm,
                currentResponderId = currentResponderId,
                onBack             = { navState = NavState.INBOX }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  INBOX SCREEN
//  FIX 2 & 3: Added currentResponderId + navController params
//             Edit icon now navigates to responder_list
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InboxScreen(
    vm                  : CoordinationViewModel,
    currentResponderRole: String,
    currentResponderId  : String,
    navController       : NavHostController?,
    onOpenChat          : (ResponderBrief?, DepartmentInfo?) -> Unit
) {
    var tabIndex    by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val responders  = vm.responders
    val departments = vm.departments
    val totalUnread = responders.sumOf { it.unreadCount } + departments.sumOf { it.unreadCount }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(BgCard)) {
                Row(
                    modifier          = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Coordination", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
                        if (totalUnread > 0) {
                            Text("$totalUnread unread", fontSize = 12.sp, color = BrandGreen)
                        }
                    }
                    // FIX 3: Correctly navigates to Firebase responder list
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandGreen.copy(alpha = 0.12f))
                            .clickable {
                                navController?.navigate("responder_list/$currentResponderId")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "New message", tint = BrandGreen, modifier = Modifier.size(20.dp))
                    }
                }

                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = { Text("Search responders…", fontSize = 14.sp, color = TextSecondary) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    trailingIcon  = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp)) } }
                    } else null,
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    shape         = RoundedCornerShape(24.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = BgPage,
                        unfocusedContainerColor = BgPage,
                        focusedBorderColor      = BrandGreen,
                        unfocusedBorderColor    = Color.Transparent
                    )
                )

                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor   = BgCard,
                    contentColor     = BrandGreen,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                            height   = 3.dp,
                            color    = BrandGreen
                        )
                    },
                    divider = { HorizontalDivider(color = DividerColor) }
                ) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Responders", fontWeight = if (tabIndex == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                            val rUnread = responders.sumOf { it.unreadCount }
                            if (rUnread > 0) UnreadPill(count = rUnread)
                        }
                    })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Departments", fontWeight = if (tabIndex == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                            val dUnread = departments.sumOf { it.unreadCount }
                            if (dUnread > 0) UnreadPill(count = dUnread)
                        }
                    })
                }
            }
        },
        containerColor = BgPage
    ) { padding ->
        when (tabIndex) {
            0 -> {
                val filtered = responders.filter {
                    it.fullName.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isEmpty()) EmptySearch(modifier = Modifier.padding(padding))
                else LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(filtered, key = { it.id }) { r -> ResponderRow(responder = r, onClick = { onOpenChat(r, null) }) }
                }
            }
            1 -> {
                val filtered = departments
                    .filter { it.displayName.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true) }
                    .filter { it.name == currentResponderRole || currentResponderRole == "admin" }
                if (filtered.isEmpty()) EmptySearch(modifier = Modifier.padding(padding))
                else LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(filtered, key = { it.name }) { d -> DepartmentRow(dept = d, onClick = { onOpenChat(null, d) }) }
                }
            }
        }
    }
}

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
                Text(responder.fullName, fontWeight = if (responder.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun DepartmentRow(dept: DepartmentInfo, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = BgCard) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(roleColor(dept.name).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(dept.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dept.displayName, fontWeight = if (dept.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(dept.lastMessage.ifBlank { "No recent update" }, fontSize = 13.sp,
                    color      = if (dept.unreadCount > 0) TextPrimary else TextSecondary,
                    fontWeight = if (dept.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines   = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (dept.unreadCount > 0) UnreadBadgeCircle(count = dept.unreadCount)
            else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFBFC2C8), modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = DividerColor, thickness = 0.5.dp)
}

@Composable
fun ResponderCard(responder: ResponderProfile) {
    val isOnline = responder.isOnline

    // Format lastSeen timestamp
    val lastSeenText = remember(responder.lastSeen) {
        if (responder.lastSeen > 0L) {
            val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
            "Last seen: " + sdf.format(java.util.Date(responder.lastSeen))
        } else "Last seen: Unknown"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2D42)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initials avatar (no photo URL in DB yet)
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4FC3F7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = responder.fullName
                            .split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString(""),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Online/offline dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = responder.fullName.ifBlank { "Unknown" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = responder.department.replaceFirstChar { it.uppercase() },
                    color = Color(0xFF4FC3F7),
                    fontSize = 13.sp
                )
                Text(
                    text = responder.email,
                    color = Color(0xFF78909C),
                    fontSize = 11.sp
                )
                if (!isOnline) {
                    Text(
                        text = lastSeenText,
                        color = Color(0xFF546E7A),
                        fontSize = 10.sp
                    )
                }
            }

            // Status badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isOnline) Color(0xFF1B5E20) else Color(0xFF424242)
            ) {
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    color = if (isOnline) Color(0xFF81C784) else Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CHAT SCREEN  — FIX 4: removed unused chatRole variable
// ─────────────────────────────────────────────────────────────────────────────
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
    val timeFmt            = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showAttach         = remember { mutableStateOf(false) }
    val showInfoDialog     = remember { mutableStateOf(false) }
    val ctx                = LocalContext.current
    val chatName           = selectedResponder?.fullName ?: selectedDepartment?.displayName ?: "Chat"
    // chatRole removed — was unused (FIX 4)
    val isOnline           = selectedResponder?.status?.contains("online", ignoreCase = true) == true

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = messages.size
            if (lastVisible == -1 || lastVisible >= total - 2) { listState.animateScrollToItem(total - 1); unseenCount.intValue = 0 }
            else unseenCount.intValue = total - (lastVisible + 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(color = BgCard, shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            }
        },
        containerColor = BgChat
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ChatMessagesPanel(messages = messages, timeFmt = timeFmt, listState = listState, currentResponderId = currentResponderId, onReact = { id, emoji -> vm.addReaction(id, emoji, currentResponderId) }, modifier = Modifier.fillMaxSize().padding(bottom = 72.dp))
            if (unseenCount.intValue > 0) {
                Button(onClick = { scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1); unseenCount.intValue = 0 } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), shape = RoundedCornerShape(20.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF323232))) {
                    Text("↓ ${unseenCount.intValue} new", color = Color.White, fontSize = 13.sp)
                }
            }
            ChatComposer(modifier = Modifier.align(Alignment.BottomCenter), text = messageInput.value, onTextChange = { messageInput.value = it }, onSend = { doSend() }, onAttachClick = { showAttach.value = true })
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
        AlertDialog(onDismissRequest = { showInfoDialog.value = false },
            confirmButton = { TextButton(onClick = { showInfoDialog.value = false }) { Text("Close", color = BrandGreen) } },
            title = { Text("Chat Info") },
            text  = { when { selectedResponder != null -> ChatInfoContent(r = selectedResponder); selectedDepartment != null -> Text("Department: ${selectedDepartment.displayName}"); else -> Text("No info available") } },
            shape = RoundedCornerShape(20.dp))
    }
    if (showAttach.value) {
        AttachSheet(onDismiss = { showAttach.value = false }, onPickImage = { showAttach.value = false; imageLauncher.launch("image/*") }, onPickFile = { showAttach.value = false; fileLauncher.launch("*/*") })
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
    fun dayLabel(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }; val now = Calendar.getInstance(); val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when { sameDay(cal, now) -> "Today"; sameDay(cal, yest) -> "Yesterday"; else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts)) }
    }
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var lastDay: String? = null
        items(messages) { msg ->
            val day = dayLabel(msg.createdAt)
            if (day != lastDay) { lastDay = day; DayDivider(label = day) }
            ChatBubble(msg = msg, timeLabel = timeFmt.format(Date(msg.createdAt)), currentResponderId = currentResponderId, onReact = onReact)
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
private fun ChatBubble(msg: ChatMessage, timeLabel: String, currentResponderId: String, onReact: (String, String) -> Unit) {
    val isOwn = msg.senderId == currentResponderId; val bubbleColor = if (isOwn) OwnBubble else PeerBubble; val textColor = if (isOwn) Color.White else TextPrimary
    val alignment = if (isOwn) Alignment.End else Alignment.Start; val horizontalArr = if (isOwn) Arrangement.End else Arrangement.Start
    var showEmojiPicker by remember(msg.id) { mutableStateOf(false) }; var showLightbox by remember(msg.id) { mutableStateOf(false) }
    val bubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp)
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
                    modifier = Modifier.combinedClickable(onClick = { showEmojiPicker = false }, onLongClick = { showEmojiPicker = !showEmojiPicker })) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (!isOwn && msg.senderName.isNotBlank()) { Text(msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role)); Spacer(Modifier.height(2.dp)) }
                        Text(msg.text ?: "", color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                MessageType.IMAGE -> Column(modifier = Modifier.clip(bubbleShape).combinedClickable(onClick = { showLightbox = true }, onLongClick = { showEmojiPicker = !showEmojiPicker })) {
                    if (!isOwn && msg.senderName.isNotBlank()) Text(msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role), modifier = Modifier.background(bubbleColor).padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 2.dp))
                    msg.attachmentUri?.let { UriImage(uriString = it, contentDescription = "Image", contentScale = ContentScale.Crop, modifier = Modifier.widthIn(min = 140.dp, max = 260.dp).heightIn(min = 100.dp, max = 200.dp)) }
                    if (!msg.text.isNullOrBlank()) Text(msg.text, color = textColor, fontSize = 13.sp, modifier = Modifier.background(bubbleColor).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                MessageType.FILE -> Surface(color = bubbleColor, shape = bubbleShape, shadowElevation = if (isOwn) 0.dp else 1.dp,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showEmojiPicker = !showEmojiPicker })) {
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
            Row(modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(timeLabel, fontSize = 10.sp, color = TextSecondary)
                if (isOwn) {
                    val (tick, color) = when (msg.status) { MessageStatus.SENT -> "✓" to TextSecondary; MessageStatus.DELIVERED -> "✓✓" to TextSecondary; MessageStatus.READ -> "✓✓" to BrandGreen; else -> "" to TextSecondary }
                    if (tick.isNotEmpty()) Text(tick, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
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
private fun ChatComposer(modifier: Modifier = Modifier, text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onAttachClick: () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = BgCard, shadowElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttachClick) { Icon(Icons.Default.AddCircleOutline, contentDescription = "Attach", tint = BrandGreen, modifier = Modifier.size(26.dp)) }
            OutlinedTextField(value = text, onValueChange = onTextChange, placeholder = { Text("Aa", fontSize = 15.sp, color = TextSecondary) }, modifier = Modifier.weight(1f), singleLine = false, maxLines = 4, shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = BgPage, unfocusedContainerColor = BgPage, focusedBorderColor = BrandGreen, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.width(8.dp))
            val hasText = text.trim().isNotEmpty()
            AnimatedContent(targetState = hasText, label = "send_btn") { active ->
                if (active) Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(BrandGreen).clickable { onSend() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp)) }
                else Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE4E6EA)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ThumbUp, contentDescription = "Like", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
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
@Composable private fun EmptySearch(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text("No results found", fontSize = 15.sp, color = TextSecondary) }
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
@Composable private fun ChatInfoContent(r: ResponderBrief) {
    val online = r.status.contains("online", ignoreCase = true)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AvatarCircle(name = r.fullName, role = r.role, size = 72.dp); Spacer(Modifier.height(10.dp))
        Text(r.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${r.role.replaceFirstChar { it.uppercase() }} Responder", color = TextSecondary, fontSize = 13.sp); Spacer(Modifier.height(8.dp))
        Surface(color = if (online) Color(0xFFDFF7EB) else Color(0xFFF0F0F0), shape = RoundedCornerShape(20.dp)) {
            Text(if (online) "● Active now" else "○ Offline", color = if (online) Color(0xFF0F6E56) else TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
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
@Composable private fun UriImage(uriString: String, contentDescription: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current; var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uriString) { withContext(Dispatchers.IO) { try { val stream = ctx.contentResolver.openInputStream(Uri.parse(uriString)); bitmap = BitmapFactory.decodeStream(stream); stream?.close() } catch (_: Exception) {} } }
    if (bitmap != null) Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
    else Box(modifier = modifier.background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(32.dp)) }
}