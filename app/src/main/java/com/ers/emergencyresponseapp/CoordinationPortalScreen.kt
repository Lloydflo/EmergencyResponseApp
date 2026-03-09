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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private enum class ScreenSize { SMALL, MEDIUM, LARGE }




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinationPortalScreen(
    currentResponderId: String,
    currentResponderRole: String // "fire" | "medical" | "police"
) {
    var muted by remember { mutableStateOf(false) }
    val vm: CoordinationViewModel = viewModel()

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
    val selectedResponder = vm.selectedResponder
    val selectedDepartment = vm.selectedDepartment
    val responders = vm.responders
    val departments = vm.departments
    val messages = vm.messages
    // list state for chat messages so we can auto-scroll
    val listState = rememberLazyListState()
    // track unseen messages when user is scrolled up
    val unseenCount = remember { mutableStateOf(0) }
    val searchQuery = remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val showDetailsDialog = remember { mutableStateOf(false) }
    var showSidebar by remember { mutableStateOf(false) }
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
                                        Toast.makeText(ctx, "Chat cleared", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Clear chat")
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
                                listState = listState
                            )

                            HorizontalDivider()

                            ChatComposer(
                                text = messageInput.value,
                                onTextChange = { messageInput.value = it },
                                onSend = { doSend() },
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

                    AnimatedVisibility(
                        visible = showSidebar,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { showSidebar = false }
                        )
                    }

                    AnimatedVisibility(
                        visible = showSidebar,
                        enter = slideInHorizontally(initialOffsetX = { -it / 2 }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(220.dp),
                            color = Color(0xFFF5F0F7),
                            shadowElevation = 8.dp
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
                                    if (res != null) vm.selectResponderAndLoadHistory(currentResponderId, res)
                                    else if (dept != null) vm.selectDepartmentAndLoadHistory(dept)
                                    showSidebar = false
                                }
                            )
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
                                listState = listState
                            )

                            HorizontalDivider()

                            ChatComposer(
                                text = messageInput.value,
                                onTextChange = { messageInput.value = it },
                                onSend = { doSend() },
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
    var tabIndex by remember { mutableStateOf(0) } // 0=Responders, 1=Departments

    if (collapsed) {
        // keep your compact layout if you want — optional
        Column(modifier = Modifier.fillMaxHeight().padding(6.dp)) {
            Text("Sidebar", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            singleLine = true
        )

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Responders") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Departments") })
        }

        when (tabIndex) {
            0 -> {
                val filtered = responders.filter {
                    it.fullName.contains(searchQuery, true) || it.role.contains(searchQuery, true)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { r ->
                        val isSelected = selectedResponder?.id == r.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { onChatSelected(r, null) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEDEDED)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray)
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(8.dp))

                                        // online dot
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (r.status.contains("online", true)) Color(0xFF4CAF50) else Color.Gray)
                                        )
                                    }
                                    Text(
                                        text = r.lastMessage.ifBlank { "No recent message" },
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (r.unreadCount > 0) {
                                    Badge(containerColor = Color(0xFFF44336)) {
                                        Text(r.unreadCount.toString(), color = Color.White, fontSize = 10.sp)
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

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { d ->
                        val isSelected = selectedDepartment?.name == d.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { onChatSelected(null, d) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(d.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = d.lastMessage.ifBlank { "No recent update" },
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (d.unreadCount > 0) {
                                    Badge(containerColor = Color(0xFFF44336)) {
                                        Text(d.unreadCount.toString(), color = Color.White, fontSize = 10.sp)
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
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    timeFmt: SimpleDateFormat,
    listState: LazyListState
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
            ChatBubble(msg, timeFmt.format(Date(msg.createdAt)))
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, timeLabel: String) {
    val isOwn = msg.isOwn
    val bubbleColor = if (isOwn) Color(0xFF4C8A89) else Color.White
    val textColor = if (isOwn) Color.White else Color(0xFF1B1B1B)
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = if (isOwn) 0.dp else 2.dp,
            shadowElevation = if (isOwn) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (!isOwn && msg.senderName.isNotBlank() && msg.senderName != "You") {
                    Text(
                        msg.senderName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(msg.body, color = textColor, fontSize = 14.sp)
            }
        }

        Text(
            timeLabel,
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
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