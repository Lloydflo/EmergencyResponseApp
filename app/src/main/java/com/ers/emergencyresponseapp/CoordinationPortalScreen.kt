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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Delete
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.ers.emergencyresponseapp.coordination.model.viewmodel.CoordinationViewModel
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import androidx.compose.foundation.combinedClickable
import com.ers.emergencyresponseapp.coordination.model.MessageStatus



private enum class ScreenSize { SMALL, MEDIUM, LARGE }



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinationPortalScreen(
    currentResponderId: String,
    currentResponderRole: String // "fire" | "medical" | "police"
) {
    var muted by remember { mutableStateOf(false) }
    val vm: CoordinationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val selectedResponder = vm.selectedResponder
    val selectedDepartment = vm.selectedDepartment
    val responders = vm.responders
    val departments = vm.departments
    val messages = vm.messages

    val messageInput = remember { mutableStateOf("") }   // ✅ una ito
    val isTyping = messageInput.value.isNotBlank()       // ✅ saka ito

    // screen width
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp

    val screenSize = when {
        widthDp < 600 -> ScreenSize.SMALL
        widthDp < 900 -> ScreenSize.MEDIUM
        else -> ScreenSize.LARGE
    }

    // Local UI state
    val scope = rememberCoroutineScope()
    // list state for chat messages so we can auto-scroll
    val listState = rememberLazyListState()
    // track unseen messages when user is scrolled up
    val unseenCount = remember { mutableStateOf(0) }
    val searchQuery = remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showDetailsDialog = remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val ctx = LocalContext.current
    val attachLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // ✅ sample behavior: send a message that attachment was added
            val label = "📎 Attachment: $uri"
            when {
                selectedResponder.value != null -> {
                    vm.sendMockPrivateMessage(currentResponderId, selectedResponder.value!!, label)
                }
                selectedDepartment.value != null -> {
                    vm.sendMockDepartmentMessage(currentResponderId, selectedDepartment.value!!.name, label)
                }
                else -> {
                    Toast.makeText(ctx, "Select chat first before attaching", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                var showSidebar by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = selectedResponder.value?.fullName
                                            ?: selectedDepartment.value?.displayName
                                            ?: "Select a chat"
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { showSidebar = true }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { /* call */ }) {
                                        Icon(Icons.Default.Call, contentDescription = "Call")
                                    }

                                    IconButton(onClick = {
                                        vm.clearCurrentChatHistory()
                                        Toast.makeText(ctx, "Chat cleared", Toast.LENGTH_SHORT)
                                            .show()
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Clear chat"
                                        )
                                    }

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
                            ChatMessagesPanel(
                                messages = messages,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(12.dp),
                                timeFmt = timeFmt,
                                listState = listState,
                                onReact = { messageId, emoji ->
                                    vm.addReaction(messageId, emoji, currentResponderId)
                                }
                            )

                            HorizontalDivider()

                            ChatComposer(
                                text = messageInput.value,
                                onTextChange = { messageInput.value = it },
                                onSend = {
                                    vm.sendTextMessage(
                                        senderId = currentResponderId,
                                        senderName = "You",
                                        text = messageInput.value
                                    )
                                    messageInput.value = ""
                                },
                                onAttach = { attachLauncher.launch("image/*") }
                            )

                            if (showDetailsDialog.value) {
                                AlertDialog(
                                    onDismissRequest = { showDetailsDialog.value = false },
                                    confirmButton = {
                                        TextButton(onClick = { showDetailsDialog.value = false }) {
                                            Text("Close")
                                        }
                                    },
                                    title = { Text("Responder Details") },
                                    text = {
                                        selectedResponder.value?.let { ChatDetailsPanel(it) }
                                            ?: Text("No responder selected")
                                    }
                                )
                            }
                        }
                    }

                    if (showSidebar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { showSidebar = false }
                        )

                        if (showSidebar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable { showSidebar = false }
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(250.dp),
                                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                color = Color(0xFFF7F4FA),
                                shadowElevation = 10.dp
                            ) {
                                ChatListSidebar(
                                    searchQuery = searchQuery.value,
                                    onSearchChange = { searchQuery.value = it },
                                    responders = responders,
                                    departments = departments,
                                    currentResponderRole = currentResponderRole,
                                    selectedResponder = selectedResponder.value,
                                    selectedDepartment = selectedDepartment.value,
                                    onChatSelected = { res, dept ->
                                        if (res != null) {
                                            vm.selectResponderAndLoadHistory(currentResponderId, res)
                                        } else if (dept != null) {
                                            vm.selectDepartmentAndLoadHistory(dept)
                                        }
                                        showSidebar = false
                                    }
                                )
                            }
                        }

                        if (showSidebar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable { showSidebar = false }
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(250.dp),
                                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                color = Color(0xFFF7F4FA),
                                shadowElevation = 10.dp
                            ) {
                                ChatListSidebar(
                                    searchQuery = searchQuery.value,
                                    onSearchChange = { searchQuery.value = it },
                                    responders = responders,
                                    departments = departments,
                                    currentResponderRole = currentResponderRole,
                                    selectedResponder = selectedResponder.value,
                                    selectedDepartment = selectedDepartment.value,
                                    onChatSelected = { res, dept ->
                                        if (res != null) {
                                            vm.selectResponderAndLoadHistory(
                                                currentResponderId,
                                                res
                                            )
                                        } else if (dept != null) {
                                            vm.selectDepartmentAndLoadHistory(dept)
                                        }
                                        showSidebar = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            ScreenSize.MEDIUM -> {
                ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(250.dp),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                    ) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open sidebar")
                        }

                        Text(
                            text = selectedResponder.value?.fullName
                                ?: selectedDepartment.value?.displayName
                                ?: "Select a chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // ✅ Clear chat
                        IconButton(onClick = {
                            vm.clearCurrentChatHistory()
                            Toast.makeText(ctx, "Chat cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                        }

                        // ✅ Info (isa nalang)
                        IconButton(onClick = { showDetailsDialog.value = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Details")
                        }
                        HorizontalDivider()

                        if (isTyping) {
                            Text(
                                text = "Typing…",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp)
                            )
                        }

                        ChatMessagesPanel(
                            messages = messages,
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            timeFmt = timeFmt,
                            listState = listState,
                            onReact = { messageId, emoji ->
                                vm.addReaction(messageId, emoji, currentResponderId)
                            }
                        )

                        HorizontalDivider()

                        ChatComposer(
                            text = messageInput.value,
                            onTextChange = { messageInput.value = it },
                            onSend = {
                                vm.sendTextMessage(
                                    senderId = currentResponderId,
                                    senderName = "You",
                                    text = messageInput.value
                                )
                                messageInput.value = ""
                            },
                            onAttach = { attachLauncher.launch("image/*") }
                        )

                        if (showDetailsDialog.value) {
                            AlertDialog(
                                onDismissRequest = { showDetailsDialog.value = false },
                                confirmButton = {
                                    TextButton(onClick = { showDetailsDialog.value = false }) {
                                        Text("Close")
                                    }
                                },
                                title = { Text("Responder Details") },
                                text = {
                                    selectedResponder.value?.let { ChatDetailsPanel(it) }
                                        ?: Text("No responder selected")
                                }
                            )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open sidebar")
                        }

                        Text(
                            text = selectedResponder.value?.fullName
                                ?: selectedDepartment.value?.displayName
                                ?: "Select a chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        IconButton(onClick = { /* optional call */ }) {
                            Icon(Icons.Default.Call, contentDescription = "Call")
                        }

                        // ✅ Clear chat
                        IconButton(onClick = {
                            vm.clearCurrentChatHistory()
                            Toast.makeText(ctx, "Chat cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                        }

                        // ✅ Info (optional — pwede mo tanggalin kung ayaw mo na)
                        IconButton(onClick = { showDetailsDialog.value = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
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
        AnimatedVisibility(
            visible = latest != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {

                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 120.dp)
                        .fillMaxWidth()
                        .clickable { vm.clearNotification() },

                    shape = RoundedCornerShape(18.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),

                    elevation = CardDefaults.cardElevation(10.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = latest ?: "",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { vm.clearNotification() }) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Dismiss",
                                tint = Color.White
                            )
                        }
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

        // 🔧 TEST: simulate incoming message notification
        LaunchedEffect(Unit) {
            delay(2000)
            vm.receiveIncomingPrivateMessage(
                meId = currentResponderId,
                peerId = "2",
                senderName = "Alice Johnson",
                role = "fire",
                body = "Need backup?"
            )
        }

        // Auto-scroll to newest message whenever messages   list size changes
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
    var tabIndex by remember { mutableStateOf(0) }

    if (collapsed) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(6.dp)
        ) {
            Text(
                "Sidebar",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F4FA))
            .padding(top = 10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    "Search chats",
                    color = Color(0xFF9A9A9A)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF6D6D6D)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF6D9998),
                unfocusedBorderColor = Color(0xFFD8D2DB)
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0xFFF7F4FA),
            contentColor = Color(0xFF4C8A89),
            divider = {
                HorizontalDivider(color = Color(0xFFD8D2DB))
            }
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = {
                    Text(
                        text = "Responders",
                        fontWeight = if (tabIndex == 0) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = {
                    Text(
                        text = "Departments",
                        fontWeight = if (tabIndex == 1) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (tabIndex) {
            0 -> {
                val filtered = responders.filter {
                    it.fullName.contains(searchQuery, true) || it.role.contains(searchQuery, true)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered) { r ->
                        val isSelected = selectedResponder?.id == r.id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clickable { onChatSelected(r, null) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White
                            ),
                            shape = RoundedCornerShape(22.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1EEF4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF8A8A8A),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = r.fullName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.width(8.dp))

                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (r.status.contains("online", true)) Color(0xFF4CAF50)
                                                    else Color.Gray
                                                )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = r.lastMessage.ifBlank { "No recent message" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF7A7A7A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (r.unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE74C3C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = r.unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered) { d ->
                        val isSelected = selectedDepartment?.name == d.name

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clickable { onChatSelected(null, d) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White
                            ),
                            shape = RoundedCornerShape(22.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1EEF4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = d.emoji,
                                        fontSize = 18.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = d.lastMessage.ifBlank { "No recent update" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF7A7A7A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (d.unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE74C3C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = d.unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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

@Composable
private fun ChatMessagesPanel(
    messages: List<com.ers.emergencyresponseapp.coordination.model.ChatMessage>,
    modifier: Modifier = Modifier,
    timeFmt: SimpleDateFormat,
    listState: LazyListState,
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No messages yet", color = Color.Gray)
        }
        return
    }

    fun dayKey(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val now = Calendar.getInstance()
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

        return when {
            sameDay -> "Today"
            isYesterday -> "Yesterday"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var lastDay: String? = null

        items(messages) { msg ->
            val key = dayKey(msg.createdAt)
            if (key != lastDay) {
                lastDay = key
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        color = Color(0xFFEDEDED),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = key,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            ChatBubble(
                msg = msg,
                timeLabel = timeFmt.format(Date(msg.createdAt)),
                onReact = { messageId, emoji ->
                    onReact(messageId, emoji)
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    timeLabel: String,
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    val isOwn = msg.senderName == "You"
    val bubbleColor = if (isOwn) Color(0xFF4C8A89) else Color.White
    val textColor = if (isOwn) Color.White else Color(0xFF1B1B1B)
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    var showReactions by remember(msg.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalAlignment = alignment
    ) {
        Box {
            Column(
                horizontalAlignment = alignment
            ) {
                if (showReactions) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("👍", "❤️", "😮", "🔥", "🚨").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier.clickable {
                                        onReact(msg.id, emoji)
                                        showReactions = false
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = if (isOwn) 0.dp else 2.dp,
                    shadowElevation = if (isOwn) 0.dp else 2.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {
                                showReactions = false
                            },
                            onLongClick = {
                                showReactions = !showReactions
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (!isOwn && msg.senderName.isNotBlank() && msg.senderName != "You") {
                            Text(
                                text = msg.senderName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = msg.text ?: "",
                            color = textColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (msg.reactions.isNotEmpty()) {
            val reactionCounts = msg.reactions.groupingBy { it.emoji }.eachCount()

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reactionCounts.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F1F1)
                    ) {
                        Text(
                            text = "$emoji $count",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }

        Text(
            text = timeLabel,
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
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


@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 6.dp, color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            val enabled = text.trim().isNotEmpty()
            FilledIconButton(
                onClick = onSend,
                enabled = enabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (enabled) Color(0xFF4C8A89) else Color(0xFFBDBDBD),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}