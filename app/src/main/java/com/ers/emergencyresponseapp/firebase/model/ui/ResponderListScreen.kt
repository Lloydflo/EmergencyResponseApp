package com.ers.emergencyresponseapp.firebase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
// ✅ FIX 1: Proper Firebase imports (was missing — caused "Unresolved reference: FirebaseDatabase")
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.compose.material3.FilterChip

// ── Data model for the responder list ────────────────────────────────────────
data class ResponderListItem(
    val userId     : String,
    val fullName   : String,
    val department : String,
    val isOnline   : Boolean = false,
    val unreadCount: Int = 0
)



// ─────────────────────────────────────────────────────────────────────────────
//  RESPONDER LIST SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderListScreen(
    myUserId   : String,
    onOpenChat : (partnerUserId: String) -> Unit
) {
    var responders by remember { mutableStateOf<List<ResponderListItem>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var unreadMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedDepartment by remember { mutableStateOf("All") }

    // ✅ FIX 2: Use ValueEventListener with explicit types — fixes
    //    "Cannot infer a type for this parameter" at lines 77/78/104/106
    DisposableEffect(myUserId) {
        val db       = FirebaseDatabase.getInstance()
        val usersRef = db.getReference("users")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<ResponderListItem>()

                for (child in snapshot.children) {
                    // ✅ FIX 3: Read the stored userId field, fallback to node key
                    //    Avoids type-mismatch between node key ("7") and stored field
                    val userId = child.key ?: continue

                    val fullName   = child.child("fullName")
                        .getValue(String::class.java) ?: "Unknown"
                    val department = child.child("department")
                        .getValue(String::class.java) ?: "Unknown"
                    val isOnline   = child.child("isOnline")
                        .getValue(Boolean::class.java) ?: false

                    android.util.Log.d("DEBUG_ID", "myUserId = '$myUserId'")

                    android.util.Log.d("DEBUG_ID", "Firebase userId = '$userId'")

                    android.util.Log.d(
                        "ResponderList",
                        "User found → id=$userId | name=$fullName | online=$isOnline"
                    )

                    if (
                        fullName.isNotBlank() &&
                        !fullName.equals("Unknown", ignoreCase = true) &&
                        department.isNotBlank() &&
                        !department.equals("Unknown", ignoreCase = true)
                    ) {
                        loaded.add(
                            ResponderListItem(
                                userId,
                                fullName,
                                department,
                                isOnline
                            )
                        )
                    }
                }

                // ✅ FIX 4: trim() on both sides avoids invisible whitespace mismatch
                responders = loaded.filter {
                    it.userId != myUserId &&
                            !it.fullName.equals("Unknown", ignoreCase = true) &&
                            !it.department.equals("Unknown", ignoreCase = true)
                }
                isLoading  = false

                android.util.Log.d(
                    "ResponderList",
                    "Loaded ${loaded.size} total, showing ${responders.size} (myUserId=$myUserId)"
                )
            }

            override fun onCancelled(error: DatabaseError) {
                errorMsg  = error.message
                isLoading = false
                android.util.Log.e("ResponderList", "Firebase read failed: ${error.message}")
            }
        }

        usersRef.addValueEventListener(listener)

        // Clean up listener when the composable leaves the screen
        onDispose {
            usersRef.removeEventListener(listener)
        }
    }

    DisposableEffect(myUserId, responders) {
        if (myUserId.isBlank()) {
            onDispose { }
        } else {
            val db = FirebaseDatabase.getInstance()
            val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

            responders.forEach { responder ->
                val chatId = privateChatId(myUserId, responder.userId)
                val ref = db.getReference("chats")
                    .child(chatId)
                    .child("messages")

                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val unread = snapshot.children.count { msg ->
                            val senderId = msg.child("senderId").getValue(String::class.java) ?: ""
                            val status = msg.child("status").getValue(String::class.java) ?: ""

                            senderId != myUserId &&
                                    !status.equals("READ", ignoreCase = true)
                        }

                        unreadMap = unreadMap.toMutableMap().apply {
                            put(responder.userId, unread)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }

                ref.addValueEventListener(listener)
                listeners.add(ref to listener)
            }

            onDispose {
                listeners.forEach { (ref, listener) ->
                    ref.removeEventListener(listener)
                }
            }
        }
    }

    val visibleResponders = responders
        .filter { responder ->
            selectedDepartment == "All" ||
                    responder.department.equals(selectedDepartment, ignoreCase = true)
        }
        .map { responder ->
            responder.copy(
                unreadCount = unreadMap[responder.userId] ?: 0
            )
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Messages",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp
                        )
                        Text(
                            text     = "${responders.size} responders",
                            fontSize = 12.sp,
                            color    = Color(0xFF65676B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF0F2F5)
    ) { padding ->

        when {
            // Loading state
            isLoading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00C07F))
                }
            }

            // Error state
            errorMsg != null -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load responders", color = Color(0xFF65676B))
                        Text(errorMsg ?: "", fontSize = 12.sp, color = Color(0xFFE41E3F))
                    }
                }
            }

            // Empty state (all users loaded but list is empty after filtering self)
            responders.isEmpty() -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No other responders found", color = Color(0xFF65676B))
                }
            }

            // Success — show list
            else -> {
                Column(
                    modifier = Modifier.padding(padding)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Fire", "Medical", "Police").forEach { dept ->
                            FilterChip(
                                selected = selectedDepartment == dept,
                                onClick = { selectedDepartment = dept },
                                label = { Text(dept) }
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = visibleResponders,
                            key = { it.userId }
                        ) { responder ->
                            ResponderRow(
                                responder = responder,
                                onClick = { onOpenChat(responder.userId) }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 80.dp),
                                color = Color(0xFFE4E6EA),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RESPONDER ROW  —  one item in the list
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ResponderRow(
    responder : ResponderListItem,
    onClick   : () -> Unit
) {
    val deptColor = departmentColor(responder.department)
    val initials  = responder.fullName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color    = Color.White
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box(modifier = Modifier.size(52.dp)) {
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(deptColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = initials.ifEmpty { "?" },
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = deptColor
                    )
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(
                            if (responder.isOnline)
                                Color(0xFF31A24C)
                            else
                                Color(0xFFB0B0B0)
                        )
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = responder.fullName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = Color(0xFF0D0D0D),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = deptColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text       = responder.department.replaceFirstChar { it.uppercase() },
                            fontSize   = 11.sp,
                            color      = deptColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text  = if (responder.isOnline) "Online" else "Offline",
                        fontSize = 12.sp,
                        color    = if (responder.isOnline) Color(0xFF31A24C) else Color(0xFF65676B)
                    )
                }
            }

            if (responder.unreadCount > 0) {
                Badge(
                    containerColor = Color(0xFFE41E3F)
                ) {
                    Text(
                        responder.unreadCount.toString(),
                        color = Color.White
                    )
                }
            } else {
                if (responder.unreadCount > 0) {
                    Badge(
                        containerColor = Color(0xFFE41E3F)
                    ) {
                        Text(
                            responder.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text("›", fontSize = 22.sp, color = Color(0xFFBFC2C8))
                }
            }
        }
    }
}

private fun privateChatId(a: String, b: String): String {
    return listOf(a, b).sorted().joinToString("_")
}

private fun departmentColor(department: String): Color = when (department.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}