package com.ers.emergencyresponseapp.firebase.ui

// ═══════════════════════════════════════════════════════════════════════════════
//  FILE 6 of 8 — ResponderListScreen.kt
//  Place in:  app/src/main/java/com/ers/emergencyresponseapp/firebase/ui/
//
//  This screen shows the list of all responders the current user can chat with.
//  It's the "inbox" — tap a responder to open their chat.
//
//  For now, it shows the mock responders from your existing app.
//  Later you can replace the list with a real API call to your MySQL backend.
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*

// ── A simple data holder for the responder list ───────────────────────────────
// This uses your existing responder model from the app.
// If you already have a different data class, use that instead.
data class ResponderListItem(
    val userId     : String,
    val fullName   : String,
    val department : String,
    val isOnline   : Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  RESPONDER LIST SCREEN
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Shows a Messenger-style list of responders.
 * Tap any row to open a chat with that responder.
 *
 * Parameters:
 *   myUserId    — logged-in user's ID
 *   responders  — list of other responders (from your API or mock data)
 *   onOpenChat  — called with the selected responder's userId
 *
 * Example usage:
 *   ResponderListScreen(
 *       myUserId   = "123",
 *       responders = listOf(
 *           ResponderListItem("456", "Alice Johnson", "Fire", true),
 *           ResponderListItem("789", "Bob Smith",     "Medical", false)
 *       ),
 *       onOpenChat = { partnerId ->
 *           navController.navigate("firebase_chat/$myUserId/$partnerId")
 *       }
 *   )
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderListScreen(
    myUserId   : String,
    onOpenChat : (partnerUserId: String) -> Unit
) {
    var responders by remember { mutableStateOf<List<ResponderListItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Load responders from Firebase /users node
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()
        db.getReference("users").get().addOnSuccessListener { snapshot ->
            val loaded = snapshot.children.mapNotNull { child ->
                val userId     = child.key ?: return@mapNotNull null
                val fullName   = child.child("fullName").getValue(String::class.java) ?: "Unknown"
                val department = child.child("department").getValue(String::class.java) ?: "Unknown"
                val isOnline   = child.child("isOnline").getValue(Boolean::class.java) ?: false
                ResponderListItem(userId, fullName, department, isOnline)
            }
            responders = loaded
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Messages", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("${responders.size} responders", fontSize = 12.sp, color = Color(0xFF65676B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF0F2F5)
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = responders.filter { it.userId != myUserId }, // exclude yourself
                key   = { it.userId }
            ) { responder ->
                ResponderRow(
                    responder  = responder,
                    onClick    = { onOpenChat(responder.userId) }
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 80.dp),
                    color     = Color(0xFFE4E6EA),
                    thickness = 0.5.dp
                )
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
    val initials  = responder.fullName.split(" ")
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

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
                // Online dot
                if (responder.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF31A24C))
                    )
                }
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = deptColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text     = responder.department,
                            fontSize = 11.sp,
                            color    = deptColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text     = if (responder.isOnline) "Online" else "Offline",
                        fontSize = 12.sp,
                        color    = if (responder.isOnline) Color(0xFF31A24C) else Color(0xFF65676B)
                    )
                }
            }

            // Chevron
            Text("›", fontSize = 22.sp, color = Color(0xFFBFC2C8))
        }
    }
}

private fun departmentColor(department: String): Color = when (department.lowercase()) {
    "fire"    -> Color(0xFFFF6B35)
    "medical" -> Color(0xFF2ECC71)
    "police"  -> Color(0xFF3498DB)
    else      -> Color(0xFF8A8A8A)
}