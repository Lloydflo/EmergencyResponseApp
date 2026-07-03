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
import com.ers.emergencyresponseapp.AppState
import com.ers.emergencyresponseapp.AppScreenTracker
import com.ers.emergencyresponseapp.MainActivity
import com.ers.emergencyresponseapp.R
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import android.provider.OpenableColumns
import android.content.Context

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
    private var threadsListener: ValueEventListener? = null
    private var messagesListenerPath: String? = null
    private var respondersListener: ValueEventListener? = null

    private var activeGroupId: Int? = null
    private var groupPollingJob: kotlinx.coroutines.Job? = null
    private val hasPrimedThread = mutableSetOf<String>()
    private val lastNotifiedMessageIdByThread = mutableMapOf<String, String>()

    // ─────────────────────────────────────────────────────────────────────────
    //  CONNECT — called once when CoordinationPortalScreen opens
    // ─────────────────────────────────────────────────────────────────────────
    fun connectRealtime(userId: String, userName: String, userRole: String) {
        myUserId   = userId
        myUserName = userName
        myRole     = userRole

        viewModelScope.launch {
            try {
                // Mark online
                firebaseRepo.setOnlineStatus(userId, isOnline = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD REAL RESPONDERS FROM FIREBASE /users
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadRespondersFromFirebase() {
        // Remove old listener if any
        respondersListener?.let { db.child("users").removeEventListener(it) }

        respondersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<ResponderBrief>()
                for (child in snapshot.children) {
                    val uid = child.key ?: return
                    val fullName   = child.child("fullName").getValue(String::class.java) ?: "Unknown"
                    val department = child.child("department").getValue(String::class.java) ?: "general"
                    val isOnline   = child.child("isOnline").getValue(Boolean::class.java) ?: false


                    // Skip the current user — you don't chat with yourself
                    if (uid == myUserId) continue

                    // Find existing entry to preserve unread count / last message
                    val existing = responders.firstOrNull { it.id == uid }

                    val latestThread = findLatestThreadForResponder(uid)

                    val previewMessage = latestThread?.first ?: existing?.lastMessage ?: ""
                    val previewTime = latestThread?.second ?: existing?.lastMessageTime ?: 0L

                    loaded.add(
                        ResponderBrief(
                            id          = uid,
                            username    = uid,
                            fullName    = fullName,
                            role        = department,
                            status      = if (isOnline) "online" else "offline",
                            lastMessage = previewMessage,
                            lastMessageTime = previewTime,
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

    private fun listenToThreads() {
        threadsListener?.let {
            db.child("threads").removeEventListener(it)
        }

        threadsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (thread in snapshot.children) {
                    val threadId = thread.key ?: continue
                    val lastMessage = thread.child("lastMessage")
                        .getValue(String::class.java) ?: ""

                    val lastMessageTime = thread.child("lastMessageTime")
                        .getValue(Long::class.java) ?: 0L

                    val responderId = threadId
                        .removePrefix("pm_")
                        .split("_")
                        .firstOrNull { it != myUserId }
                        ?: continue

                    val index = responders.indexOfFirst { it.id == responderId }

                    if (index >= 0) {
                        responders[index] = responders[index].copy(
                            lastMessage = lastMessage,
                            lastMessageTime = lastMessageTime
                        )
                    }
                }

                val sorted = responders.sortedByDescending { it.lastMessageTime }
                responders.clear()
                responders.addAll(sorted)
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }

        db.child("threads").addValueEventListener(threadsListener!!)
    }

    private fun loadInteragencyGroups(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://emergency-response.alertaraqc.com/api/api_app/get-interagency-groups.php?user_id=$userId")
                    .get()
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: ""

                val json = JSONObject(body)

                if (!json.optBoolean("success")) {
                    latestNotification.value = json.optString("message", "Failed to load groups")
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

                    loadedGroups.add(
                        DepartmentInfo(
                            name = groupId.toString(),
                            displayName = displayName,
                            emoji = icon,
                            lastMessage = when {
                                isMember -> "Tap to open group chat"
                                requestPending -> "Request pending approval"
                                else -> "Request access to join"
                            },
                            unreadCount = if (isMember) item.optInt("unreadCount", 0) else 0
                        )
                    )
                }

                launch(Dispatchers.Main) {
                    departments.clear()
                    departments.addAll(loadedGroups)
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    latestNotification.value = "Failed to load groups: ${e.message}"
                }
            }
        }
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
                    .url("https://emergency-response.alertaraqc.com/api/api_app/request-group-access.php")
                    .post(formBody)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()?.trim() ?: ""
                val json = JSONObject(body)

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
        viewModelScope.launch {
            try {
                firebaseRepo.setOnlineStatus(myUserId, isOnline = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Remove Firebase listeners to avoid memory leaks
        respondersListener?.let { db.child("users").removeEventListener(it) }
        threadsListener?.let { db.child("threads").removeEventListener(it) }
        stopListeningToMessages()
        groupPollingJob?.cancel()
        groupPollingJob = null
        activeGroupId = null
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT RESPONDER — open a real Firebase chat thread
    // ─────────────────────────────────────────────────────────────────────────
    fun selectResponderAndLoadHistory(meId: String, responder: ResponderBrief) {
        // Leaving department chat: stop group polling immediately to avoid mixed messages.
        activeGroupId = null
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SELECT DEPARTMENT — open a department group channel
    // ─────────────────────────────────────────────────────────────────────────
    fun selectDepartmentAndLoadHistory(dept: DepartmentInfo) {
        // Leaving private chat: remove old Firebase private listener.
        stopListeningToMessages()

        selectedDepartment.value = dept
        selectedResponder.value = null
        markDepartmentRead(dept.name)

        val groupId = dept.name.toIntOrNull() ?: return

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

    private fun loadInteragencyGroupMessages(groupId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://emergency-response.alertaraqc.com/api/api_app/get-interagency-group-messages.php?group_id=$groupId")
                    .get()
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()?.trim() ?: ""

                if (!response.isSuccessful || body.isBlank()) {
                    launch(Dispatchers.Main) {
                        latestNotification.value = "Failed to load groups: empty API response"
                    }
                    return@launch
                }

                val json = JSONObject(body)

                if (!json.optBoolean("success")) return@launch

                val arr = json.optJSONArray("messages")
                val loaded = mutableListOf<ChatMessage>()

                for (i in 0 until (arr?.length() ?: 0)) {
                    val item = arr!!.getJSONObject(i)
                    val typeText = item.optString("type", "TEXT")

                    val messageType = when (typeText.uppercase()) {
                        "IMAGE" -> MessageType.IMAGE
                        "FILE" -> MessageType.FILE
                        else -> MessageType.TEXT
                    }



                    val senderId = item.optString("senderId")
                    val senderName = item.optString("senderName")
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
                            status = MessageStatus.SENT,
                            isOwn = senderId == myUserId || senderName.equals(myUserName, ignoreCase = true),
                            attachmentUri = item.optString("attachmentUri").takeIf { it.isNotBlank() && it != "null" },
                            attachmentName = item.optString("attachmentName").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }

                launch(Dispatchers.Main) {
                    // Ignore stale results if user already switched to another chat.
                    if (currentThread.value?.id == "group_$groupId") {
                        messages.clear()
                        messages.addAll(loaded.sortedBy { it.createdAt })
                    }
                }

                val threadId = "group_$groupId"
                maybeNotifyIncoming(threadId, loaded)

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    latestNotification.value = "Failed to load group messages"
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
                        else -> MessageType.TEXT
                    }

                    val attachmentUri = child.child("attachmentUri").getValue(String::class.java)
                    val attachmentName = child.child("attachmentName").getValue(String::class.java)
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
        val channelId = "coordination_messages"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Coordination Messages",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "New coordination chat messages"
                    }
                )
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_coordination", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sender = message.senderName.ifBlank { "Responder" }
        val content = when {
            !message.text.isNullOrBlank() -> message.text!!
            message.type == MessageType.IMAGE -> "Sent an image"
            message.type == MessageType.FILE -> "Sent a file"
            else -> "New message"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$sender sent a message")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val hasNotificationPermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (hasNotificationPermission) {
            NotificationManagerCompat.from(context).notify(threadId.hashCode(), notification)
        }
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
        val groupId = department.toIntOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("sender_user_id", meId)
                    .add("text", body)
                    .build()

                val request = Request.Builder()
                    .url("https://emergency-response.alertaraqc.com/api/api_app/send-interagency-group-message.php")
                    .post(formBody)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: "")

                if (json.optBoolean("success")) {
                    loadInteragencyGroupMessages(groupId)
                } else {
                    launch(Dispatchers.Main) {
                        latestNotification.value = json.optString("message", "Failed to send message")
                    }
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    latestNotification.value = "Failed to send message"
                }
            }
        }
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
            "createdAt"  to System.currentTimeMillis(),
            "status" to "sent"
        )
        msgRef.setValue(data)

        // Also update the thread's lastMessage so the inbox shows a preview
        db.child("threads").child(threadId).updateChildren(mapOf(
            "lastMessage"     to text,
            "lastMessageTime" to System.currentTimeMillis()
        ))
    }

    private fun uploadFileToServer(
        uri: Uri,
        fileName: String,
        onSuccess: (fileUrl: String, uploadedName: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")

                val tempFile = File(context.cacheDir, fileName)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }

                val requestBody = tempFile
                    .asRequestBody("application/octet-stream".toMediaTypeOrNull())

                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    requestBody
                )

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(filePart)
                    .build()

                val request = Request.Builder()
                    .url("https://emergency-response.alertaraqc.com/api/api_app/upload_chat_file.php")
                    .post(multipartBody)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: ""

                val json = JSONObject(body)

                if (json.optBoolean("success")) {
                    onSuccess(
                        json.optString("file_url"),
                        json.optString("file_name")
                    )
                } else {
                    onError(json.optString("message", "Upload failed"))
                }

            } catch (e: Exception) {
                onError(e.message ?: "Upload error")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILE / IMAGE SENDING
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
            onSuccess = { fileUrl, uploadedName ->
                pushFileMessageToFirebase(
                    threadId = threadId,
                    senderId = meId,
                    senderName = myUserName.ifBlank { meId },
                    role = myRole,
                    fileUrl = fileUrl,
                    fileName = fileName,
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
            onSuccess = { fileUrl, uploadedName ->
                sendGroupAttachmentToSql(
                    groupId = groupId,
                    senderUserId = meId,
                    fileUrl = fileUrl,
                    fileName = realFileName,
                    isImage = isImage
                )
            },
            onError = { error ->
                latestNotification.value = "Upload failed: $error"
            }
        )
    }

    private fun sendGroupAttachmentToSql(
        groupId: Int,
        senderUserId: String,
        fileUrl: String,
        fileName: String,
        isImage: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = when {
                    isImage -> "image/*"
                    fileName.endsWith(".pdf", true) -> "application/pdf"
                    fileName.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    fileName.endsWith(".doc", true) -> "application/msword"
                    else -> "application/octet-stream"
                }

                val formBody = okhttp3.FormBody.Builder()
                    .add("group_id", groupId.toString())
                    .add("sender_user_id", senderUserId)
                    .add("file_url", fileUrl)
                    .add("file_name", fileName)
                    .add("mime_type", mimeType)
                    .add("file_size", "0")
                    .add("is_image", if (isImage) "1" else "0")
                    .build()

                val request = Request.Builder()
                    .url("https://emergency-response.alertaraqc.com/api/api_app/send-interagency-group-attachment.php")
                    .post(formBody)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()?.trim() ?: ""
                val json = JSONObject(body)

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

    private fun findLatestThreadForResponder(responderId: String): Pair<String, Long>? {
        val threadId = buildChatId(myUserId, responderId)

        var result: Pair<String, Long>? = null

        db.child("threads").child(threadId).get()
            .addOnSuccessListener { snapshot ->
                val lastMessage = snapshot.child("lastMessage").getValue(String::class.java) ?: ""
                val lastMessageTime = snapshot.child("lastMessageTime").getValue(Long::class.java) ?: 0L

                if (lastMessage.isNotBlank() && lastMessageTime > 0L) {
                    val index = responders.indexOfFirst { it.id == responderId }

                    if (index >= 0) {
                        responders[index] = responders[index].copy(
                            lastMessage = lastMessage,
                            lastMessageTime = lastMessageTime
                        )
                    }
                }
            }

        return result
    }

    private fun pushFileMessageToFirebase(
        threadId: String,
        senderId: String,
        senderName: String,
        role: String,
        fileUrl: String,
        fileName: String,
        isImage: Boolean
    ) {
        val now = System.currentTimeMillis()
        val msgRef = db.child("messages").child(threadId).push()

        val data = mapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "role" to role,
            "text" to if (isImage) "Image" else fileName,
            "type" to if (isImage) "IMAGE" else "FILE",
            "attachmentUri" to fileUrl,
            "attachmentName" to fileName,
            "createdAt" to now
        )

        msgRef.setValue(data)

        db.child("threads").child(threadId).updateChildren(
            mapOf(
                "lastMessage" to if (isImage) "📷 Image" else "📎 $fileName",
                "lastMessageTime" to now
            )
        )
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
