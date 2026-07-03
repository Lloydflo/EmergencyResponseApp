package com.ers.emergencyresponseapp.firebase.ui

// ═══════════════════════════════════════════════════════════════════════════════
//  FILE 5 of 8 — FirebaseChatScreen.kt
//  Place in:  app/src/main/java/com/ers/emergencyresponseapp/firebase/ui/
//
//  This is the full chat screen UI built with Jetpack Compose.
//  It shows:
//    • A top bar with the chat partner's name + online status
//    • A scrollable list of message bubbles
//    • An input field and send button at the bottom
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ers.emergencyresponseapp.AppScreenTracker
import com.ers.emergencyresponseapp.firebase.model.FirebaseMessage
import com.ers.emergencyresponseapp.firebase.viewmodel.FirebaseChatViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ── Colors (Messenger-style) ──────────────────────────────────────────────────
private val MyBubbleColor      = Color(0xFF00C07F)   // green  — your messages (right)
private val TheirBubbleColor   = Color(0xFFFFFFFF)   // white  — partner's messages (left)
private val MyTextColor        = Color.White
private val TheirTextColor     = Color(0xFF0D0D0D)
private val BackgroundColor    = Color(0xFFF0F2F5)
private val TopBarColor        = Color(0xFFFFFFFF)
private val OnlineGreen        = Color(0xFF31A24C)

