package com.ers.emergencyresponseapp.coordination.model.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.DepartmentInfo
import com.ers.emergencyresponseapp.ResponderBrief
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.ChatThread
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.ThreadType
import com.ers.emergencyresponseapp.coordination.model.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject


class CoordinationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository()

    val responders = mutableStateListOf<ResponderBrief>()
    val departments = mutableStateListOf<DepartmentInfo>()
    val messages = mutableStateListOf<ChatMessage>()

    val selectedResponder = mutableStateOf<ResponderBrief?>(null)
    val selectedDepartment = mutableStateOf<DepartmentInfo?>(null)
    val latestNotification = mutableStateOf<String?>(null)
    val currentThread = mutableStateOf<ChatThread?>(null)

    init {
        if (responders.isEmpty()) {
            responders.addAll(
                listOf(
                    ResponderBrief("2", "alice", "Alice Johnson", "fire", "online", "Need backup at station", 1),
                    ResponderBrief("3", "bob", "Bob Smith", "medical", "on-duty", "Patient stable", 0),
                    ResponderBrief("4", "carol", "Carol Davis", "police", "online", "Traffic cleared", 3)
                )
            )
        }

        if (departments.isEmpty()) {
            departments.addAll(
                listOf(
                    DepartmentInfo("fire", "Fire Department", "🔥", "Fire team assembled", 2),
                    DepartmentInfo("medical", "Medical Department", "🚑", "Ambulance dispatched", 0),
                    DepartmentInfo("crime", "Crime Department", "🚨", "Crime unit active", 0),
                    DepartmentInfo("police", "Police Department", "🚓", "Patrol on scene", 1)
                )
            )
        }

        loadPersistedUnreadCounts()
    }

    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        selectedResponder.value = responder
        selectedDepartment.value = null
        markResponderRead(responder.id)

        val thread = repository.getOrCreatePrivateThread(
            meId = meId,
            peerId = responder.id,
            peerName = responder.fullName
        )
        repository.seedPrivateHistoryIfEmpty(
            threadId = thread.id,
            meId = meId,
            peerId = responder.id,
            peerRole = responder.role
        )
        currentThread.value = thread
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        selectedDepartment.value = dept
        selectedResponder.value = null
        markDepartmentRead(dept.name)

        val thread = repository.getOrCreateDepartmentThread(
            departmentName = dept.name,
            displayName = dept.displayName
        )
        repository.seedDepartmentHistoryIfEmpty(
            threadId = thread.id,
            department = dept.name
        )
        currentThread.value = thread
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    fun clearCurrentChatHistory() {
        val threadId = currentThread.value?.id ?: return
        repository.clearThread(threadId)
        messages.clear()
        latestNotification.value = null
    }

    fun sendTextMessage(senderId: String, senderName: String, text: String) {
        val thread = currentThread.value ?: return
        val role = selectedDepartment.value?.name ?: selectedResponder.value?.role ?: "general"

        val message = ChatMessage(
            threadId = thread.id,
            senderId = senderId,
            senderName = senderName,
            role = role,
            type = MessageType.TEXT,
            text = text,
            isOwn = true
        )

        repository.addMessage(message)
        messages.clear()
        messages.addAll(repository.getMessages(thread.id))
    }

    fun sendMockPrivateMessage(meId: String, peer: ResponderBrief, body: String) {
        val thread = repository.getOrCreatePrivateThread(meId, peer.id, peer.fullName)
        currentThread.value = thread

        viewModelScope.launch {
            repository.addMessage(
                ChatMessage(
                    threadId = thread.id,
                    senderId = meId,
                    senderName = "You",
                    role = peer.role,
                    type = MessageType.TEXT,
                    text = body,
                    isOwn = true
                )
            )
            refreshMessages()

            delay(700)

            repository.addMessage(
                ChatMessage(
                    threadId = thread.id,
                    senderId = peer.id,
                    senderName = peer.fullName,
                    role = peer.role,
                    type = MessageType.TEXT,
                    text = "Received: $body",
                    isOwn = false
                )
            )
            refreshMessages()
        }
    }

    fun sendMockDepartmentMessage(meId: String, department: String, body: String) {
        val deptInfo = departments.firstOrNull { it.name == department }
        val displayName = deptInfo?.displayName ?: department
        val thread = repository.getOrCreateDepartmentThread(department, displayName)
        currentThread.value = thread

        viewModelScope.launch {
            repository.addMessage(
                ChatMessage(
                    threadId = thread.id,
                    senderId = meId,
                    senderName = "You",
                    role = department,
                    type = MessageType.TEXT,
                    text = body,
                    isOwn = true
                )
            )
            refreshMessages()

            delay(500)

            repository.addMessage(
                ChatMessage(
                    threadId = thread.id,
                    senderId = "2",
                    senderName = "Alice",
                    role = department,
                    type = MessageType.TEXT,
                    text = "Acknowledged",
                    isOwn = false
                )
            )
            refreshMessages()
        }
    }

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
                threadId = thread.id,
                senderId = peerId,
                senderName = senderName,
                role = role,
                type = MessageType.TEXT,
                text = body,
                isOwn = false
            )
        )

        if (currentThread.value?.id == thread.id) {
            refreshMessages()
            latestNotification.value = "Message from $senderName: ${body.take(80)}"
        } else {
            val idx = responders.indexOfFirst { it.id == peerId }
            if (idx >= 0) {
                val r = responders[idx]
                responders[idx] = r.copy(
                    unreadCount = r.unreadCount + 1,
                    lastMessage = body
                )
                savePersistedUnreadCounts()
                latestNotification.value = "New message from ${r.fullName}: ${body.take(80)}"
            }
        }
    }

    fun receiveIncomingDepartmentMessage(
        deptName: String,
        senderName: String,
        role: String,
        body: String
    ) {
        val deptInfo = departments.firstOrNull { it.name == deptName }
        val displayName = deptInfo?.displayName ?: deptName
        val thread = repository.getOrCreateDepartmentThread(deptName, displayName)

        repository.addMessage(
            ChatMessage(
                threadId = thread.id,
                senderId = senderName,
                senderName = senderName,
                role = role,
                type = MessageType.TEXT,
                text = body,
                isOwn = false
            )
        )

        if (currentThread.value?.id == thread.id) {
            refreshMessages()
            latestNotification.value = "$senderName @ $deptName: ${body.take(80)}"
        } else {
            val idx = departments.indexOfFirst { it.name == deptName }
            if (idx >= 0) {
                val d = departments[idx]
                departments[idx] = d.copy(
                    unreadCount = d.unreadCount + 1,
                    lastMessage = body
                )
                savePersistedUnreadCounts()
                latestNotification.value = "New message in ${d.displayName}: ${body.take(80)}"
            }
        }
    }

    fun addReaction(messageId: String, emoji: String, userId: String) {
        val threadId = currentThread.value?.id ?: return
        val list = repository.getMessages(threadId).toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val item = list[index]
            item.reactions.add(com.ers.emergencyresponseapp.coordination.model.MessageReaction(userId, emoji))
            repository.replaceMessages(threadId, list)
            refreshMessages()
        }
    }

    fun markResponderRead(responderId: String) {
        val idx = responders.indexOfFirst { it.id == responderId }
        if (idx >= 0) {
            val r = responders[idx]
            if (r.unreadCount > 0) responders[idx] = r.copy(unreadCount = 0)
            savePersistedUnreadCounts()
        }
    }

    fun markDepartmentRead(deptName: String) {
        val idx = departments.indexOfFirst { it.name == deptName }
        if (idx >= 0) {
            val d = departments[idx]
            if (d.unreadCount > 0) departments[idx] = d.copy(unreadCount = 0)
            savePersistedUnreadCounts()
        }
    }

    private fun refreshMessages() {
        val threadId = currentThread.value?.id ?: return
        messages.clear()
        messages.addAll(repository.getMessages(threadId))
    }

    private fun prefs() = getApplication<Application>().getSharedPreferences(
        "chat_prefs",
        Context.MODE_PRIVATE
    )

    private fun savePersistedUnreadCounts() {
        try {
            val rObj = JSONObject()
            responders.forEach { r -> rObj.put(r.id, r.unreadCount) }

            val dObj = JSONObject()
            departments.forEach { d -> dObj.put(d.name, d.unreadCount) }

            prefs().edit()
                .putString("responders_unread", rObj.toString())
                .putString("departments_unread", dObj.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadPersistedUnreadCounts() {
        try {
            val rStr = prefs().getString("responders_unread", "{}") ?: "{}"
            val dStr = prefs().getString("departments_unread", "{}") ?: "{}"
            val rObj = JSONObject(rStr)
            val dObj = JSONObject(dStr)

            for (i in responders.indices) {
                val r = responders[i]
                if (rObj.has(r.id)) {
                    responders[i] = r.copy(unreadCount = rObj.optInt(r.id, r.unreadCount))
                }
            }

            for (i in departments.indices) {
                val d = departments[i]
                if (dObj.has(d.name)) {
                    departments[i] = d.copy(unreadCount = dObj.optInt(d.name, d.unreadCount))
                }
            }
        } catch (_: Exception) {
        }
    }

    fun clearNotification() {
        latestNotification.value = null
    }
}