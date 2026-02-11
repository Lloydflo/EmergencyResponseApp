package com.ers.emergencyresponseapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Models are in the same package; explicit imports are not required here.
// This ViewModel persists unread counts to SharedPreferences so badges survive restarts.

class CoordinationViewModel(application: Application) : AndroidViewModel(application) {
    // State lists are kept here so they persist across navigation and configuration changes
    val responders = mutableStateListOf<ResponderBrief>()
    val departments = mutableStateListOf<DepartmentInfo>()
    val messages = mutableStateListOf<ChatMessage>()

    val selectedResponder = mutableStateOf<ResponderBrief?>(null)
    val selectedDepartment = mutableStateOf<DepartmentInfo?>(null)
    // Live notification preview shown by the UI when a new incoming message arrives
    val latestNotification = mutableStateOf<String?>(null)

    init {
        // Seed mock responders/departments only if empty
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

        // Load persisted unread counters (if any)
        loadPersistedUnreadCounts()
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

    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        selectedResponder.value = responder
        selectedDepartment.value = null
        markResponderRead(responder.id)
        viewModelScope.launch { loadMockPrivateHistory(meId, responder.id) }
    }

    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        selectedDepartment.value = dept
        selectedResponder.value = null
        markDepartmentRead(dept.name)
        viewModelScope.launch { loadMockDepartmentHistory(dept.name) }
    }

    private suspend fun loadMockPrivateHistory(meId: String, peerId: String) {
        withContext(Dispatchers.Default) {
            messages.clear()
            messages.addAll(
                listOf(
                    ChatMessage("h1", peerId, "Peer", "fire", "Hey, need backup?", System.currentTimeMillis() - 120_000, false),
                    ChatMessage("h2", meId, "You", "fire", "On my way.", System.currentTimeMillis() - 90_000, true)
                )
            )
        }
    }

    private suspend fun loadMockDepartmentHistory(department: String) {
        withContext(Dispatchers.Default) {
            messages.clear()
            messages.addAll(
                listOf(
                    ChatMessage("d1", "2", "Alice", department, "Fire team, status update.", System.currentTimeMillis() - 60_000, false),
                    ChatMessage("d2", "3", "Bob", department, "Medical on scene.", System.currentTimeMillis() - 30_000, false)
                )
            )
        }
    }

    // Mock send functions (simulate network)
    fun sendMockPrivateMessage(meId: String, peer: ResponderBrief, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            messages.add(ChatMessage(null, meId, "You", peer.role, body, now, true))
            delay(700)
            messages.add(ChatMessage(null, peer.id, peer.fullName, peer.role, "Received: $body", System.currentTimeMillis(), false))
        }
    }

    fun sendMockDepartmentMessage(meId: String, department: String, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            messages.add(ChatMessage(null, meId, "You", department, body, now, true))
            delay(500)
            messages.add(ChatMessage(null, "2", "Alice", department, "Acknowledged", System.currentTimeMillis(), false))
        }
    }

    // Handle incoming messages from network/other users
    // If the incoming private message is for the currently-open chat, append it to messages.
    // Otherwise increment unreadCount for that responder so the badge appears.
    @Suppress("unused")
    fun receiveIncomingPrivateMessage(peerId: String, senderName: String, role: String, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // If the user currently has this responder open, append to messages
            if (selectedResponder.value?.id == peerId) {
                messages.add(ChatMessage(null, peerId, senderName, role, body, now, false))
                // still notify UI of incoming message for open chats (so user sees a toast)
                latestNotification.value = "Message from $senderName: ${body.take(80)}"
            } else {
                // increment unread counter for that responder
                val idx = responders.indexOfFirst { it.id == peerId }
                if (idx >= 0) {
                    val r = responders[idx]
                    responders[idx] = r.copy(unreadCount = r.unreadCount + 1, lastMessage = body)
                    savePersistedUnreadCounts()
                    latestNotification.value = "New message from ${r.fullName}: ${body.take(80)}"
                }
            }
        }
    }

    @Suppress("unused")
    fun receiveIncomingDepartmentMessage(deptName: String, senderName: String, role: String, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (selectedDepartment.value?.name == deptName) {
                messages.add(ChatMessage(null, "", senderName, role, body, now, false))
                latestNotification.value = "${senderName} @ $deptName: ${body.take(80)}"
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
    }

    // Persist unread counts to SharedPreferences as JSON objects
    private fun prefs() = getApplication<Application>().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

    private fun savePersistedUnreadCounts() {
        try {
            val rObj = JSONObject()
            responders.forEach { r -> rObj.put(r.id, r.unreadCount) }
            val dObj = JSONObject()
            departments.forEach { d -> dObj.put(d.name, d.unreadCount) }
            prefs().edit().putString("responders_unread", rObj.toString()).putString("departments_unread", dObj.toString()).apply()
        } catch (_: Exception) { /* ignore */ }
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
        } catch (_: Exception) { /* ignore */ }
    }

    fun clearNotification() { latestNotification.value = null }
}