// ─────────────────────────────────────────────────────────────────────────────
//  MAIN CHAT SCREEN
// ─────────────────────────────────────────────────────────────────────────────
/**
 * The main chat screen between two responders.
 *
 * Parameters:
 *   myUserId      — the logged-in user's ID (from your MySQL login)
 *   partnerUserId — the ID of the person you're chatting with
 *   onBack        — lambda called when the back arrow is tapped
 *
 * Example usage from your NavGraph / calling screen:
 *   FirebaseChatScreen(
 *       myUserId      = "123",
 *       partnerUserId = "456",
 *       onBack        = { navController.popBackStack() }
 *   )
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FirebaseChatScreen(
    myUserId      : String,
    partnerUserId : String,
    onBack        : () -> Unit,
    viewModel     : FirebaseChatViewModel = viewModel()
) {
    // ── Collect state from ViewModel ──────────────────────────────────────────
    val messages    by viewModel.messages.collectAsStateWithLifecycle()
    val isSending   by viewModel.isSending.collectAsStateWithLifecycle()
    val chatPartner by viewModel.chatPartner.collectAsStateWithLifecycle()
    val errorMsg    by viewModel.errorMessage.collectAsStateWithLifecycle()

    // ── Local state ───────────────────────────────────────────────────────────
    var inputText     by remember { mutableStateOf("") }
    val listState     = rememberLazyListState()
    val snackbarHost  = remember { SnackbarHostState() }
    var revealedTimestampMessageKey by remember { mutableStateOf<String?>(null) }
    val orderedMessages = remember(messages) {
        messages.sortedBy { normalizeTimestamp(it.timestamp) }
    }
    val chatItems = remember(orderedMessages) {
        val timeline = mutableListOf<ChatListItem>()
        var lastDayKey: String? = null
        orderedMessages.withIndex().forEach { indexedMessage ->
            val ts = normalizeTimestamp(indexedMessage.value.timestamp)
            val currentDayKey = dayKey(ts)
            if (currentDayKey != lastDayKey) {
                timeline.add(
                    ChatListItem.DayHeader(
                        dayKey = currentDayKey,
                        dayLabel = formatDaySeparatorLabel(ts)
                    )
                )
                lastDayKey = currentDayKey
            }
            timeline.add(ChatListItem.MessageRow(indexedMessage))
        }
        timeline
    }

    LaunchedEffect(revealedTimestampMessageKey) {
        val selected = revealedTimestampMessageKey ?: return@LaunchedEffect
        delay(2500)
        if (revealedTimestampMessageKey == selected) {
            revealedTimestampMessageKey = null
        }
    }

    // ── Open the chat when the screen first loads ─────────────────────────────
    LaunchedEffect(myUserId, partnerUserId) {
        AppScreenTracker.currentScreen = "CHAT"
        viewModel.openChat(myUserId, partnerUserId)
    }

    // ── Auto-scroll to bottom when a new message arrives ─────────────────────
    // BUT ONLY if the user is already at the bottom (not scrolling up to read old messages)
    LaunchedEffect(orderedMessages.size) {
        if (orderedMessages.isNotEmpty()) {
            // Check if user is currently viewing the bottom of the list
            val canScroll = listState.canScrollForward
            
            // Only auto-scroll if at bottom (canScrollForward = false means we're at bottom)
            // OR if this is the first message load
            if (!canScroll || orderedMessages.size <= 1) {
                listState.animateScrollToItem(orderedMessages.size - 1)
            }
        }
    }

    // ── Show error in Snackbar ────────────────────────────────────────────────
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            snackbarHost.showSnackbar(errorMsg!!)
            viewModel.clearError()
        }
    }

    // ── Screen layout ─────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundColor,
        snackbarHost   = { SnackbarHost(snackbarHost) },

        // ── Top bar ───────────────────────────────────────────────────────────
        topBar = {
            Surface(color = TopBarColor, shadowElevation = 3.dp) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = Color(0xFF0D0D0D)
                        )
                    }

                    // Avatar (initials circle)
                    val partnerName   = chatPartner?.fullName ?: "Loading…"
                    val initials      = partnerName.split(" ")
                        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                    val department    = chatPartner?.department ?: ""
                    val avatarColor   = departmentColor(department)

                    Box(
                        modifier         = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = initials.ifEmpty { "?" },
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = avatarColor
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Name + online status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = partnerName,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = Color(0xFF0D0D0D),
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        // Show "Online" or "Last seen X min ago"
                        OnlineStatusText(
                            isOnline = chatPartner?.isOnline ?: false,
                            lastSeen = chatPartner?.lastSeen ?: 0L
                        )
                    }

                    // Department badge in top right
                    if (department.isNotBlank()) {
                        Surface(
                            color = avatarColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text     = department,
                                fontSize = 11.sp,
                                color    = avatarColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        },

        // ── Bottom input bar ──────────────────────────────────────────────────
        bottomBar = {
            MessageInputBar(
                text        = inputText,
                onTextChange = { inputText = it },
                isSending   = isSending,
                onSend      = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(
                            senderId   = myUserId,
                            receiverId = partnerUserId,
                            text       = inputText
                        )
                        inputText = "" // clear input after sending
                    }
                }
            )
        }

    ) { padding ->

        // ── Message list ──────────────────────────────────────────────────────
        if (orderedMessages.isEmpty()) {
            // Empty state
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("👋", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text      = "Say hello to ${chatPartner?.fullName ?: "your colleague"}!",
                        fontSize  = 15.sp,
                        color     = Color(0xFF65676B)
                    )
                }
            }
        } else {
            LazyColumn(
                state          = listState,
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = chatItems,
                    key = { _, item ->
                        when (item) {
                            is ChatListItem.DayHeader -> "day_${item.dayKey}"
                            is ChatListItem.MessageRow -> {
                                stableMessageKey(item.indexedMessage.value, item.indexedMessage.index)
                            }
                        }
                    }
                ) { _, item ->
                    when (item) {
                        is ChatListItem.DayHeader -> DaySeparator(label = item.dayLabel)
                        is ChatListItem.MessageRow -> {
                            val message = item.indexedMessage.value
                            val messageKey = stableMessageKey(message, item.indexedMessage.index)
                            MessageBubble(
                                message  = message,
                                isMyMsg  = message.senderId == myUserId,
                                isTimestampVisible = revealedTimestampMessageKey == messageKey,
                                onRevealTimestamp = { revealedTimestampMessageKey = messageKey }
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class ChatListItem {
    data class DayHeader(
        val dayKey: String,
        val dayLabel: String
    ) : ChatListItem()

    data class MessageRow(
        val indexedMessage: IndexedValue<FirebaseMessage>
    ) : ChatListItem()
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFFE8ECF1),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFF5F6670),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun dayKey(timestampMs: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
}

private fun formatDaySeparatorLabel(timestampMs: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
}

// ─────────────────────────────────────────────────────────────────────────────
//  MESSAGE BUBBLE  —  the individual chat bubble component
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message : FirebaseMessage,
    isMyMsg : Boolean,
    isTimestampVisible: Boolean,
    onRevealTimestamp: () -> Unit
) {
    val timeLabel = remember(message.timestamp) { formatMessageTimestamp(message.timestamp) }

    // isMyMsg = true  → align to the RIGHT (your message, green)
    // isMyMsg = false → align to the LEFT  (their message, white)
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = if (isMyMsg) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMyMsg) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // The bubble itself
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = onRevealTimestamp,
                    onLongClick = onRevealTimestamp
                ),
                color  = if (isMyMsg) MyBubbleColor else TheirBubbleColor,
                shape  = RoundedCornerShape(
                    topStart    = 18.dp,
                    topEnd      = 18.dp,
                    bottomEnd   = if (isMyMsg) 4.dp else 18.dp,   // pointed corner for sender
                    bottomStart = if (isMyMsg) 18.dp else 4.dp    // pointed corner for receiver
                ),
                shadowElevation = if (isMyMsg) 0.dp else 1.dp
            ) {
                Text(
                    text     = message.text,
                    color    = if (isMyMsg) MyTextColor else TheirTextColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            // Date/time appears only when message is tapped/held; status stays available for my messages.
            if (isTimestampVisible || isMyMsg) {
                Row(
                    modifier              = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (isTimestampVisible) {
                        Text(
                            text     = timeLabel,
                            fontSize = 10.sp,
                            color    = Color(0xFF65676B)
                        )
                    }

                    // Show status ticks only on YOUR messages
                    if (isMyMsg) {
                        val (statusText, statusColor) = when (message.status) {
                            "seen"      -> "✓✓" to MyBubbleColor
                            "delivered" -> "✓✓" to Color(0xFF65676B)
                            else         -> "✓"  to Color(0xFF65676B)
                        }
                        Text(
                            text       = statusText,
                            fontSize   = 10.sp,
                            color      = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun stableMessageKey(message: FirebaseMessage, index: Int): String {
    return message.messageId.ifBlank {
        "${message.senderId}_${message.receiverId}_${normalizeTimestamp(message.timestamp)}_$index"
    }
}

private fun normalizeTimestamp(raw: Long): Long {
    if (raw <= 0L) return System.currentTimeMillis()
    // Some rows may be stored in seconds; convert to millis for stable ordering/formatting.
    return if (raw < 1_000_000_000_000L) raw * 1000L else raw
}

private fun formatMessageTimestamp(raw: Long): String {
    val ts = normalizeTimestamp(raw)
    return SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault()).format(Date(ts))
}

// ─────────────────────────────────────────────────────────────────────────────
//  MESSAGE INPUT BAR  —  text field + send button at the bottom
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MessageInputBar(
    text         : String,
    onTextChange : (String) -> Unit,
    isSending    : Boolean,
    onSend       : () -> Unit
) {
    Surface(
        color           = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),   // moves bar above keyboard
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text field
            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                placeholder   = { Text("Type a message…", fontSize = 14.sp, color = Color(0xFF65676B)) },
                modifier      = Modifier.weight(1f),
                singleLine    = false,
                maxLines      = 4,
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFF00C07F),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor   = Color(0xFFF0F2F5),
                    unfocusedContainerColor = Color(0xFFF0F2F5)
                )
            )

            Spacer(Modifier.width(8.dp))

            // Send button — green when there's text, grey when empty
            val hasText = text.isNotBlank()
            Box(
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasText && !isSending) Color(0xFF00C07F) else Color(0xFFCCCCCC)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    // Show spinner while sending
                    CircularProgressIndicator(
                        modifier  = Modifier.size(22.dp),
                        color     = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick  = onSend,
                        enabled  = hasText
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ONLINE STATUS TEXT  —  shows "Online" or "Last seen X min ago"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OnlineStatusText(isOnline: Boolean, lastSeen: Long) {
    if (isOnline) {
        Text(
            text     = "● Online",
            fontSize = 12.sp,
            color    = OnlineGreen
        )
    } else if (lastSeen > 0L) {
        val minutesAgo = ((System.currentTimeMillis() - lastSeen) / 60_000).toInt()
        val label = when {
            minutesAgo < 1  -> "Last seen just now"
            minutesAgo < 60 -> "Last seen ${minutesAgo}m ago"
            minutesAgo < 1440 -> "Last seen ${minutesAgo / 60}h ago"
            else            -> "Last seen ${minutesAgo / 1440}d ago"
        }
        Text(text = label, fontSize = 12.sp, color = Color(0xFF65676B))
    } else {
        Text(text = "Offline", fontSize = 12.sp, color = Color(0xFF65676B))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPER — Department color
// ─────────────────────────────────────────────────────────────────────────────
private fun departmentColor(department: String): Color = when (department.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}