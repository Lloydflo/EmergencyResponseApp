package com.ers.emergencyresponseapp

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.viewmodel.CoordinationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*import androidx.compose.foundation.ExperimentalFoundationApi

// ─── Screen-size tier ────────────────────────────────────────────────────────
private enum class ScreenSize { SMALL, MEDIUM, LARGE }

// ─── Role accent colors ──────────────────────────────────────────────────────
private fun roleColor(role: String): Color = when (role.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}

private fun roleInitials(name: String): String =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

// ─────────────────────────────────────────────────────────────────────────────
//  MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinationPortalScreen(
    currentResponderId: String,
    currentResponderRole: String
) {
    val vm: CoordinationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val selectedResponder  = vm.selectedResponder
    val selectedDepartment = vm.selectedDepartment
    val responders         = vm.responders
    val departments        = vm.departments
    val messages           = vm.messages

    val messageInput = remember { mutableStateOf("") }
    val isPeerTyping = vm.isPeerTyping

    val windowInfo    = LocalWindowInfo.current
    val density       = LocalDensity.current.density
    val screenWidthDp = windowInfo.containerSize.width / density
    val screenSize = when {
        screenWidthDp < 600f -> ScreenSize.SMALL
        screenWidthDp < 900f -> ScreenSize.MEDIUM
        else                  -> ScreenSize.LARGE
    }

    val scope             = rememberCoroutineScope()
    val listState         = rememberLazyListState()
    // FIX: mutableIntStateOf instead of mutableStateOf for Int
    val unseenCount       = remember { mutableIntStateOf(0) }
    val searchQuery       = remember { mutableStateOf("") }
    val timeFmt           = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showDetailsDialog = remember { mutableStateOf(false) }
    val showAttachSheet   = remember { mutableStateOf(false) }
    val drawerState       = rememberDrawerState(initialValue = DrawerValue.Closed)
    val ctx               = LocalContext.current

    // ── File picker ──────────────────────────────────────────────────────────
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "file"
            when {
                selectedResponder.value != null ->
                    vm.sendFileMessage(currentResponderId, selectedResponder.value!!, uri, fileName, isImage = false)
                selectedDepartment.value != null ->
                    vm.sendFileToDepartment(currentResponderId, selectedDepartment.value!!.name, uri, fileName, isImage = false)
                else -> Toast.makeText(ctx, "Select a chat before attaching", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Image picker ─────────────────────────────────────────────────────────
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            when {
                selectedResponder.value != null ->
                    vm.sendFileMessage(currentResponderId, selectedResponder.value!!, uri, "image", isImage = true)
                selectedDepartment.value != null ->
                    vm.sendFileToDepartment(currentResponderId, selectedDepartment.value!!.name, uri, "image", isImage = true)
                else -> Toast.makeText(ctx, "Select a chat before attaching", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun doSend() {
        val text = messageInput.value.trim()
        if (text.isEmpty()) return
        when {
            selectedResponder.value != null -> {
                vm.sendMockPrivateMessage(currentResponderId, selectedResponder.value!!, text)
                messageInput.value = ""
            }
            selectedDepartment.value != null -> {
                vm.sendMockDepartmentMessage(currentResponderId, selectedDepartment.value!!.name, text)
                messageInput.value = ""
            }
            else -> Toast.makeText(ctx, "Select a chat to send a message", Toast.LENGTH_SHORT).show()
        }
    }

    val onChatSelected: (ResponderBrief?, DepartmentInfo?) -> Unit = { res, dept ->
        if (res != null) vm.selectResponderAndLoadHistory(currentResponderId, res)
        else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
    }

    val composerSlot: @Composable () -> Unit = {
        ChatComposer(
            text          = messageInput.value,
            onTextChange  = { messageInput.value = it },
            onSend        = { doSend() },
            onAttachClick = { showAttachSheet.value = true }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screenSize) {

            // ── SMALL ─────────────────────────────────────────────────────────
            ScreenSize.SMALL -> {
                var showSidebar by remember { mutableStateOf(false) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = selectedResponder.value?.fullName
                                            ?: selectedDepartment.value?.displayName ?: "Select a chat",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    selectedResponder.value?.let { r ->
                                        val online = r.status.contains("online", true)
                                        Text(
                                            text = if (online) "● Online" else "○ Offline",
                                            fontSize = 11.sp,
                                            color = if (online) Color(0xFF4CAF50) else Color.Gray
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { showSidebar = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showDetailsDialog.value = true }) {
                                    Icon(Icons.Default.Info, contentDescription = "Info")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .navigationBarsPadding()
                            .background(Color(0xFFF8F9FA))
                    ) {
                        if (isPeerTyping) TypingIndicator(name = selectedResponder.value?.fullName ?: "Someone")
                        if (selectedResponder.value == null && selectedDepartment.value == null) {
                            EmptyStatePanel(modifier = Modifier.weight(1f))
                        } else {
                            ChatMessagesPanel(
                                messages  = messages,
                                modifier  = Modifier.weight(1f).padding(horizontal = 8.dp),
                                timeFmt   = timeFmt,
                                listState = listState,
                                onReact   = { id, emoji -> vm.addReaction(id, emoji, currentResponderId) }
                            )
                        }
                        HorizontalDivider()
                        composerSlot()
                    }
                }

                if (showSidebar) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { showSidebar = false })
                    Surface(
                        modifier        = Modifier.fillMaxHeight().width(270.dp),
                        shape           = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        color           = Color(0xFFF7F4FA),
                        shadowElevation = 12.dp
                    ) {
                        ChatListSidebar(
                            searchQuery          = searchQuery.value,
                            onSearchChange       = { searchQuery.value = it },
                            responders           = responders,
                            departments          = departments,
                            currentResponderRole = currentResponderRole,
                            selectedResponder    = selectedResponder.value,
                            selectedDepartment   = selectedDepartment.value,
                            onChatSelected       = { res, dept -> onChatSelected(res, dept); showSidebar = false }
                        )
                    }
                }

                if (showDetailsDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showDetailsDialog.value = false },
                        confirmButton    = { TextButton(onClick = { showDetailsDialog.value = false }) { Text("Close") } },
                        title            = { Text("Responder Details") },
                        text             = { selectedResponder.value?.let { ChatDetailsPanel(it) } ?: Text("No responder selected") }
                    )
                }
            }

            // ── MEDIUM ────────────────────────────────────────────────────────
            ScreenSize.MEDIUM -> {
                ModalNavigationDrawer(
                    drawerState   = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(270.dp), drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)) {
                            ChatListSidebar(
                                searchQuery          = searchQuery.value,
                                onSearchChange       = { searchQuery.value = it },
                                responders           = responders,
                                departments          = departments,
                                currentResponderRole = currentResponderRole,
                                selectedResponder    = selectedResponder.value,
                                selectedDepartment   = selectedDepartment.value,
                                onChatSelected       = { res, dept -> onChatSelected(res, dept); scope.launch { drawerState.close() } }
                            )
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open sidebar")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text       = selectedResponder.value?.fullName ?: selectedDepartment.value?.displayName ?: "Select a chat",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                selectedResponder.value?.let { r ->
                                    val online = r.status.contains("online", true)
                                    Text(text = if (online) "● Online" else "○ Offline", fontSize = 11.sp, color = if (online) Color(0xFF4CAF50) else Color.Gray)
                                }
                            }
                            IconButton(onClick = { showDetailsDialog.value = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Details")
                            }
                        }
                        HorizontalDivider()
                        if (isPeerTyping) TypingIndicator(name = selectedResponder.value?.fullName ?: "Someone")
                        if (selectedResponder.value == null && selectedDepartment.value == null) {
                            EmptyStatePanel(modifier = Modifier.weight(1f))
                        } else {
                            ChatMessagesPanel(
                                messages  = messages,
                                modifier  = Modifier.weight(1f).padding(horizontal = 8.dp),
                                timeFmt   = timeFmt,
                                listState = listState,
                                onReact   = { id, emoji -> vm.addReaction(id, emoji, currentResponderId) }
                            )
                        }
                        HorizontalDivider()
                        composerSlot()
                    }
                }
                if (showDetailsDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showDetailsDialog.value = false },
                        confirmButton    = { TextButton(onClick = { showDetailsDialog.value = false }) { Text("Close") } },
                        title            = { Text("Responder Details") },
                        text             = { selectedResponder.value?.let { ChatDetailsPanel(it) } ?: Text("No responder selected") }
                    )
                }
            }

            // ── LARGE ─────────────────────────────────────────────────────────
            ScreenSize.LARGE -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.width(300.dp).fillMaxHeight(), color = Color(0xFFF7F4FA), shadowElevation = 4.dp) {
                        ChatListSidebar(
                            searchQuery          = searchQuery.value,
                            onSearchChange       = { searchQuery.value = it },
                            responders           = responders,
                            departments          = departments,
                            currentResponderRole = currentResponderRole,
                            selectedResponder    = selectedResponder.value,
                            selectedDepartment   = selectedDepartment.value,
                            onChatSelected       = onChatSelected
                        )
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedResponder.value?.let { r ->
                                InitialsAvatar(name = r.fullName, role = r.role, size = 38.dp, modifier = Modifier.padding(end = 10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text       = selectedResponder.value?.fullName ?: selectedDepartment.value?.displayName ?: "Select a chat",
                                    fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                selectedResponder.value?.let { r ->
                                    val online = r.status.contains("online", true)
                                    Text(text = if (online) "● Online" else "○ Offline", fontSize = 11.sp, color = if (online) Color(0xFF4CAF50) else Color.Gray)
                                }
                            }
                        }
                        HorizontalDivider()
                        if (isPeerTyping) TypingIndicator(name = selectedResponder.value?.fullName ?: "Someone")
                        if (selectedResponder.value == null && selectedDepartment.value == null) {
                            EmptyStatePanel(modifier = Modifier.weight(1f))
                        } else {
                            ChatMessagesPanel(
                                messages  = messages,
                                modifier  = Modifier.weight(1f).padding(horizontal = 16.dp),
                                timeFmt   = timeFmt,
                                listState = listState,
                                onReact   = { id, emoji -> vm.addReaction(id, emoji, currentResponderId) }
                            )
                        }
                        HorizontalDivider()
                        composerSlot()
                    }
                }
            }
        }

        // ── Notification toast ────────────────────────────────────────────────
        val latest = vm.latestNotification.value
        AnimatedVisibility(
            visible  = latest != null,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                Card(
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 100.dp).fillMaxWidth().clickable { vm.clearNotification() },
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(text = latest ?: "", color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        IconButton(onClick = { vm.clearNotification() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                        }
                    }
                }
            }
        }

        LaunchedEffect(latest) {
            if (latest != null) { delay(5000L); vm.clearNotification() }
        }

        // Remove before production
        LaunchedEffect(Unit) {
            delay(2000)
            vm.receiveIncomingPrivateMessage(meId = currentResponderId, peerId = "2", senderName = "Alice Johnson", role = "fire", body = "Need backup?")
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total       = messages.size
                if (lastVisible == -1 || lastVisible >= total - 2) {
                    listState.animateScrollToItem(total - 1)
                    unseenCount.intValue = 0
                } else {
                    unseenCount.intValue = total - (lastVisible + 1)
                }
            }
        }

        if (unseenCount.intValue > 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick  = {
                        scope.launch {
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                            unseenCount.intValue = 0
                        }
                    },
                    modifier = Modifier.padding(bottom = 96.dp),
                    shape    = RoundedCornerShape(24.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF323232))
                ) { Text("↓ New messages (${unseenCount.intValue})", color = Color.White) }
            }
        }
    }

    if (showAttachSheet.value) {
        AttachBottomSheet(
            onDismiss   = { showAttachSheet.value = false },
            onPickImage = { showAttachSheet.value = false; imageLauncher.launch("image/*") },
            onPickFile  = { showAttachSheet.value = false; fileLauncher.launch("*/*") }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ATTACH BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AttachBottomSheet(onDismiss: () -> Unit, onPickImage: () -> Unit, onPickFile: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Send attachment", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 20.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPickImage() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Text("Photo / Image", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Send from gallery or camera", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPickFile() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) {
                        // FIX: use AutoMirrored version
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = Color(0xFF3498DB), modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Text("Document / File", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("PDF, Word, zip, and more", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  INITIALS AVATAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InitialsAvatar(name: String, role: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val initials = roleInitials(name).ifEmpty { "?" }
    Box(modifier = modifier.size(size).clip(CircleShape).background(roleColor(role).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(text = initials, fontSize = (size.value * 0.35f).sp, fontWeight = FontWeight.SemiBold, color = roleColor(role))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyStatePanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Forum, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("No conversation selected", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9A9A9A))
            Spacer(Modifier.height(6.dp))
            Text("Choose a responder or department from the sidebar", fontSize = 13.sp, color = Color(0xFFBBBBBB), textAlign = TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TYPING INDICATOR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TypingIndicator(name: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val inf = rememberInfiniteTransition(label = "typing")
        val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
        repeat(3) { i ->
            Box(modifier = Modifier.padding(end = 3.dp).size(6.dp).clip(CircleShape).background(Color.Gray.copy(alpha = if (i == 1) alpha else 0.4f)))
        }
        Spacer(Modifier.width(6.dp))
        Text(text = "$name is typing…", fontSize = 12.sp, color = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SIDEBAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatListSidebar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    responders: List<ResponderBrief>,
    departments: List<DepartmentInfo>,
    currentResponderRole: String,
    selectedResponder: ResponderBrief?,
    selectedDepartment: DepartmentInfo?,
    collapsed: Boolean = false,
    onChatSelected: (ResponderBrief?, DepartmentInfo?) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    if (collapsed) {
        Column(modifier = Modifier.fillMaxHeight().padding(6.dp)) {
            Text("Sidebar", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F4FA)).padding(top = 10.dp)) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            placeholder = { Text("Search chats…", color = Color(0xFF9A9A9A)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF6D6D6D)) },
            modifier    = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape       = RoundedCornerShape(24.dp), singleLine = true,
            colors      = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = Color.White, unfocusedContainerColor = Color.White,
                focusedBorderColor      = Color(0xFF4C8A89), unfocusedBorderColor = Color(0xFFD8D2DB)
            )
        )
        TabRow(selectedTabIndex = tabIndex, containerColor = Color(0xFFF7F4FA), contentColor = Color(0xFF4C8A89), divider = { HorizontalDivider(color = Color(0xFFD8D2DB)) }) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Responders", fontWeight = if (tabIndex == 0) FontWeight.SemiBold else FontWeight.Normal) })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Departments", fontWeight = if (tabIndex == 1) FontWeight.SemiBold else FontWeight.Normal) })
        }
        Spacer(Modifier.height(6.dp))
        when (tabIndex) {
            0 -> {
                val filtered = responders.filter { it.fullName.contains(searchQuery, true) || it.role.contains(searchQuery, true) }
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered) { r ->
                        val isSelected = selectedResponder?.id == r.id
                        val online     = r.status.contains("online", true)
                        Card(
                            modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { onChatSelected(r, null) },
                            colors    = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White),
                            shape     = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    InitialsAvatar(name = r.fullName, role = r.role, size = 44.dp)
                                    Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(if (online) Color(0xFF4CAF50) else Color.Gray).align(Alignment.BottomEnd))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.fullName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.lastMessage.ifBlank { "No recent message" }, fontSize = 12.sp, color = Color(0xFF7A7A7A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (r.unreadCount > 0) {
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFE74C3C)), contentAlignment = Alignment.Center) {
                                        Text(r.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                val filtered = departments
                    .filter { it.displayName.contains(searchQuery, true) || it.name.contains(searchQuery, true) }
                    .filter { it.name == currentResponderRole || currentResponderRole == "admin" }
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered) { d ->
                        val isSelected = selectedDepartment?.name == d.name
                        Card(
                            modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { onChatSelected(null, d) },
                            colors    = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White),
                            shape     = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EEF4)), contentAlignment = Alignment.Center) {
                                    Text(text = d.emoji, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(d.lastMessage.ifBlank { "No recent update" }, fontSize = 12.sp, color = Color(0xFF7A7A7A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (d.unreadCount > 0) {
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFE74C3C)), contentAlignment = Alignment.Center) {
                                        Text(d.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MESSAGES PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatMessagesPanel(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    timeFmt: SimpleDateFormat,
    listState: LazyListState,
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No messages yet", color = Color.Gray, fontSize = 14.sp)
        }
        return
    }
    fun dayKey(ts: Long): String {
        val cal  = Calendar.getInstance().apply { timeInMillis = ts }
        val now  = Calendar.getInstance()
        val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)  && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)  -> "Today"
            cal.get(Calendar.YEAR) == yest.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yest.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
        }
    }
    LazyColumn(
        state               = listState,
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var lastDay: String? = null
        items(messages) { msg ->
            val key = dayKey(msg.createdAt)
            if (key != lastDay) {
                lastDay = key
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Surface(color = Color(0xFFEDEDED), shape = RoundedCornerShape(999.dp)) {
                        Text(text = key, fontSize = 11.sp, color = Color(0xFF666666), modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                    }
                }
            }
            ChatBubble(msg = msg, timeLabel = timeFmt.format(Date(msg.createdAt)), onReact = onReact)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  URI IMAGE — loads from content:// URI without Coil
//  Once you add Coil to gradle, replace this with:
//    AsyncImage(model = uriString, contentDescription = desc, contentScale = scale, modifier = modifier)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UriImage(
    uriString: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx     = LocalContext.current
    var bitmap  by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val stream = ctx.contentResolver.openInputStream(uri)
                bitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
            } catch (_: Exception) { }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap             = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale       = contentScale,
            modifier           = modifier
        )
    } else {
        // Placeholder while loading
        Box(modifier = modifier.background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CHAT BUBBLE — TEXT / IMAGE / FILE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(
    msg: ChatMessage,
    timeLabel: String,
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    val isOwn       = msg.senderName == "You"
    val bubbleColor = if (isOwn) Color(0xFF1D9E75) else Color.White
    val textColor   = if (isOwn) Color.White else Color(0xFF1B1B1B)
    val alignment   = if (isOwn) Alignment.End else Alignment.Start

    var showReactions by remember(msg.id) { mutableStateOf(false) }
    var showLightbox  by remember(msg.id) { mutableStateOf(false) }

    val bubbleShape = RoundedCornerShape(
        topStart    = 16.dp, topEnd      = 16.dp,
        bottomEnd   = if (isOwn) 4.dp else 16.dp,
        bottomStart = if (isOwn) 16.dp else 4.dp
    )

    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        if (!isOwn) {
            InitialsAvatar(name = msg.senderName, role = msg.role, size = 28.dp, modifier = Modifier.padding(end = 6.dp, bottom = 18.dp))
        }

        Column(horizontalAlignment = alignment) {

            // Long-press emoji picker
            if (showReactions) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 8.dp, modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("👍", "❤️", "😮", "🔥", "🚨").forEach { emoji ->
                            Text(text = emoji, fontSize = 22.sp, modifier = Modifier.clickable { onReact(msg.id, emoji); showReactions = false })
                        }
                    }
                }
            }

            when (msg.type) {

                // ── TEXT ──────────────────────────────────────────────────────
                MessageType.TEXT -> {
                    Surface(
                        color           = bubbleColor, shape = bubbleShape,
                        tonalElevation  = if (isOwn) 0.dp else 1.dp,
                        shadowElevation = if (isOwn) 0.dp else 2.dp,
                        modifier        = Modifier.widthIn(max = 260.dp)
                            .combinedClickable(onClick = { showReactions = false }, onLongClick = { showReactions = !showReactions })
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (!isOwn && msg.senderName.isNotBlank()) {
                                Text(text = msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role))
                                Spacer(Modifier.height(3.dp))
                            }
                            Text(text = msg.text ?: "", color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }

                // ── IMAGE ─────────────────────────────────────────────────────
                MessageType.IMAGE -> {
                    Column(
                        modifier = Modifier.widthIn(max = 260.dp).clip(bubbleShape)
                            .combinedClickable(
                                onClick     = { showLightbox = true },
                                onLongClick = { showReactions = !showReactions }
                            )
                    ) {
                        if (!isOwn && msg.senderName.isNotBlank()) {
                            Text(
                                text       = msg.senderName,
                                fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                color      = roleColor(msg.role),
                                modifier   = Modifier.background(bubbleColor).padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 2.dp)
                            )
                        }
                        msg.attachmentUri?.let { uriStr ->
                            UriImage(
                                uriString          = uriStr,
                                contentDescription = "Image",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.widthIn(min = 140.dp, max = 260.dp).heightIn(min = 100.dp, max = 200.dp)
                            )
                        }
                        if (!msg.text.isNullOrBlank()) {
                            Text(text = msg.text, color = textColor, fontSize = 13.sp, modifier = Modifier.background(bubbleColor).padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }

                // ── FILE ──────────────────────────────────────────────────────
                MessageType.FILE -> {
                    Surface(
                        color           = bubbleColor, shape = bubbleShape,
                        shadowElevation = if (isOwn) 0.dp else 2.dp,
                        modifier        = Modifier.widthIn(max = 260.dp)
                            .combinedClickable(onClick = { showReactions = false }, onLongClick = { showReactions = !showReactions })
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (isOwn) Color.White.copy(alpha = 0.2f) else Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                // FIX: AutoMirrored version
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = if (isOwn) Color.White else Color(0xFF3498DB), modifier = Modifier.size(22.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (!isOwn && msg.senderName.isNotBlank()) {
                                    Text(text = msg.senderName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = roleColor(msg.role))
                                }
                                Text(text = msg.attachmentName ?: "File", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(text = "Tap to open", color = textColor.copy(alpha = 0.65f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                else -> {
                    Surface(color = bubbleColor, shape = bubbleShape, modifier = Modifier.widthIn(max = 260.dp)) {
                        Text(text = msg.text ?: "", color = textColor, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // Reactions display
            if (msg.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    msg.reactions.groupingBy { it.emoji }.eachCount().forEach { (emoji, count) ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF1F1F1), modifier = Modifier.clickable { onReact(msg.id, emoji) }) {
                            Text(text = "$emoji $count", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 11.sp, color = Color(0xFF333333))
                        }
                    }
                }
            }

            // Time + read receipt
            Row(modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = timeLabel, fontSize = 10.sp, color = Color.Gray)
                if (isOwn) {
                    val (statusText, statusColor) = when (msg.status) {
                        MessageStatus.SENT      -> "✓"  to Color.Gray
                        MessageStatus.DELIVERED -> "✓✓" to Color.Gray
                        MessageStatus.READ      -> "✓✓" to Color(0xFF1D9E75)
                        else                    -> ""   to Color.Gray
                    }
                    if (statusText.isNotEmpty()) Text(text = statusText, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Fullscreen image lightbox (no Coil needed)
    if (showLightbox && msg.attachmentUri != null) {
        Dialog(onDismissRequest = { showLightbox = false }) {
            Box(
                modifier         = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black).clickable { showLightbox = false },
                contentAlignment = Alignment.Center
            ) {
                UriImage(
                    uriString          = msg.attachmentUri,
                    contentDescription = "Full image",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(28.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RESPONDER DETAILS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatDetailsPanel(selected: ResponderBrief?) {
    if (selected == null) { Text("No responder selected", color = Color.Gray); return }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        InitialsAvatar(name = selected.fullName, role = selected.role, size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(selected.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${selected.role.replaceFirstChar { it.uppercase() }} Responder", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        val online = selected.status.contains("online", true)
        Surface(color = if (online) Color(0xFFDFF7EB) else Color(0xFFF0F0F0), shape = RoundedCornerShape(20.dp)) {
            Text(text = if (online) "● Online" else "○ Offline", color = if (online) Color(0xFF0F6E56) else Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPOSER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 6.dp, color = Color.White) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Attach", tint = Color(0xFF1D9E75), modifier = Modifier.size(26.dp))
            }
            OutlinedTextField(
                value = text, onValueChange = onTextChange,
                placeholder   = { Text("Type a message…", fontSize = 14.sp) },
                modifier      = Modifier.weight(1f),
                singleLine    = true, maxLines = 1,
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1D9E75), unfocusedBorderColor = Color(0xFFDDDDDD))
            )
            Spacer(Modifier.width(8.dp))
            val enabled = text.trim().isNotEmpty()
            FilledIconButton(
                onClick  = onSend, enabled = enabled, modifier = Modifier.size(44.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor         = if (enabled) Color(0xFF1D9E75) else Color(0xFFCCCCCC),
                    disabledContainerColor = Color(0xFFCCCCCC),
                    contentColor           = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}