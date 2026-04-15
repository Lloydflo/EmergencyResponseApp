package com.ers.emergencyresponseapp.coordination.model.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.DepartmentInfo
import com.ers.emergencyresponseapp.ResponderBrief
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.ChatThread
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.ThreadType
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.UUID

class CoordinationViewModel(application: Application) : AndroidViewModel(application) {

    // ── Repositories ─────────────────────────────────────────────────────────
    private val firebaseRepo = FirebaseChatRepository()
    private val db = FirebaseDatabase.getInstance().reference

    // ── UI State ──────────────────────────────────────────────────────────────
    val responders   = mutableStateListOf<ResponderBrief>()
    val departments  = mutableStateListOf<DepartmentInfo>()
    val messages     = mutableStateListOf<ChatMessage>()

    val selectedResponder  = mutableStateOf<ResponderBrief?>(null)
    val selectedDepartment = mutableStateOf<DepartmentInfo?>(null)
    val latestNotification = mutableStateOf<String?>(null)
    private val currentThread = mutableStateOf<ChatThread?>(null)

    var isPeerTyping: Boolean by mutableStateOf(false)
        private set

    // ── Internal ──────────────────────────────────────────────────────────────
    private var myUserId   = ""
    private var myUserName = ""
    private var myRole     = ""

    // Active Firebase listeners so we can remove them on disconnect
    private var messagesListener: ValueEventListener? = null
    private var messagesListenerPath: String? = null
    private var respondersListener: ValueEventListener? = null

