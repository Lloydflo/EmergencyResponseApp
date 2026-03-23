package com.ers.emergencyresponseapp.coordination.model.viewmodel

import android.app.Application
import android.content.Context
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
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import java.util.UUID


class CoordinationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository()

    val responders   = mutableStateListOf<ResponderBrief>()
    val departments  = mutableStateListOf<DepartmentInfo>()
    val messages     = mutableStateListOf<ChatMessage>()

    val selectedResponder  = mutableStateOf<ResponderBrief?>(null)
    val selectedDepartment = mutableStateOf<DepartmentInfo?>(null)
    val latestNotification = mutableStateOf<String?>(null)
    private val currentThread = mutableStateOf<ChatThread?>(null)

    // ── Typing indicator state ───────────────────────────────────────────────
    // Set to true when a simulated (or real) peer typing event is active.
    // The screen reads this directly as vm.isPeerTyping.
    var isPeerTyping: Boolean by mutableStateOf(false)
        private set

    init {
        if (responders.isEmpty()) {
            responders.addAll(
                listOf(
                    ResponderBrief("2", "alice", "Alice Johnson", "fire",    "online",   "Need backup at station", 1),
                    ResponderBrief("3", "bob",   "Bob Smith",     "medical", "on-duty",  "Patient stable",         0),
                    ResponderBrief("4", "carol", "Carol Davis",   "police",  "online",   "Traffic cleared",        3)
                )
            )
        }

        if (departments.isEmpty()) {
            departments.addAll(
                listOf(
                    DepartmentInfo("fire",    "Fire Department",    "🔥", "Fire team assembled",  2),
                    DepartmentInfo("medical", "Medical Department", "🚑", "Ambulance dispatched", 0),
                    DepartmentInfo("crime",   "Crime Department",   "🚨", "Crime unit active",    0),
                    DepartmentInfo("police",  "Police Department",  "🚓", "Patrol on scene",      1)
                )
            )
        }

        loadPersistedUnreadCounts()
    }

    // ── Thread selection ─────────────────────────────────────────────────────

    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        selectedResponder.value  = responder
        selectedDepartment.value = null
        markResponderRead(responder.id)

        val thread = repository.getOrCreatePrivateThread(
            meId     = meId,
            peerId   = responder.id,
            peerName = responder.fullName
        )
        repository.seedPrivateHistoryIfEmpty(
            threadId = thread.id,
            meId     = meId,
            peerId   = responder.id,
            peerRole = responder.role
        )
        currentThread.value = thread
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        selectedDepartment.value = dept
        selectedResponder.value  = null
        markDepartmentRead(dept.name)

        val thread = repository.getOrCreateDepartmentThread(
            departmentName = dept.name,
            displayName    = dept.displayName
        )
        repository.seedDepartmentHistoryIfEmpty(
            threadId   = thread.id,
            department = dept.name
        )
        currentThread.value = thread
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    @Suppress("unused")
    fun clearCurrentChatHistory() {
        val threadId = currentThread.value?.id ?: return
        repository.clearThread(threadId)
        messages.clear()
        latestNotification.value = null
    }

    // ── Sending ──────────────────────────────────────────────────────────────

    @Suppress("unused")
    fun sendTextMessage(senderId: String, senderName: String, text: String) {
        val thread = currentThread.value ?: return
        val role   = selectedDepartment.value?.name ?: selectedResponder.value?.role ?: "general"

        val message = ChatMessage(
            threadId   = thread.id,
            senderId   = senderId,
            senderName = senderName,
            role       = role,
            type       = MessageType.TEXT,
            text       = text,
            isOwn      = true
        )

        repository.addMessage(message)
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    /**
     * Sends a message from the current user to a specific responder.
     * Simulates sent → delivered → read status progression,
     * then adds a realistic mock reply from the peer.
     *
     * FIX: was incorrectly adding a duplicate of the user's own message
     * as the "reply" — now adds a proper peer reply with peer's senderName and id.
     */
    fun sendMockPrivateMessage(meId: String, peer: ResponderBrief, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // 1. Add the user's outgoing message immediately
            val myMessage = ChatMessage(
                id         = UUID.randomUUID().toString(),
                senderId   = meId,
                senderName = "You",
                threadId   = selectedResponder.value?.id
                    ?: selectedDepartment.value?.name
                    ?: "general",
                type       = MessageType.TEXT,
                text       = body,
                role       = peer.role,
                createdAt  = now,
                status     = MessageStatus.SENT,
                isOwn      = true
            )
            messages.add(myMessage)

            // 2. Simulate delivery receipt
            delay(700)
            updateMessageStatus(myMessage.id, MessageStatus.DELIVERED)

            // 3. Simulate read receipt
            delay(1200)
            updateMessageStatus(myMessage.id, MessageStatus.READ)

            // 4. Simulate peer typing indicator
            delay(400)
            isPeerTyping = true

            // 5. Add a realistic mock reply from the peer (was a duplicate bug before)
            delay(1500)
            isPeerTyping = false

            val replyText = mockReplyFor(body, peer.fullName)
            messages.add(
                ChatMessage(
                    id         = UUID.randomUUID().toString(),
                    senderId   = peer.id,
                    senderName = peer.fullName,
                    threadId   = selectedResponder.value?.id
                        ?: selectedDepartment.value?.name
                        ?: "general",
                    type       = MessageType.TEXT,
                    text       = replyText,
                    role       = peer.role,
                    createdAt  = System.currentTimeMillis(),
                    status     = MessageStatus.SENT,
                    isOwn      = false
                )
            )
        }
    }

    /**
     * Sends a message to a department channel.
     * Simulates status progression, then adds a mock reply from a department member.
     *
     * FIX: same duplicate-own-message bug as sendMockPrivateMessage — fixed.
     */
    fun sendMockDepartmentMessage(meId: String, department: String, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val myMessage = ChatMessage(
                id         = UUID.randomUUID().toString(),
                senderId   = meId,
                senderName = "You",
                threadId   = selectedDepartment.value?.name
                    ?: selectedResponder.value?.id
                    ?: "general",
                type       = MessageType.TEXT,
                text       = body,
                role       = department,
                createdAt  = now,
                status     = MessageStatus.SENT,
                isOwn      = true
            )
            messages.add(myMessage)

            delay(700)
            updateMessageStatus(myMessage.id, MessageStatus.DELIVERED)

            delay(1200)
            updateMessageStatus(myMessage.id, MessageStatus.READ)

            // Simulate a reply from a random department member
            delay(400)
            isPeerTyping = true

            delay(1500)
            isPeerTyping = false

            // Pick a mock responder from the matching department as the reply sender
            val deptMember = responders.firstOrNull { it.role == department }
                ?: ResponderBrief("0", "dispatch", "Dispatch", department, "online", "", 0)

            messages.add(
                ChatMessage(
                    id         = UUID.randomUUID().toString(),
                    senderId   = deptMember.id,
                    senderName = deptMember.fullName,
                    threadId   = selectedDepartment.value?.name
                        ?: selectedResponder.value?.id
                        ?: "general",
                    type       = MessageType.TEXT,
                    text       = mockReplyFor(body, deptMember.fullName),
                    role       = department,
                    createdAt  = System.currentTimeMillis(),
                    status     = MessageStatus.SENT,
                    isOwn      = false
                )
            )
        }
    }

    // ── File / Image sending ─────────────────────────────────────────────────

    /** Sends an image or file to a specific responder. */
    fun sendFileMessage(
        meId: String,
        peer: ResponderBrief,
        uri: Uri,
        fileName: String,
        isImage: Boolean
    ) {
        val msgType  = if (isImage) MessageType.IMAGE else MessageType.FILE
        val threadId = selectedResponder.value?.id ?: selectedDepartment.value?.name ?: "general"
        val msgId    = UUID.randomUUID().toString()
        messages.add(
            ChatMessage(
                id             = msgId,
                senderId       = meId,
                senderName     = "You",
                threadId       = threadId,
                type           = msgType,
                text           = null,
                role           = peer.role,
                createdAt      = System.currentTimeMillis(),
                status         = MessageStatus.SENT,
                isOwn          = true,
                attachmentUri  = uri.toString(),
                attachmentName = fileName
            )
        )
        viewModelScope.launch {
            delay(700)
            updateMessageStatus(msgId, MessageStatus.DELIVERED)
            delay(1200)
            updateMessageStatus(msgId, MessageStatus.READ)
        }
    }

    /** Sends an image or file to a department channel. */
    fun sendFileToDepartment(
        meId: String,
        department: String,
        uri: Uri,
        fileName: String,
        isImage: Boolean
    ) {
        val msgType  = if (isImage) MessageType.IMAGE else MessageType.FILE
        val threadId = selectedDepartment.value?.name ?: selectedResponder.value?.id ?: "general"
        val msgId    = UUID.randomUUID().toString()
        messages.add(
            ChatMessage(
                id             = msgId,
                senderId       = meId,
                senderName     = "You",
                threadId       = threadId,
                type           = msgType,
                text           = null,
                role           = department,
                createdAt      = System.currentTimeMillis(),
                status         = MessageStatus.SENT,
                isOwn          = true,
                attachmentUri  = uri.toString(),
                attachmentName = fileName
            )
        )
        viewModelScope.launch {
            delay(700)
            updateMessageStatus(msgId, MessageStatus.DELIVERED)
            delay(1200)
            updateMessageStatus(msgId, MessageStatus.READ)
        }
    }

    // ── Incoming messages ────────────────────────────────────────────────────

    fun receiveIncomingPrivateMessage(
        meId: String,
        peerId: String,
        senderName: String,
        role: String,
        body: String
    ) {
        val thread = repository.getOrCreatePrivateThread(meId, peerId, senderName)

        repository.addMessage(
            ChatMessage(
                threadId   = thread.id,
                senderId   = peerId,
                senderName = senderName,
                role       = role,
                type       = MessageType.TEXT,
                text       = body,
                isOwn      = false
            )
        )

        if (currentThread.value?.id == thread.id) {
            refreshMessages()
            latestNotification.value = "Message from $senderName: ${body.take(80)}"
        } else {
            val idx = responders.indexOfFirst { it.id == peerId }
            if (idx >= 0) {
                val r = responders[idx]
                responders[idx] = r.copy(unreadCount = r.unreadCount + 1, lastMessage = body)
                savePersistedUnreadCounts()
                latestNotification.value = "New message from ${r.fullName}: ${body.take(80)}"
            }
        }
    }

    @Suppress("unused")
    fun receiveIncomingDepartmentMessage(
        deptName: String,
        senderName: String,
        role: String,
        body: String
    ) {
        val deptInfo    = departments.firstOrNull { it.name == deptName }
        val displayName = deptInfo?.displayName ?: deptName
        val thread      = repository.getOrCreateDepartmentThread(deptName, displayName)

        repository.addMessage(
            ChatMessage(
                threadId   = thread.id,
                senderId   = senderName,
                senderName = senderName,
                role       = role,
                type       = MessageType.TEXT,
                text       = body,
                isOwn      = false
            )
        )

        if (currentThread.value?.id == thread.id) {
            refreshMessages()
            latestNotification.value = "$senderName @ $deptName: ${body.take(80)}"
        } else {
            val idx = departments.indexOfFirst { it.name == deptName }
            if (idx >= 0) {
                val d = departments[idx]
                departments[idx] = d.copy(unreadCount = d.unreadCount + 1, lastMessage = body)
                savePersistedUnreadCounts()
                latestNotification.value = "New message in ${d.displayName}: ${body.take(80)}"
            }
        }
    }

    // ── Reactions ────────────────────────────────────────────────────────────

    fun addReaction(messageId: String, emoji: String, userId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) return

        val old             = messages[index]
        val updatedReactions = old.reactions.toMutableList()
        val existingIndex   = updatedReactions.indexOfFirst { it.userId == userId && it.emoji == emoji }

        if (existingIndex >= 0) {
            updatedReactions.removeAt(existingIndex)   // toggle off
        } else {
            updatedReactions.add(
                com.ers.emergencyresponseapp.coordination.model.MessageReaction(
                    userId = userId,
                    emoji  = emoji
                )
            )
        }
        messages[index] = old.copy(reactions = updatedReactions)
    }

    // ── Unread tracking ──────────────────────────────────────────────────────

    fun markResponderRead(responderId: String) {
        val idx = responders.indexOfFirst { it.id == responderId }
        if (idx >= 0) {
            val r = responders[idx]
            if (r.unreadCount > 0) {
                responders[idx] = r.copy(unreadCount = 0)
                savePersistedUnreadCounts()
            }
        }
    }

    fun markDepartmentRead(deptName: String) {
        val idx = departments.indexOfFirst { it.name == deptName }
        if (idx >= 0) {
            val d = departments[idx]
            if (d.unreadCount > 0) {
                departments[idx] = d.copy(unreadCount = 0)
                savePersistedUnreadCounts()
            }
        }
    }

    fun clearNotification() {
        latestNotification.value = null
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun updateMessageStatus(messageId: String, newStatus: MessageStatus) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) return
        messages[index] = messages[index].copy(status = newStatus)
    }

    private fun refreshMessages() {
        val threadId = currentThread.value?.id ?: return
        messages.clear()
        messages.addAll(repository.getMessages(threadId))
    }

    /**
     * Returns a short, context-aware mock reply so the simulated conversation
     * feels realistic during development/testing.
     */
    private fun mockReplyFor(incomingText: String, @Suppress("UNUSED_PARAMETER") peerName: String): String {
        val lower = incomingText.lowercase()
        return when {
            lower.contains("backup")   -> "Copy that, en route to your position."
            lower.contains("eta")      -> "ETA approximately 5 minutes."
            lower.contains("fire")     -> "Understood. Deploying suppression unit now."
            lower.contains("medical")  -> "Medical team standing by."
            lower.contains("secure")   -> "Area secured. No further movement detected."
            lower.contains("evacuate") -> "Evacuation in progress. Civilians moving out."
            lower.contains("📎")       -> "Attachment received."
            else                       -> "Received. Standing by."
        }
    }

    private fun prefs() = getApplication<Application>().getSharedPreferences(
        "chat_prefs", Context.MODE_PRIVATE
    )

    private fun savePersistedUnreadCounts() {
        try {
            val rObj = JSONObject().also { o -> responders.forEach  { r -> o.put(r.id,   r.unreadCount) } }
            val dObj = JSONObject().also { o -> departments.forEach { d -> o.put(d.name, d.unreadCount) } }
            prefs().edit()
                .putString("responders_unread",  rObj.toString())
                .putString("departments_unread", dObj.toString())
                .apply()
        } catch (_: Exception) { }
    }

    private fun loadPersistedUnreadCounts() {
        try {
            val rObj = JSONObject(prefs().getString("responders_unread",  "{}") ?: "{}")
            val dObj = JSONObject(prefs().getString("departments_unread", "{}") ?: "{}")

            for (i in responders.indices) {
                val r = responders[i]
                if (rObj.has(r.id))   responders[i]  = r.copy(unreadCount = rObj.optInt(r.id,   r.unreadCount))
            }
            for (i in departments.indices) {
                val d = departments[i]
                if (dObj.has(d.name)) departments[i] = d.copy(unreadCount = dObj.optInt(d.name, d.unreadCount))
            }
        } catch (_: Exception) { }
    }
}