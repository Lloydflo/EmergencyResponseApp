package com.ers.emergencyresponseapp

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class ScreenSize { SMALL, MEDIUM, LARGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinationPortalScreen(
    currentResponderId: String,
    currentResponderRole: String // "fire" | "medical" | "police"
) {
    val vm: CoordinationViewModel = viewModel()

    // screen width
    val windowInfo = LocalWindowInfo.current
    val widthDp = windowInfo.containerSize.width
    val screenSize = when {
        widthDp < 600 -> ScreenSize.SMALL
        widthDp < 900 -> ScreenSize.MEDIUM
        else -> ScreenSize.LARGE
    }

    // Local UI state
    val scope = rememberCoroutineScope()
    val selectedResponder = vm.selectedResponder
    val selectedDepartment = vm.selectedDepartment
    val responders = vm.responders
    val departments = vm.departments
    val messages = vm.messages
    // list state for chat messages so we can auto-scroll
    val listState = rememberLazyListState()
    // track unseen messages when user is scrolled up
    val unseenCount = remember { mutableStateOf(0) }
    val messageInput = remember { mutableStateOf("") }
    val searchQuery = remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showDetailsDialog = remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val ctx = LocalContext.current

    // Proper send function (local function) so we can reuse without label returns
    fun doSend() {
        val text = messageInput.value.trim()
        if (text.isEmpty()) return
        // If a private responder is selected, send private; else if a department selected, send department.
        when {
            selectedResponder.value != null -> {
                vm.sendMockPrivateMessage(currentResponderId, selectedResponder.value!!, text)
                messageInput.value = ""
            }
            selectedDepartment.value != null -> {
                vm.sendMockDepartmentMessage(currentResponderId, selectedDepartment.value!!.name, text)
                messageInput.value = ""
            }
            else -> {
                Toast.makeText(ctx, "Please select a chat or department to send message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screenSize) {
            ScreenSize.SMALL -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        // Compact drawer width to match left sidebar
                        ModalDrawerSheet(modifier = Modifier.width(220.dp)) {
                            ChatListSidebar(
                                searchQuery = searchQuery.value,
                                onSearchChange = { searchQuery.value = it },
                                responders = responders,
                                departments = departments,
                                currentResponderRole = currentResponderRole,
                                selectedResponder = selectedResponder.value,
                                selectedDepartment = selectedDepartment.value,
                                onChatSelected = { res, dept ->
                                    if (res != null) vm.selectResponderAndLoadHistory(currentResponderId, res)
                                    else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(text = selectedResponder.value?.fullName ?: selectedDepartment.value?.displayName ?: "Select a chat") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                                },
                                actions = {
                                    IconButton(onClick = { /* call */ }) { Icon(Icons.Default.Call, contentDescription = "Call") }
                                    // Debug: simulate a private incoming message for the first responder
                                    IconButton(onClick = {
                                        val target = responders.firstOrNull()?.id
                                        if (target != null) {
                                            vm.receiveIncomingPrivateMessage(target, "Debug Sender", "fire", "Simulated private message")
                                        } else {
                                            Toast.makeText(ctx, "No responder to simulate", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Simulate private") }
                                    // Debug: simulate a department incoming message for the first department
                                    IconButton(onClick = {
                                        val dept = departments.firstOrNull()?.name
                                        if (dept != null) {
                                            vm.receiveIncomingDepartmentMessage(dept, "Debug Dept", "system", "Simulated department message")
                                        } else {
                                            Toast.makeText(ctx, "No department to simulate", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Icon(Icons.Default.AttachFile, contentDescription = "Simulate department") }
                                    IconButton(onClick = { showDetailsDialog.value = true }) { Icon(Icons.Default.Info, contentDescription = "Info") }
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Call, contentDescription = "Home") }, label = { Text("Home") })
                            }
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .navigationBarsPadding()
                                .background(Color(0xFFF8F9FA))
                        ) {
                            ChatMessagesPanel(messages = messages, modifier = Modifier.weight(1f).padding(12.dp), timeFmt = timeFmt, listState = listState)

                            HorizontalDivider()

                            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { /* attach */ }) { Icon(Icons.Default.AttachFile, contentDescription = "Attach") }
                                OutlinedTextField(value = messageInput.value, onValueChange = { messageInput.value = it }, placeholder = { Text("Type a message...") }, modifier = Modifier.weight(1f), singleLine = true, maxLines = 1, shape = RoundedCornerShape(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { doSend() }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF44336))) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White) }
                            }

                            // Chat details dialog for small screen
                            if (showDetailsDialog.value) {
                                AlertDialog(
                                    onDismissRequest = { showDetailsDialog.value = false },
                                    confirmButton = {
                                        TextButton(onClick = { showDetailsDialog.value = false }) { Text("Close") }
                                    },
                                    title = { Text("Responder Details") },
                                    text = {
                                        selectedResponder.value?.let { ChatDetailsPanel(it) } ?: Text("No responder selected")
                                    }
                                )
                            }
                        }
                    }
                }

            }

            ScreenSize.MEDIUM -> {
                ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                        ChatListSidebar(
                            searchQuery = searchQuery.value,
                            onSearchChange = { searchQuery.value = it },
                            responders = responders,
                            departments = departments,
                            currentResponderRole = currentResponderRole,
                            selectedResponder = selectedResponder.value,
                            selectedDepartment = selectedDepartment.value,
                            collapsed = false,
                            onChatSelected = { res, dept ->
                                if (res != null) vm.selectResponderAndLoadHistory(currentResponderId, res)
                                else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }) {
                    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Open sidebar") }
                                Text(selectedResponder.value?.fullName ?: selectedDepartment.value?.displayName ?: "Select a chat", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showDetailsDialog.value = true }) { Icon(Icons.Default.Info, contentDescription = "Details") }
                            }

                            HorizontalDivider()

                            ChatMessagesPanel(messages = messages, modifier = Modifier.weight(1f).padding(12.dp), timeFmt = timeFmt, listState = listState)

                            HorizontalDivider()

                            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, contentDescription = "Attach") }
                                OutlinedTextField(value = messageInput.value, onValueChange = { messageInput.value = it }, placeholder = { Text("Type a message...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { doSend() }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF44336))) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White) }
                            }

                            // Details as modal/dialog (not side panel) on medium
                            if (showDetailsDialog.value) {
                                AlertDialog(
                                    onDismissRequest = { showDetailsDialog.value = false },
                                    confirmButton = { TextButton(onClick = { showDetailsDialog.value = false }) { Text("Close") } },
                                    title = { Text("Responder Details") },
                                    text = { selectedResponder.value?.let { ChatDetailsPanel(it) } ?: Text("No responder selected") }
                                )
                            }
                        }
                    }
                }
            }

            ScreenSize.LARGE -> {
                ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                        ChatListSidebar(
                            searchQuery = searchQuery.value,
                            onSearchChange = { searchQuery.value = it },
                            responders = responders,
                            departments = departments,
                            currentResponderRole = currentResponderRole,
                            selectedResponder = selectedResponder.value,
                            selectedDepartment = selectedDepartment.value,
                            collapsed = false,
                            onChatSelected = { res, dept ->
                                if (res != null) vm.selectResponderAndLoadHistory(currentResponderId, res)
                                else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }) {
                    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Open sidebar") }
                                Text(selectedResponder.value?.fullName ?: selectedDepartment.value?.displayName ?: "Select a chat", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {}) { Icon(Icons.Default.Call, contentDescription = "Call") }
                                IconButton(onClick = {}) { Icon(Icons.Default.Info, contentDescription = "Info") }
                            }

                            HorizontalDivider()

                            ChatMessagesPanel(messages = messages, modifier = Modifier.weight(1f).padding(16.dp), timeFmt = timeFmt, listState = listState)

                            HorizontalDivider()

                            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, contentDescription = "Attach") }
                                OutlinedTextField(value = messageInput.value, onValueChange = { messageInput.value = it }, placeholder = { Text("Type a message...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { doSend() }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF44336))) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White) }
                            }
                        }

                        Column(modifier = Modifier.widthIn(min = 240.dp, max = 360.dp).fillMaxHeight().background(Color.White).shadow(4.dp).padding(16.dp)) {
                            ChatDetailsPanel(selectedResponder.value)
                        }
                    }
                }
            }
        }

        // Bottom-right transient notification for new messages
        val latest = vm.latestNotification.value
        AnimatedVisibility(visible = latest != null, enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Card(modifier = Modifier.padding(16.dp).wrapContentWidth().wrapContentHeight().clickable { vm.clearNotification() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF323232)), elevation = CardDefaults.cardElevation(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(text = latest ?: "", color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearNotification() }) { Icon(imageVector = Icons.Default.Done, contentDescription = "Dismiss", tint = Color.White) }
                    }
                }
            }
        }

        LaunchedEffect(latest) {
            if (latest != null) {
                delay(5000L)
                vm.clearNotification()
            }
        }

        // Auto-scroll to newest message whenever messages list size changes
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                // Determine the last visible item index; if no visible items yet, treat as near-end and scroll
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = messages.size
                val nearEnd = lastVisible == -1 || lastVisible >= total - 2
                if (nearEnd) {
                    // user is at (or near) bottom — auto-scroll
                    listState.animateScrollToItem(total - 1)
                    unseenCount.value = 0
                } else {
                    // user has scrolled up — don't auto-scroll; show a small indicator of new messages
                    unseenCount.value = total - (lastVisible + 1)
                }
            }
        }

        // If user is scrolled up and there are unseen messages, show a small button to jump to bottom
        if (unseenCount.value > 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                            unseenCount.value = 0
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 96.dp)
                        .wrapContentWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF323232))
                ) {
                    Text(text = "New messages (${unseenCount.value})", color = Color.White)
                }
            }
        }
    }
}

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
    if (collapsed) {
        // Compact icon-only sidebar
        Column(modifier = Modifier.fillMaxHeight().padding(4.dp), verticalArrangement = Arrangement.SpaceBetween) {
            // Responders (icons)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(responders) { r ->
                    Box(modifier = Modifier.padding(6.dp).size(48.dp).clip(CircleShape).background(Color.LightGray).clickable { onChatSelected(r, null) }, contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = r.fullName, tint = Color.Gray, modifier = Modifier.fillMaxSize().padding(8.dp))
                        if (r.unreadCount > 0) Badge(containerColor = Color(0xFFF44336)) { Text(r.unreadCount.toString(), color = Color.White, fontSize = 9.sp) }
                    }
                }
            }

            // Departments (icons)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                departments.filter { it.name == currentResponderRole || currentResponderRole == "admin" }.forEach { d ->
                    TextButton(onClick = { onChatSelected(null, d) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text(d.emoji, fontSize = 18.sp) }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact search
        OutlinedTextField(value = searchQuery, onValueChange = onSearchChange, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp))

        Text("Responder Chats", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.Gray)

        LazyColumn(modifier = Modifier.weight(1f)) {
            val filteredResponders = responders.filter { it.fullName.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true) }
            items(filteredResponders) { r ->
                val isSelected = selectedResponder?.id == r.id
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onChatSelected(r, null) }, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Avatar", tint = Color.Gray, modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.LightGray))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.fullName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (r.status.contains("online", true)) Color(0xFF4CAF50) else Color.Gray))
                            }
                            Text(r.role.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = Color.Gray)
                            if (r.lastMessage.isNotBlank()) Text(r.lastMessage, fontSize = 11.sp, color = Color.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.unreadCount > 0) Badge(containerColor = Color(0xFFF44336)) { Text(r.unreadCount.toString(), color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Department Channels", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.Gray)

        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
            val filteredDepartments = departments.filter { it.displayName.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true) }
            items(filteredDepartments) { d ->
                val isSelected = selectedDepartment?.name == d.name
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onChatSelected(null, d) }, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (d.lastMessage.isNotBlank()) Text(d.lastMessage, fontSize = 11.sp, color = Color.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (d.unreadCount > 0) Badge(containerColor = Color(0xFFF44336)) { Text(d.unreadCount.toString(), color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessagesPanel(messages: List<ChatMessage>, modifier: Modifier = Modifier, timeFmt: SimpleDateFormat, listState: LazyListState) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { msg -> ChatBubble(msg, timeFmt.format(Date(msg.createdAt))) }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, timeLabel: String) {
    val isOwn = msg.isOwn
    val bgColor = if (isOwn) Color(0xFF007AFF) else Color(0xFFE5E5EA)
    val textColor = if (isOwn) Color.White else Color.Black
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(modifier = Modifier.padding(4.dp), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isOwn && msg.senderName != "You") {
                    Text(msg.senderName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(msg.body, color = textColor)
            }
        }
        Text(timeLabel, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
private fun ChatDetailsPanel(selected: ResponderBrief?) {
    if (selected == null) { Text("No responder selected", color = Color.Gray); return }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Avatar", tint = Color.Gray, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(selected.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${selected.role.replaceFirstChar { it.uppercase() }} Responder", color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Status: ${selected.status}", color = if (selected.status.contains("online")) Color(0xFF4CAF50) else Color.Gray)
    }
}

// --- Mock backend helpers (synchronous, UI/test-only) ---
@Suppress("unused")
private fun loadMockPrivateHistory(messages: MutableList<ChatMessage>, meId: String, peerId: String) {
    messages.clear()
    messages.addAll(listOf(
        ChatMessage("h1", peerId, "Peer", "fire", "Hey, need backup?", System.currentTimeMillis() - 120_000, false),
        ChatMessage("h2", meId, "You", "fire", "On my way.", System.currentTimeMillis() - 90_000, true)
    ))
}

@Suppress("unused")
private fun loadMockDepartmentHistory(messages: MutableList<ChatMessage>, department: String) {
    messages.clear()
    messages.addAll(listOf(
        ChatMessage("d1", "2", "Alice", department, "Fire team, status update.", System.currentTimeMillis() - 60_000, false),
        ChatMessage("d2", "3", "Bob", department, "Medical on scene.", System.currentTimeMillis() - 30_000, false)
    ))
}

@Suppress("unused")
private fun sendMockPrivateMessage(messages: MutableList<ChatMessage>, meId: String, peer: ResponderBrief, body: String) {
    val now = System.currentTimeMillis()
    messages.add(ChatMessage(null, meId, "You", peer.role, body, now, true))
    // immediate echo for mock
    messages.add(ChatMessage(null, peer.id, peer.fullName, peer.role, "Received: $body", System.currentTimeMillis(), false))
}

@Suppress("unused")
private fun sendMockDepartmentMessage(messages: MutableList<ChatMessage>, meId: String, department: String, body: String) {
    val now = System.currentTimeMillis()
    messages.add(ChatMessage(null, meId, "You", department, body, now, true))
    messages.add(ChatMessage(null, "2", "Alice", department, "Acknowledged", System.currentTimeMillis(), false))
}