    // ─────────────────────────────────────────────────────────────────────────
    //  CONNECT — called once when CoordinationPortalScreen opens
    // ─────────────────────────────────────────────────────────────────────────
    fun connectRealtime(userId: String, userName: String, userRole: String) {
        myUserId   = userId
        myUserName = userName
        myRole     = userRole

        viewModelScope.launch {
            try {
                // Save/update this user's profile in Firebase
                firebaseRepo.saveUserToFirebase(
                    userId     = userId,
                    fullName   = userName,
                    email      = "",
                    department = userRole
                )
                // Mark online
                firebaseRepo.setOnlineStatus(userId, isOnline = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load the real list of responders from Firebase /users
        loadRespondersFromFirebase(userId)

        // Load static department list (departments are role-based, not user accounts)
        if (departments.isEmpty()) {
            departments.addAll(listOf(
                DepartmentInfo("fire",    "Fire Department",    "🔥", "Fire team assembled",  0),
                DepartmentInfo("medical", "Medical Department", "🚑", "Ambulance dispatched", 0),
                DepartmentInfo("police",  "Police Department",  "🚓", "Patrol on scene",      0)
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD REAL RESPONDERS FROM FIREBASE /users
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadRespondersFromFirebase(myId: String) {
        // Remove old listener if any
        respondersListener?.let { db.child("users").removeEventListener(it) }

        respondersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<ResponderBrief>()
                for (child in snapshot.children) {
                    val uid        = child.child("userId").getValue(String::class.java) ?: child.key ?: continue
                    val fullName   = child.child("fullName").getValue(String::class.java) ?: "Unknown"
                    val department = child.child("department").getValue(String::class.java) ?: "general"
                    val isOnline   = child.child("isOnline").getValue(Boolean::class.java) ?: false

                    // Skip the current user — you don't chat with yourself
                    if (uid == myId) continue

                    // Find existing entry to preserve unread count / last message
                    val existing = responders.firstOrNull { it.id == uid }

                    loaded.add(
                        ResponderBrief(
                            id          = uid,
                            username    = uid,
                            fullName    = fullName,
                            role        = department,
                            status      = if (isOnline) "online" else "offline",
                            lastMessage = existing?.lastMessage ?: "",
                            unreadCount = existing?.unreadCount ?: 0
                        )
                    )
                }
                // Update the list on main thread (StateList triggers recompose)
                responders.clear()
                responders.addAll(loaded)
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("users").addValueEventListener(respondersListener!!)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DISCONNECT
    // ─────────────────────────────────────────────────────────────────────────
    fun disconnectRealtime() {
        viewModelScope.launch {
            try {
                firebaseRepo.setOnlineStatus(myUserId, isOnline = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Remove Firebase listeners to avoid memory leaks
        respondersListener?.let { db.child("users").removeEventListener(it) }
        stopListeningToMessages()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT RESPONDER — open a real Firebase chat thread
    // ─────────────────────────────────────────────────────────────────────────
    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        selectedResponder.value  = responder
        selectedDepartment.value = null
        markResponderRead(responder.id)

        val thread = ChatThread(
            id           = buildChatId(meId, responder.id),
            type         = ThreadType.PRIVATE,
            name         = responder.fullName,
            participants = listOf(meId, responder.id)
        )
        currentThread.value = thread
        messages.clear()

        // Start real-time message listener for this thread
        listenToMessages(thread.id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT DEPARTMENT — open a department group channel
    // ─────────────────────────────────────────────────────────────────────────
    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        selectedDepartment.value = dept
        selectedResponder.value  = null
        markDepartmentRead(dept.name)

        val thread = ChatThread(
            id           = "dept_${dept.name}",
            type         = ThreadType.DEPARTMENT,
            name         = dept.displayName,
            participants = listOf(dept.name)
        )
        currentThread.value = thread
        messages.clear()

        listenToMessages(thread.id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FIREBASE REAL-TIME MESSAGE LISTENER
    // ─────────────────────────────────────────────────────────────────────────
    private fun listenToMessages(threadId: String) {
        // Remove any existing listener first
        stopListeningToMessages()

        messagesListenerPath = "messages/$threadId"

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val senderId   = child.child("senderId").getValue(String::class.java)   ?: continue
                    val senderName = child.child("senderName").getValue(String::class.java) ?: "Unknown"
                    val text       = child.child("text").getValue(String::class.java)       ?: ""
                    val role       = child.child("role").getValue(String::class.java)       ?: ""
                    val createdAt  = child.child("createdAt").getValue(Long::class.java)    ?: 0L
                    val msgId      = child.key ?: UUID.randomUUID().toString()

                    loaded.add(
                        ChatMessage(
                            id         = msgId,
                            threadId   = threadId,
                            senderId   = senderId,
                            senderName = senderName,
                            role       = role,
                            type       = MessageType.TEXT,
                            text       = text,
                            createdAt  = createdAt,
                            status     = MessageStatus.READ,
                            // FIX: isOwn based on actual userId, NOT hardcoded "You"
                            isOwn      = senderId == myUserId
                        )
                    )
                }
                messages.clear()
                messages.addAll(loaded.sortedBy { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("messages").child(threadId)
            .addValueEventListener(messagesListener!!)
    }

    private fun stopListeningToMessages() {
        val path = messagesListenerPath
        val listener = messagesListener
        if (path != null && listener != null) {
            db.child(path).removeEventListener(listener)
        }
        messagesListener = null
        messagesListenerPath = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND MESSAGE — writes to Firebase, listener picks it up on ALL devices
    // ─────────────────────────────────────────────────────────────────────────
    fun sendMockPrivateMessage(meId: String, peer: ResponderBrief, body: String) {
        val threadId = buildChatId(meId, peer.id)
        pushMessageToFirebase(
            threadId   = threadId,
            senderId   = meId,
            senderName = myUserName.ifBlank { meId },
            role       = myRole,
            text       = body
        )
    }

    fun sendMockDepartmentMessage(meId: String, department: String, body: String) {
        val threadId = "dept_$department"
        pushMessageToFirebase(
            threadId   = threadId,
            senderId   = meId,
            senderName = myUserName.ifBlank { meId },
            role       = myRole,
            text       = body
        )
    }

    private fun pushMessageToFirebase(
        threadId   : String,
        senderId   : String,
        senderName : String,
        role       : String,
        text       : String
    ) {
        val msgRef = db.child("messages").child(threadId).push()
        val data = mapOf(
            "senderId"   to senderId,
            "senderName" to senderName,
            "role"       to role,
            "text"       to text,
            "createdAt"  to System.currentTimeMillis()
        )
        msgRef.setValue(data)

        // Also update the thread's lastMessage so the inbox shows a preview
        db.child("threads").child(threadId).updateChildren(mapOf(
            "lastMessage"     to text,
            "lastMessageTime" to System.currentTimeMillis()
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILE / IMAGE SENDING
    // ─────────────────────────────────────────────────────────────────────────
    fun sendFileMessage(meId: String, peer: ResponderBrief, uri: Uri, fileName: String, isImage: Boolean) {
        val msgType  = if (isImage) MessageType.IMAGE else MessageType.FILE
        val threadId = buildChatId(meId, peer.id)
        val msgId    = UUID.randomUUID().toString()
        // Add locally for instant feedback (URI is local, can't push to Firebase easily without Storage)
        messages.add(
            ChatMessage(
                id             = msgId,
                senderId       = meId,
                senderName     = myUserName.ifBlank { meId },
                threadId       = threadId,
                type           = msgType,
                text           = null,
                role           = myRole,
                createdAt      = System.currentTimeMillis(),
                status         = MessageStatus.SENT,
                isOwn          = true,
                attachmentUri  = uri.toString(),
                attachmentName = fileName
            )
        )
    }

    fun sendFileToDepartment(meId: String, department: String, uri: Uri, fileName: String, isImage: Boolean) {
        val msgType  = if (isImage) MessageType.IMAGE else MessageType.FILE
        val threadId = "dept_$department"
        val msgId    = UUID.randomUUID().toString()
        messages.add(
            ChatMessage(
                id             = msgId,
                senderId       = meId,
                senderName     = myUserName.ifBlank { meId },
                threadId       = threadId,
                type           = msgType,
                text           = null,
                role           = myRole,
                createdAt      = System.currentTimeMillis(),
                status         = MessageStatus.SENT,
                isOwn          = true,
                attachmentUri  = uri.toString(),
                attachmentName = fileName
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REACTIONS
    // ─────────────────────────────────────────────────────────────────────────
    fun addReaction(messageId: String, emoji: String, userId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val old = messages[index]
        val updatedReactions = old.reactions.toMutableList()
        val existingIndex = updatedReactions.indexOfFirst { it.userId == userId && it.emoji == emoji }
        if (existingIndex >= 0) updatedReactions.removeAt(existingIndex)
        else updatedReactions.add(
            com.ers.emergencyresponseapp.coordination.model.MessageReaction(userId = userId, emoji = emoji)
        )
        messages[index] = old.copy(reactions = updatedReactions)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UNREAD / NOTIFICATION HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    fun markResponderRead(responderId: String) {
        val idx = responders.indexOfFirst { it.id == responderId }
        if (idx >= 0 && responders[idx].unreadCount > 0) {
            responders[idx] = responders[idx].copy(unreadCount = 0)
        }
    }

    fun markDepartmentRead(deptName: String) {
        val idx = departments.indexOfFirst { it.name == deptName }
        if (idx >= 0 && departments[idx].unreadCount > 0) {
            departments[idx] = departments[idx].copy(unreadCount = 0)
        }
    }

    fun clearNotification() {
        latestNotification.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Always produces the same chatId regardless of who is "me" vs "peer" */
    private fun buildChatId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_", prefix = "pm_")
}