package com.ers.emergencyresponseapp.coordination.model.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ers.emergencyresponseapp.DepartmentInfo
import com.ers.emergencyresponseapp.data.NotificationRepository
import com.ers.emergencyresponseapp.BuildConfig
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.AppState
import com.ers.emergencyresponseapp.AppScreenTracker
import com.ers.emergencyresponseapp.MainActivity
import com.ers.emergencyresponseapp.R
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.ResponderBrief
import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.ChatThread
import com.ers.emergencyresponseapp.coordination.model.MessageStatus
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.ReplyMessageCodec
import com.ers.emergencyresponseapp.coordination.model.ThreadType
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import android.content.Context
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import android.util.Log

class CoordinationViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TYPING_INACTIVITY_TIMEOUT_MS = 2_500L
    }

    // ── Repositories ─────────────────────────────────────────────────────────
    private val notificationRepository = NotificationRepository()
    private val db = FirebaseDatabase.getInstance().reference
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/api/api_app/"
    private val httpClient: OkHttpClient = RetrofitProvider.okHttpClient

    private fun apiUrl(endpoint: String): String =
        apiBaseUrl + endpoint.trimStart('/')

    private fun executeJson(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().trim()
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IOException(
                    "Invalid server response (${response.code})" +
                            body.takeIf { it.isNotBlank() }
                                ?.let { raw -> ": ${raw.take(180)}" }
                                .orEmpty()
                )
            }

            if (!response.isSuccessful) {
                throw IOException(
                    json.optString("message").ifBlank {
                        "Request failed with HTTP ${response.code}"
                    }
                )
            }
            return json
        }
    }

    // ── UI State ──────────────────────────────────────────────────────────────
    val responders   = mutableStateListOf<ResponderBrief>()
    val departments  = mutableStateListOf<DepartmentInfo>()
    val messages     = mutableStateListOf<ChatMessage>()

    val selectedResponder  = mutableStateOf<ResponderBrief?>(null)
    val selectedDepartment = mutableStateOf<DepartmentInfo?>(null)
    val latestNotification = mutableStateOf<String?>(null)
    private val currentThread = mutableStateOf<ChatThread?>(null)
    val activeThreadId: String? get() = currentThread.value?.id

    var isPeerTyping: Boolean by mutableStateOf(false)
        private set

    // ── Internal ──────────────────────────────────────────────────────────────
    private var myUserId   = ""
    private var myUserName = ""
    private var myRole     = ""

    // Active Firebase listeners so we can remove them on disconnect
    private var messagesListener: ValueEventListener? = null
    private var threadsListener: ValueEventListener? = null
    private var messagesListenerPath: String? = null
    private var respondersListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null
    private var typingListenerPath: String? = null
    private var typingStopJob: kotlinx.coroutines.Job? = null
    private var typingWriteThreadId: String? = null
    private var isLocalTyping = false

    private var activeGroupId: Int? = null
    private var groupPollingJob: kotlinx.coroutines.Job? = null
    private var lastMarkedGroupRead: Pair<Int, Int>? = null
    private val groupMessagesRequestId = AtomicInteger(0)
    private val hasPrimedThread = mutableSetOf<String>()
    private val lastNotifiedMessageIdByThread = mutableMapOf<String, String>()

    private data class DirectThreadPreview(
        val lastMessage: String,
        val lastMessageTime: Long
    )

    // /threads can arrive before /users (or vice versa). Keep the complete
    // thread snapshot by thread ID and merge it into responders deterministically.
    // This removes the previous asynchronous get() race that returned null.
    private val directThreadPreviewByThreadId = mutableMapOf<String, DirectThreadPreview>()
    private val privateUnreadByThreadId = mutableMapOf<String, Int>()


    // ─────────────────────────────────────────────────────────────────────────
    //  CONNECT — called once when CoordinationPortalScreen opens
    // ─────────────────────────────────────────────────────────────────────────
    fun connectRealtime(userId: String, userName: String, userRole: String) {
        clearTyping()
        stopListeningToTyping()
        myUserId   = userId
        myUserName = userName
        myRole     = userRole

        // Presence is process-scoped and is managed by ResponderPresenceManager.
        // Opening or leaving one Coordination screen must never change whether
        // dispatch can assign this responder.

        // Load the real list of responders from Firebase /users
        loadRespondersFromFirebase()
        listenToThreads()

        // Load static department list (departments are role-based, not user accounts)
        loadInteragencyGroups(userId)
    }

    private fun getRealFileName(uri: Uri, fallback: String): String {
        val context = getApplication<Application>()

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    fallback
                }
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private fun DataSnapshot.readLongChild(name: String): Long {
        return when (val value = child(name).value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun sortRespondersLatestFirst(
        source: List<ResponderBrief>
    ): List<ResponderBrief> {
        return source.sortedWith(
            compareByDescending<ResponderBrief> {
                if (it.lastMessageTime > 0L || it.lastMessage.isNotBlank()) 1 else 0
            }
                .thenByDescending { it.lastMessageTime }
                .thenByDescending { it.unreadCount }
                .thenBy { it.fullName.lowercase() }
        )
    }

    private fun applyThreadPreviewsToResponders() {
        if (responders.isEmpty()) return

        val merged = responders.map { responder ->
            val threadId = buildChatId(myUserId, responder.id)
            val preview = directThreadPreviewByThreadId[threadId]

            if (preview != null) {
                responder.copy(
                    lastMessage = preview.lastMessage,
                    lastMessageTime = preview.lastMessageTime
                )
            } else {
                // The /threads snapshot is authoritative. A missing thread means
                // this responder belongs in the Responders tab, not Recent Chats.
                responder.copy(
                    lastMessage = "",
                    lastMessageTime = 0L
                )
            }
        }

        responders.clear()
        responders.addAll(sortRespondersLatestFirst(merged))
    }

    private fun updateLocalThreadPreview(
        threadId: String,
        lastMessage: String,
        lastMessageTime: Long
    ) {
        if (!threadId.startsWith("pm_")) return

        directThreadPreviewByThreadId[threadId] = DirectThreadPreview(
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime
        )

        // Update only the affected row while the full /threads snapshot is
        // still loading; do not temporarily erase other conversation previews.
        val updated = responders.map { responder ->
            if (buildChatId(myUserId, responder.id) == threadId) {
                responder.copy(
                    lastMessage = lastMessage,
                    lastMessageTime = lastMessageTime
                )
            } else {
                responder
            }
        }

        responders.clear()
        responders.addAll(sortRespondersLatestFirst(updated))
    }

    private fun incrementPrivateUnread(recipientId: String, threadId: String) {
        if (recipientId.isBlank() || threadId.isBlank()) return

        // Keep the per-user unread value inside the existing thread metadata.
        // This works with the currently deployed /threads rules and avoids a
        // second Firebase root that the existing project rules do not expose.
        db.child("threads")
            .child(threadId)
            .child("unreadCounts")
            .child(recipientId)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = when (val value = currentData.value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull() ?: 0
                        else -> 0
                    }
                    currentData.value = (current + 1).coerceAtMost(999)
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null || !committed) {
                        Log.w(
                            "CoordinationVM",
                            "Message sent but unread counter was not updated",
                            error?.toException()
                        )
                    }
                }
            })
    }

    private fun resetPrivateUnread(threadId: String) {
        if (myUserId.isBlank() || threadId.isBlank()) return
        db.child("threads")
            .child(threadId)
            .child("unreadCounts")
            .child(myUserId)
            .setValue(0)
            .addOnFailureListener { error ->
                Log.w("CoordinationVM", "Failed to reset unread counter", error)
            }
    }

    private fun restoreLocalThreadPreview(
        threadId: String,
        previous: DirectThreadPreview?
    ) {
        if (previous == null) {
            directThreadPreviewByThreadId.remove(threadId)
        } else {
            directThreadPreviewByThreadId[threadId] = previous
        }
        applyThreadPreviewsToResponders()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD REAL RESPONDERS FROM FIREBASE /users
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadRespondersFromFirebase() {
        respondersListener?.let { db.child("users").removeEventListener(it) }

        respondersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<ResponderBrief>()

                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == myUserId) continue

                    val fullName = child.child("fullName")
                        .getValue(String::class.java)
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Unknown" }
                    val department = child.child("department")
                        .getValue(String::class.java)
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "general" }
                    val onlineFlag = when (val value = child.child("isOnline").value) {
                        is Boolean -> value
                        is String -> value.toBoolean()
                        is Number -> value.toInt() != 0
                        else -> false
                    }
                    val onlineUntil = when (val value = child.child("onlineUntil").value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val isOnline = onlineFlag && (
                        onlineUntil <= 0L || onlineUntil > System.currentTimeMillis()
                    )

                    val existing = responders.firstOrNull { it.id == uid }
                    val preview = directThreadPreviewByThreadId[
                        buildChatId(myUserId, uid)
                    ]

                    loaded.add(
                        ResponderBrief(
                            id = uid,
                            username = child.child("email")
                                .getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                                ?: uid,
                            fullName = fullName,
                            role = department,
                            status = if (isOnline) "online" else "offline",
                            lastMessage = preview?.lastMessage
                                ?: existing?.lastMessage
                                .orEmpty(),
                            lastMessageTime = preview?.lastMessageTime
                                ?: existing?.lastMessageTime
                                ?: 0L,
                            unreadCount = privateUnreadByThreadId[
                                buildChatId(myUserId, uid)
                            ] ?: existing?.unreadCount ?: 0
                        )
                    )
                }

                responders.clear()
                responders.addAll(sortRespondersLatestFirst(loaded))
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("users").addValueEventListener(respondersListener!!)
    }

    private fun listenToThreads() {
        threadsListener?.let { db.child("threads").removeEventListener(it) }

        threadsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latestSnapshot = mutableMapOf<String, DirectThreadPreview>()
                val latestUnread = mutableMapOf<String, Int>()

                for (thread in snapshot.children) {
                    val threadId = thread.key ?: continue
                    if (!threadId.startsWith("pm_")) continue

                    latestSnapshot[threadId] = DirectThreadPreview(
                        lastMessage = ReplyMessageCodec.displayBody(
                            thread.child("lastMessage")
                                .getValue(String::class.java)
                        ),
                        lastMessageTime = thread.readLongChild("lastMessageTime")
                    )

                    val unreadValue = thread.child("unreadCounts").child(myUserId).value
                    latestUnread[threadId] = when (unreadValue) {
                        is Number -> unreadValue.toInt()
                        is String -> unreadValue.toIntOrNull() ?: 0
                        else -> 0
                    }.coerceAtLeast(0)
                }

                directThreadPreviewByThreadId.clear()
                directThreadPreviewByThreadId.putAll(latestSnapshot)
                privateUnreadByThreadId.clear()
                privateUnreadByThreadId.putAll(latestUnread)

                if (responders.isNotEmpty()) {
                    val withUnread = responders.map { responder ->
                        val threadId = buildChatId(myUserId, responder.id)
                        responder.copy(unreadCount = latestUnread[threadId] ?: 0)
                    }
                    responders.clear()
                    responders.addAll(sortRespondersLatestFirst(withUnread))
                }
                applyThreadPreviewsToResponders()
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("threads").addValueEventListener(threadsListener!!)
    }

    private fun loadInteragencyGroups(
        userId: String,
        onFinished: ((String?) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(apiUrl("get-interagency-groups.php") + "?user_id=$userId")
                    .get()
                    .build()

                val json = executeJson(request)

                if (!json.optBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        val message = json.optString("message", "Failed to load groups")
                        latestNotification.value = message
                        onFinished?.invoke(message)
                    }
                    return@launch
                }

                val groupsArray = json.optJSONArray("groups")

                val loadedGroups = mutableListOf<DepartmentInfo>()

                for (i in 0 until (groupsArray?.length() ?: 0)) {
                    val item = groupsArray!!.getJSONObject(i)

                    val groupId = item.optInt("id")
                    val name = groupId.toString()
                    val displayName = item.optString("displayName", item.optString("name"))

                    val icon = when {
                        displayName.contains("fire", ignoreCase = true) -> "🔥"
                        displayName.contains("medical", ignoreCase = true) ||
                                displayName.contains("ambulance", ignoreCase = true) -> "🚑"
                        displayName.contains("police", ignoreCase = true) -> "🚓"
                        else -> "👥"
                    }

                    val isMember = item.optBoolean("isMember")
                    val requestPending = item.optBoolean("requestPending")
                    val latestMessage = ReplyMessageCodec
                        .displayBody(item.optString("lastMessage"))
                        .trim()
                    val latestMessageTime = item.optLong("lastMessageTime", 0L)

                    loadedGroups.add(
                        DepartmentInfo(
                            name = groupId.toString(),
                            displayName = displayName,
                            emoji = icon,
                            isMember = isMember,
                            requestPending = requestPending,
                            lastMessage = when {
                                isMember && latestMessage.isNotBlank() -> latestMessage
                                isMember -> "Tap to open group chat"
                                requestPending -> "Request pending approval"
                                else -> "Request access to join"
                            },
                            lastMessageTime = if (isMember) latestMessageTime else 0L,
                            unreadCount = if (isMember) item.optInt("unreadCount", 0) else 0
                        )
                    )
                }

                launch(Dispatchers.Main) {
                    departments.clear()
                    departments.addAll(
                        loadedGroups.sortedWith(
                            compareByDescending<DepartmentInfo> { it.unreadCount }
                                .thenByDescending { it.lastMessageTime }
                                .thenBy { it.displayName.lowercase() }
                        )
                    )
                    onFinished?.invoke(null)
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    val message = "Failed to load groups: ${e.message}"
                    latestNotification.value = message
                    onFinished?.invoke(message)
                }
            }
        }
    }
    fun refreshInbox(
        userId: String,
        onFinished: (String?) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            onFinished("Responder session is unavailable")
            return
        }
        loadRespondersFromFirebase()
        listenToThreads()
        loadInteragencyGroups(userId, onFinished)
    }

    fun requestGroupAccess(
        groupId: Int,
        userId: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("user_id", userId.toString())
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("request-group-access.php"))
                    .post(formBody)
                    .build()

                val json = executeJson(request)

                launch(Dispatchers.Main) {
                    latestNotification.value =
                        json.optString("message", "Request submitted")
                }

                if (json.optBoolean("success")) {
                    loadInteragencyGroups(userId.toString())
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    latestNotification.value = "Failed to request access: ${e.message}"
                }
            }
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    //  DISCONNECT
    // ─────────────────────────────────────────────────────────────────────────
    fun disconnectRealtime() {
        // Remove only screen-scoped listeners. Presence belongs to the signed-in
        // app session and remains online during the background grace period.
        // Remove Firebase listeners to avoid memory leaks
        clearTyping()
        stopListeningToTyping()
        respondersListener?.let { db.child("users").removeEventListener(it) }
        threadsListener?.let { db.child("threads").removeEventListener(it) }
        stopListeningToMessages()
        groupPollingJob?.cancel()
        groupPollingJob = null
        activeGroupId = null
        lastMarkedGroupRead = null
        directThreadPreviewByThreadId.clear()
        privateUnreadByThreadId.clear()
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT RESPONDER — open a real Firebase chat thread
    // ─────────────────────────────────────────────────────────────────────────
    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        // Leaving department chat: stop group polling immediately to avoid mixed messages.
        clearTyping()
        stopListeningToTyping()
        activeGroupId = null
        lastMarkedGroupRead = null
        groupPollingJob?.cancel()
        groupPollingJob = null

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
        listenToTyping(thread.id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT DEPARTMENT — open a department group channel
    // ─────────────────────────────────────────────────────────────────────────
    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        // Leaving private chat: remove old Firebase private listener.
        clearTyping()
        stopListeningToTyping()
        stopListeningToMessages()

        selectedDepartment.value = dept
        selectedResponder.value = null
        markDepartmentRead(dept.name)

        val groupId = dept.name.toIntOrNull() ?: return
        lastMarkedGroupRead = null

        currentThread.value = ChatThread(
            id = "group_$groupId",
            type = ThreadType.DEPARTMENT,
            name = dept.displayName,
            participants = listOf(dept.name)
        )

        messages.clear()
        loadInteragencyGroupMessages(groupId)
        activeGroupId = groupId
        startGroupPolling(groupId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PRIVATE CHAT TYPING STATE
    // ────────────────────────────────────────────────────────────────────────────
    fun onComposerTextChanged(hasText: Boolean) {
        val thread = currentThread.value
        if (!hasText || thread?.type != ThreadType.PRIVATE || myUserId.isBlank()) {
            clearTyping()
            return
        }

        typingStopJob?.cancel()

        if (!isLocalTyping || typingWriteThreadId != thread.id) {
            clearTypingEntry(typingWriteThreadId)
            typingWriteThreadId = thread.id
            isLocalTyping = true

            val typingRef = db.child("threads")
                .child(thread.id)
                .child("typing")
                .child(myUserId)

            typingRef.onDisconnect().removeValue()
                .addOnFailureListener { error ->
                    Log.w("CoordinationVM", "Failed to register typing cleanup", error)
                }
            typingRef.updateChildren(
                mapOf(
                    "name" to myUserName.ifBlank { myUserId },
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            ).addOnFailureListener { error ->
                if (typingWriteThreadId == thread.id) {
                    typingWriteThreadId = null
                    isLocalTyping = false
                }
                Log.w("CoordinationVM", "Failed to publish typing state", error)
            }
        }

        typingStopJob = viewModelScope.launch {
            kotlinx.coroutines.delay(TYPING_INACTIVITY_TIMEOUT_MS)
            clearTyping()
        }
    }

    fun clearTyping() {
        typingStopJob?.cancel()
        typingStopJob = null

        val threadId = typingWriteThreadId
        typingWriteThreadId = null
        isLocalTyping = false
        clearTypingEntry(threadId)
    }

    private fun clearTypingEntry(threadId: String?) {
        if (threadId.isNullOrBlank() || myUserId.isBlank()) return

        db.child("threads")
            .child(threadId)
            .child("typing")
            .child(myUserId)
            .removeValue()
            .addOnFailureListener { error ->
                Log.w("CoordinationVM", "Failed to clear typing state", error)
            }
    }

    private fun listenToTyping(threadId: String) {
        stopListeningToTyping()
        if (currentThread.value?.type != ThreadType.PRIVATE || myUserId.isBlank()) return

        typingListenerPath = "threads/$threadId/typing"
        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (currentThread.value?.id != threadId) return
                isPeerTyping = snapshot.children.any { typingEntry ->
                    typingEntry.key != myUserId
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isPeerTyping = false
                Log.w("CoordinationVM", "Typing listener was cancelled", error.toException())
            }
        }

        db.child("threads")
            .child(threadId)
            .child("typing")
            .addValueEventListener(typingListener!!)
    }

    private fun stopListeningToTyping() {
        val path = typingListenerPath
        val listener = typingListener
        if (path != null && listener != null) {
            db.child(path).removeEventListener(listener)
        }
        typingListener = null
        typingListenerPath = null
        isPeerTyping = false
    }

    private fun loadInteragencyGroupMessages(groupId: Int) {
        val requestId = groupMessagesRequestId.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(apiUrl("get-interagency-group-messages.php") + "?group_id=$groupId&user_id=$myUserId")
                    .get()
                    .build()
                val json = executeJson(request)

                if (!json.optBoolean("success")) return@launch

                val arr = json.optJSONArray("messages")
                val loaded = mutableListOf<ChatMessage>()

                for (i in 0 until (arr?.length() ?: 0)) {
                    val item = arr!!.getJSONObject(i)
                    val typeText = item.optString("type", "TEXT")

                    val messageType = when (typeText.uppercase()) {
                        "IMAGE" -> MessageType.IMAGE
                        "FILE" -> MessageType.FILE
                        "AUDIO", "VOICE" -> MessageType.AUDIO
                        else -> MessageType.TEXT
                    }



                    val senderId = item.optString("senderId")
                    val senderName = item.optString("senderName")
                    val statusText = item.optString("status", "sent")
                    val messageStatus = when (statusText.lowercase()) {
                        "read" -> MessageStatus.READ
                        "delivered" -> MessageStatus.DELIVERED
                        else -> MessageStatus.SENT
                    }
                    loaded.add(
                        ChatMessage(
                            id = item.optString("id"),
                            threadId = "group_$groupId",
                            senderId = senderId,
                            senderName = senderName,
                            role = item.optString("role"),
                            type = messageType,
                            text = item.optString("text"),
                            createdAt = item.optLong("createdAt"),
                            status = messageStatus,   // <-- was hardcoded MessageStatus.SENT
                            isOwn = senderId == myUserId || senderName.equals(myUserName, ignoreCase = true),
                            attachmentUri = item.optString("attachmentUri")
                                .takeIf { it.isNotBlank() && it != "null" },
                            attachmentName = item.optString("attachmentName")
                                .takeIf { it.isNotBlank() && it != "null" },
                            attachmentMimeType = item.optString("attachmentMimeType")
                                .takeIf { it.isNotBlank() && it != "null" },
                            attachmentSize = item.optLong("attachmentSize", 0L)
                                .coerceAtLeast(0L),
                            audioDurationMs = item.optLong("audioDurationMs", 0L)
                                .coerceAtLeast(0L)
                        )
                    )
                }

                launch(Dispatchers.Main) {
                    // Ignore stale results from older, out-of-order requests.
                    if (requestId == groupMessagesRequestId.get() && currentThread.value?.id == "group_$groupId") {
                        // Keep optimistic (locally sent, not-yet-confirmed) bubbles alive if the
                        // server hasn't caught up to them yet. Without this, a reload/poll that
                        // races ahead of DB read-after-write wipes the bubble you just sent,
                        // and it won't reappear until the next full reload catches it.
                        val stillPendingLocal = messages.filter { local ->
                            local.id.startsWith("local_") &&
                                    loaded.none {
                                        it.senderId == local.senderId &&
                                                it.text == local.text &&
                                                kotlin.math.abs(it.createdAt - local.createdAt) < 15000
                                    }
                        }
                        messages.clear()
                        messages.addAll((loaded + stillPendingLocal).sortedBy { it.createdAt })

                        // We're actively viewing this thread — mark the latest message as read
                        if (
                            AppState.isForeground &&
                            AppScreenTracker.currentScreen == "COORDINATION" &&
                            AppScreenTracker.currentThreadId == "group_$groupId"
                        ) {
                            loaded.maxOfOrNull { it.id.toIntOrNull() ?: 0 }?.let { maxId ->
                                if (maxId > 0) markGroupThreadRead(groupId, maxId)
                            }
                        }
                    }
                }

                val threadId = "group_$groupId"
                maybeNotifyIncoming(threadId, loaded)

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    if (requestId == groupMessagesRequestId.get() && currentThread.value?.id == "group_$groupId") {
                        latestNotification.value = "Failed to load group messages"
                    }
                }
            }
        }
    }

    private fun startGroupPolling(groupId: Int) {
        groupPollingJob?.cancel()

        groupPollingJob = viewModelScope.launch {
            while (activeGroupId == groupId) {
                loadInteragencyGroupMessages(groupId)
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private fun markGroupThreadRead(groupId: Int, lastReadId: Int) {
        if (lastReadId <= 0 || myUserId.isBlank()) return
        val marker = groupId to lastReadId
        if (lastMarkedGroupRead == marker) return
        lastMarkedGroupRead = marker
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("user_id", myUserId)
                    .add("last_read_id", lastReadId.toString())
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("mark-group-read.php"))
                    .post(formBody)
                    .build()

                executeJson(request)
            } catch (e: Exception) {
                if (lastMarkedGroupRead == marker) lastMarkedGroupRead = null
                e.printStackTrace()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FIREBASE REAL-TIME MESSAGE LISTENER
    // ─────────────────────────────────────────────────────────────────────────
    private fun listenToMessages(threadId: String) {
        stopListeningToMessages()

        messagesListenerPath = "messages/$threadId"

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Ignore stale updates from previous private thread.
                if (currentThread.value?.id != threadId) return

                val loaded = mutableListOf<ChatMessage>()

                for (child in snapshot.children) {

                    val senderId   = child.child("senderId").getValue(String::class.java) ?: continue
                    val senderName = child.child("senderName").getValue(String::class.java) ?: "Unknown"
                    val text = child.child("text").getValue(String::class.java)
                    val role = child.child("role").getValue(String::class.java) ?: ""
                    val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                    val msgId = child.key ?: UUID.randomUUID().toString()

                    val typeText = child.child("type").getValue(String::class.java) ?: "TEXT"
                    val msgType = when (typeText.uppercase()) {
                        "IMAGE" -> MessageType.IMAGE
                        "FILE" -> MessageType.FILE
                        "AUDIO", "VOICE" -> MessageType.AUDIO
                        else -> MessageType.TEXT
                    }

                    val attachmentUri = child.child("attachmentUri").getValue(String::class.java)
                    val attachmentName = child.child("attachmentName").getValue(String::class.java)
                    val attachmentMimeType = child.child("attachmentMimeType")
                        .getValue(String::class.java)
                    val attachmentSize = child.readLongChild("attachmentSize")
                    val audioDurationMs = child.readLongChild("audioDurationMs")
                    val statusText = child.child("status").getValue(String::class.java) ?: "sent"

                    val messageStatus = when (statusText.lowercase()) {
                        "delivered" -> MessageStatus.DELIVERED
                        "read" -> MessageStatus.READ
                        else -> MessageStatus.SENT
                    }

                    if (senderId != myUserId && statusText == "sent") {
                        child.ref.child("status").setValue("delivered")
                    }
                    val reactions = child.child("reactions").children.mapNotNull { reaction ->
                        val userId = reaction.key ?: return@mapNotNull null
                        val emoji = reaction.getValue(String::class.java) ?: return@mapNotNull null

                        com.ers.emergencyresponseapp.coordination.model.MessageReaction(
                            userId = userId,
                            emoji = emoji
                        )
                    }

                    loaded.add(
                        ChatMessage(
                            id         = msgId,
                            threadId   = threadId,
                            senderId   = senderId,
                            senderName = senderName,
                            role       = role,
                            type = msgType,
                            text = text,
                            createdAt = createdAt,
                            status = messageStatus,
                            isOwn = senderId == myUserId,
                            attachmentUri = attachmentUri,
                            attachmentName = attachmentName,
                            attachmentMimeType = attachmentMimeType,
                            attachmentSize = attachmentSize,
                            audioDurationMs = audioDurationMs,
                            reactions = reactions
                        )
                    )
                }

                messages.clear()
                messages.addAll(loaded.sortedBy { it.createdAt })


                maybeNotifyIncoming(threadId, loaded)
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("messages").child(threadId)
            .addValueEventListener(messagesListener!!)
    }

    fun markMessagesAsRead(myId: String, peerId: String?) {
        if (peerId == null) return

        val threadId = buildChatId(myId, peerId)
        resetPrivateUnread(threadId)

        db.child("messages")
            .child(threadId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { msg ->
                    val senderId = msg.child("senderId")
                        .getValue(String::class.java)

                    if (senderId != myId) {
                        msg.ref.child("status").setValue("read")
                    }
                }
            }
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

    private fun maybeNotifyIncoming(threadId: String, loadedMessages: List<ChatMessage>) {
        val latestIncoming = loadedMessages
            .sortedBy { it.createdAt }
            .lastOrNull { !it.isOwn }
            ?: return

        // Prime on first load so old history does not trigger notifications.
        if (!hasPrimedThread.contains(threadId)) {
            hasPrimedThread.add(threadId)
            lastNotifiedMessageIdByThread[threadId] = latestIncoming.id
            return
        }

        if (lastNotifiedMessageIdByThread[threadId] == latestIncoming.id) return

        val viewingThisThread =
            AppState.isForeground &&
                    AppScreenTracker.currentScreen == "COORDINATION" &&
                    currentThread.value?.id == threadId

        if (viewingThisThread) {
            lastNotifiedMessageIdByThread[threadId] = latestIncoming.id
            return
        }

        lastNotifiedMessageIdByThread[threadId] = latestIncoming.id
        showMessageNotification(threadId, latestIncoming)
    }

    private fun showMessageNotification(threadId: String, message: ChatMessage) {
        val context = getApplication<Application>()
        val content = when {
            message.text.orEmpty().contains("ERS_COORDINATION_TIP_") -> "Incident tip shared"
            message.type == MessageType.AUDIO -> "Sent a voice message"
            message.type == MessageType.IMAGE -> "Sent an image"
            message.type == MessageType.FILE -> "Sent a file"
            !message.text.isNullOrBlank() -> ReplyMessageCodec.displayBody(message.text).trim()
            else -> "New message"
        }

        if (threadId.startsWith("group_")) {
            val groupId = threadId.removePrefix("group_").toIntOrNull() ?: return
            val groupName = departments.firstOrNull { it.name == groupId.toString() }
                ?.displayName
                .orEmpty()
            AppNotificationManager.showDepartmentChat(
                context = context,
                eventKey = "department:$groupId:${message.id}",
                groupId = groupId,
                groupName = groupName,
                senderName = message.senderName,
                body = content
            )
        } else {
            AppNotificationManager.showPrivateChat(
                context = context,
                eventKey = "private:$threadId:${message.id}",
                peerId = message.senderId,
                threadId = threadId,
                senderName = message.senderName,
                body = content
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND MESSAGE — writes to Firebase, listener picks it up on ALL devices
    // ─────────────────────────────────────────────────────────────────────────
    fun sendPrivateMessage(meId: String, peer: ResponderBrief, body: String) {
        val threadId = buildChatId(meId, peer.id)
        pushMessageToFirebase(
            threadId   = threadId,
            senderId   = meId,
            senderName = myUserName.ifBlank { meId },
            role       = myRole,
            recipientId = peer.id,
            text       = body
        )
    }

    fun sendDepartmentMessage(meId: String, department: String, body: String) {
        val groupId = department.toIntOrNull() ?: return
        val threadId = "group_$groupId"

        // Optimistic local append so it appears instantly, matching private chat UX.
        val optimisticId = "local_${System.currentTimeMillis()}"
        val optimisticMessage = ChatMessage(
            id = optimisticId,
            threadId = threadId,
            senderId = meId,
            senderName = myUserName.ifBlank { meId },
            role = myRole,
            type = MessageType.TEXT,
            text = body,
            createdAt = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            isOwn = true
        )
        if (currentThread.value?.id == threadId) {
            messages.add(optimisticMessage)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("sender_user_id", meId)
                    .add("text", body)
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("send-interagency-group-message.php"))
                    .post(formBody)
                    .build()

                val json = executeJson(request)

                if (json.optBoolean("success")) {
                    loadInteragencyGroupMessages(groupId)   // reconciles/replaces optimistic entry with the real one
                } else {
                    launch(Dispatchers.Main) {
                        // Sending failed — remove the optimistic bubble so it doesn't look sent when it wasn't.
                        if (currentThread.value?.id == threadId) {
                            messages.removeAll { it.id == optimisticId }
                        }
                        latestNotification.value = json.optString("message", "Failed to send message")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    if (currentThread.value?.id == threadId) {
                        messages.removeAll { it.id == optimisticId }
                    }
                    latestNotification.value = "Failed to send message"
                }
            }
        }
    }

    private fun dispatchPrivatePush(
        senderId: String,
        recipientId: String,
        threadId: String,
        messageId: String,
        senderName: String,
        messageType: String,
        preview: String
    ) {
        val sender = senderId.toIntOrNull() ?: return
        val recipient = recipientId.toIntOrNull() ?: return
        val safePreview = when {
            preview.contains("ERS_COORDINATION_TIP_") -> "Incident tip shared"
            messageType.equals("image", ignoreCase = true) -> "Sent an image"
            messageType.equals("file", ignoreCase = true) -> "Sent a file: ${preview.take(100)}"
            messageType.equals("audio", ignoreCase = true) ||
                    messageType.equals("voice", ignoreCase = true) -> "Sent a voice message"
            else -> preview.trim().take(240)
        }

        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.notifyPrivateMessage(
                senderId = sender,
                recipientId = recipient,
                threadId = threadId,
                messageId = messageId,
                senderName = senderName,
                messageType = messageType,
                preview = safePreview
            ).onFailure { error ->
                Log.w("CoordinationVM", "Private push notification was not delivered", error)
            }
        }
    }

    private fun pushMessageToFirebase(
        threadId: String,
        senderId: String,
        senderName: String,
        role: String,
        recipientId: String,
        text: String
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val conversationPreview = ReplyMessageCodec.displayBody(cleanText).trim()

        val now = System.currentTimeMillis()
        val messageId = db.child("messages").child(threadId).push().key
        if (messageId.isNullOrBlank()) {
            latestNotification.value = "Unable to create message. Please try again."
            return
        }

        val data = mapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "role" to role,
            "type" to "TEXT",
            "text" to cleanText,
            "createdAt" to now,
            "status" to "sent"
        )

        val previousPreview = directThreadPreviewByThreadId[threadId]
        updateLocalThreadPreview(threadId, conversationPreview, now)

        val updates: Map<String, Any?> = mapOf(
            "messages/$threadId/$messageId" to data,
            "threads/$threadId/lastMessage" to conversationPreview,
            "threads/$threadId/lastMessageTime" to now,
            "threads/$threadId/lastSenderId" to senderId,
            "threads/$threadId/lastSenderName" to senderName,
            "threads/$threadId/participants/$senderId" to true,
            "threads/$threadId/participants/$recipientId" to true
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                incrementPrivateUnread(recipientId, threadId)
                dispatchPrivatePush(
                    senderId = senderId,
                    recipientId = recipientId,
                    threadId = threadId,
                    messageId = messageId,
                    senderName = senderName,
                    messageType = "text",
                    preview = conversationPreview
                )
            }
            .addOnFailureListener { error ->
                restoreLocalThreadPreview(threadId, previousPreview)
                latestNotification.value =
                    "Message was not sent: ${error.localizedMessage ?: "Firebase write failed"}"
            }
    }

    private fun safeAttachmentName(rawName: String): String {
        val leaf = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
            .take(140)
        return leaf.ifBlank { "attachment" }
    }

    private fun uploadFileToServer(
        uri: Uri,
        fileName: String,
        onSuccess: (
            fileUrl: String,
            uploadedName: String,
            fileSize: Long,
            mimeType: String
        ) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val context = getApplication<Application>()
                val safeName = safeAttachmentName(fileName)
                val extension = safeName.substringAfterLast('.', "")
                    .takeIf { it.isNotBlank() && it.length <= 12 }
                val uploadFile = File.createTempFile(
                    "chat_upload_",
                    extension?.let { ".$it" } ?: ".bin",
                    context.cacheDir
                )
                tempFile = uploadFile

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(uploadFile).use { output -> input.copyTo(output) }
                } ?: throw IOException("Cannot open the selected file")

                val size = uploadFile.length()
                if (size <= 0L) throw IOException("The selected file is empty")
                if (size > 25L * 1024L * 1024L) {
                    throw IOException("The selected file exceeds the 25 MB limit")
                }

                val mediaType = context.contentResolver.getType(uri)
                    ?.toMediaTypeOrNull()
                    ?: "application/octet-stream".toMediaTypeOrNull()
                val requestBody = uploadFile.asRequestBody(mediaType)
                val uploaderUserId = myUserId.toIntOrNull()
                    ?: throw IllegalStateException("Responder session is unavailable")

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uploader_user_id", uploaderUserId.toString())
                    .addFormDataPart("file", safeName, requestBody)
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("upload_chat_file.php"))
                    .post(multipartBody)
                    .build()

                val json = executeJson(request)
                val uploadedUrl = json.optString("file_url").trim()
                val uploadedName = json.optString("file_name", safeName).trim().ifBlank { safeName }
                val uploadedSize = json.optLong("file_size", size).takeIf { it > 0L } ?: size
                val uploadedMime = json.optString("mime_type", mediaType.toString())
                    .trim()
                    .ifBlank { mediaType.toString() }

                if (!json.optBoolean("success") || uploadedUrl.isBlank()) {
                    throw IOException(json.optString("message", "Upload failed"))
                }

                withContext(Dispatchers.Main) {
                    onSuccess(uploadedUrl, uploadedName, uploadedSize, uploadedMime)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Upload error")
                }
            } finally {
                tempFile?.delete()
            }
        }
    }

    /** Uploads an app-owned recording and always removes the cache file. */
    private fun uploadVoiceFileToServer(
        recordingFile: File,
        declaredMimeType: String,
        onSuccess: (
            fileUrl: String,
            uploadedName: String,
            fileSize: Long,
            mimeType: String
        ) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!recordingFile.exists() || recordingFile.length() <= 0L) {
                    throw IOException("The voice recording is empty")
                }
                if (recordingFile.length() > 15L * 1024L * 1024L) {
                    throw IOException("The voice recording exceeds the 15 MB limit")
                }

                val safeName = safeAttachmentName(recordingFile.name)
                val extension = safeName.substringAfterLast('.', "").lowercase()
                val fallbackMimeType = when (extension) {
                    "wav" -> "audio/wav"
                    "m4a", "mp4" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "3gp" -> "audio/3gpp"
                    "ogg", "opus" -> "audio/ogg"
                    "mp3" -> "audio/mpeg"
                    else -> "application/octet-stream"
                }
                val uploadMimeType = declaredMimeType
                    .trim()
                    .takeIf { it.startsWith("audio/", ignoreCase = true) }
                    ?: fallbackMimeType
                val mediaType = uploadMimeType.toMediaTypeOrNull()
                val uploaderUserId = myUserId.toIntOrNull()
                    ?: throw IllegalStateException("Responder session is unavailable")
                val requestBody = recordingFile.asRequestBody(mediaType)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uploader_user_id", uploaderUserId.toString())
                    .addFormDataPart("file", safeName, requestBody)
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("upload_chat_file.php"))
                    .post(multipartBody)
                    .build()

                val json = executeJson(request)
                val uploadedUrl = json.optString("file_url").trim()
                val uploadedName = json.optString("file_name", safeName)
                    .trim()
                    .ifBlank { safeName }
                val uploadedSize = json.optLong("file_size", recordingFile.length())
                    .takeIf { it > 0L }
                    ?: recordingFile.length()
                val uploadedMime = json.optString("mime_type", uploadMimeType)
                    .trim()
                    .ifBlank { uploadMimeType }

                if (!json.optBoolean("success") || uploadedUrl.isBlank()) {
                    throw IOException(json.optString("message", "Voice upload failed"))
                }

                withContext(Dispatchers.Main) {
                    onSuccess(uploadedUrl, uploadedName, uploadedSize, uploadedMime)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onError(error.message ?: "Voice upload error")
                }
            } finally {
                recordingFile.delete()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILE / IMAGE / VOICE SENDING
    // ─────────────────────────────────────────────────────────────────────────
    fun sendFileMessage(
        meId: String,
        peer: ResponderBrief,
        uri: Uri,
        fileName: String,
        isImage: Boolean
    ) {
        val threadId = buildChatId(meId, peer.id)

        uploadFileToServer(
            uri = uri,
            fileName = fileName,
            onSuccess = { fileUrl, uploadedName, fileSize, mimeType ->
                pushFileMessageToFirebase(
                    threadId = threadId,
                    senderId = meId,
                    senderName = myUserName.ifBlank { meId },
                    role = myRole,
                    recipientId = peer.id,
                    fileUrl = fileUrl,
                    fileName = uploadedName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    isImage = isImage
                )
            },
            onError = { error ->
                latestNotification.value = "Upload failed: $error"
            }
        )
    }

    fun sendFileToDepartment(
        meId: String,
        department: String,
        uri: Uri,
        fileName: String,
        isImage: Boolean
    ) {
        val groupId = department.toIntOrNull() ?: return
        val realFileName = getRealFileName(uri, fileName)


        uploadFileToServer(
            uri = uri,
            fileName = realFileName,
            onSuccess = { fileUrl, uploadedName, fileSize, mimeType ->
                sendGroupAttachmentToSql(
                    groupId = groupId,
                    senderUserId = meId,
                    fileUrl = fileUrl,
                    fileName = uploadedName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    isImage = isImage
                )
            },
            onError = { error ->
                latestNotification.value = "Upload failed: $error"
            }
        )
    }

    fun sendVoiceMessage(
        meId: String,
        peer: ResponderBrief,
        recordingFile: File,
        durationMs: Long,
        mimeType: String
    ) {
        val threadId = buildChatId(meId, peer.id)
        val safeDuration = durationMs.coerceIn(0L, 120_000L)

        uploadVoiceFileToServer(
            recordingFile = recordingFile,
            declaredMimeType = mimeType,
            onSuccess = { fileUrl, uploadedName, fileSize, uploadedMimeType ->
                pushFileMessageToFirebase(
                    threadId = threadId,
                    senderId = meId,
                    senderName = myUserName.ifBlank { meId },
                    role = myRole,
                    recipientId = peer.id,
                    fileUrl = fileUrl,
                    fileName = uploadedName,
                    fileSize = fileSize,
                    mimeType = uploadedMimeType,
                    isImage = false,
                    isAudio = true,
                    audioDurationMs = safeDuration
                )
            },
            onError = { error ->
                latestNotification.value = "Voice message failed: $error"
            }
        )
    }

    fun sendVoiceToDepartment(
        meId: String,
        department: String,
        recordingFile: File,
        durationMs: Long,
        mimeType: String
    ) {
        val groupId = department.toIntOrNull()
        if (groupId == null) {
            recordingFile.delete()
            latestNotification.value = "Department channel is unavailable"
            return
        }
        val safeDuration = durationMs.coerceIn(0L, 120_000L)

        uploadVoiceFileToServer(
            recordingFile = recordingFile,
            declaredMimeType = mimeType,
            onSuccess = { fileUrl, uploadedName, fileSize, uploadedMimeType ->
                sendGroupAttachmentToSql(
                    groupId = groupId,
                    senderUserId = meId,
                    fileUrl = fileUrl,
                    fileName = uploadedName,
                    fileSize = fileSize,
                    mimeType = uploadedMimeType,
                    isImage = false,
                    isAudio = true,
                    audioDurationMs = safeDuration
                )
            },
            onError = { error ->
                latestNotification.value = "Voice message failed: $error"
            }
        )
    }

    private fun sendGroupAttachmentToSql(
        groupId: Int,
        senderUserId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        isImage: Boolean,
        isAudio: Boolean = false,
        audioDurationMs: Long = 0L
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("sender_user_id", senderUserId)
                    .add("file_url", fileUrl)
                    .add("file_name", fileName)
                    .add("mime_type", mimeType)
                    .add("file_size", fileSize.coerceAtLeast(0L).toString())
                    .add("is_image", if (isImage && !isAudio) "1" else "0")
                    .add("is_audio", if (isAudio) "1" else "0")
                    .add("audio_duration_ms", audioDurationMs.coerceAtLeast(0L).toString())
                    .build()

                val request = Request.Builder()
                    .url(apiUrl("send-interagency-group-attachment.php"))
                    .post(formBody)
                    .build()

                val json = executeJson(request)

                if (json.optBoolean("success")) {
                    loadInteragencyGroupMessages(groupId)
                } else {
                    launch(Dispatchers.Main) {
                        latestNotification.value = json.optString("message", "Failed to send attachment")
                    }
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    latestNotification.value = "Failed to send attachment: ${e.message}"
                }
            }
        }
    }



    private fun pushFileMessageToFirebase(
        threadId: String,
        senderId: String,
        senderName: String,
        role: String,
        recipientId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        isImage: Boolean,
        isAudio: Boolean = false,
        audioDurationMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val messageId = db.child("messages").child(threadId).push().key
        if (messageId.isNullOrBlank()) {
            latestNotification.value = "Unable to create attachment message."
            return
        }

        val messageType = when {
            isAudio -> "AUDIO"
            isImage -> "IMAGE"
            else -> "FILE"
        }
        val previewText = when {
            isAudio -> "🎙 Voice message"
            isImage -> "📷 Image"
            else -> "📎 $fileName"
        }
        val messageText = when {
            isAudio -> "Voice message"
            isImage -> "Image"
            else -> fileName
        }
        val data = mapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "role" to role,
            "text" to messageText,
            "type" to messageType,
            "attachmentUri" to fileUrl,
            "attachmentName" to fileName,
            "attachmentSize" to fileSize.coerceAtLeast(0L),
            "attachmentMimeType" to mimeType,
            "audioDurationMs" to if (isAudio) audioDurationMs.coerceAtLeast(0L) else 0L,
            "createdAt" to now,
            "status" to "sent"
        )

        val previousPreview = directThreadPreviewByThreadId[threadId]
        updateLocalThreadPreview(threadId, previewText, now)

        val updates: Map<String, Any?> = mapOf(
            "messages/$threadId/$messageId" to data,
            "threads/$threadId/lastMessage" to previewText,
            "threads/$threadId/lastMessageTime" to now,
            "threads/$threadId/lastSenderId" to senderId,
            "threads/$threadId/lastSenderName" to senderName,
            "threads/$threadId/participants/$senderId" to true,
            "threads/$threadId/participants/$recipientId" to true
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                incrementPrivateUnread(recipientId, threadId)
                dispatchPrivatePush(
                    senderId = senderId,
                    recipientId = recipientId,
                    threadId = threadId,
                    messageId = messageId,
                    senderName = senderName,
                    messageType = when {
                        isAudio -> "audio"
                        isImage -> "image"
                        else -> "file"
                    },
                    preview = messageText
                )
            }
            .addOnFailureListener { error ->
                restoreLocalThreadPreview(threadId, previousPreview)
                latestNotification.value =
                    "Attachment message was not sent: ${error.localizedMessage ?: "Firebase write failed"}"
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REACTIONS
    // ─────────────────────────────────────────────────────────────────────────
    fun addReaction(messageId: String, emoji: String, userId: String) {
        val threadId = currentThread.value?.id ?: return

        val reactionRef = db.child("messages")
            .child(threadId)
            .child(messageId)
            .child("reactions")
            .child(userId)

        reactionRef.get().addOnSuccessListener { snapshot ->
            val currentEmoji = snapshot.getValue(String::class.java)

            if (currentEmoji == emoji) {
                reactionRef.removeValue()
            } else {
                reactionRef.setValue(emoji)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UNREAD / NOTIFICATION HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    fun markResponderRead(responderId: String) {
        val threadId = buildChatId(myUserId, responderId)
        resetPrivateUnread(threadId)
        privateUnreadByThreadId[threadId] = 0

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

    override fun onCleared() {
        clearTyping()
        stopListeningToTyping()
        super.onCleared()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Always produces the same chatId regardless of who is "me" vs "peer" */
    private fun buildChatId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_", prefix = "pm_")
}
