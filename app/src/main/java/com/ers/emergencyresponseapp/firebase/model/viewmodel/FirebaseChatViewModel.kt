package com.ers.emergencyresponseapp.firebase.viewmodel

// ═══════════════════════════════════════════════════════════════════════════════
//  FILE 4 of 8 — FirebaseChatViewModel.kt
//  Place in:  app/src/main/java/com/ers/emergencyresponseapp/firebase/viewmodel/
//
//  The ViewModel sits between the UI (screen) and the Repository (database).
//  It holds the state (messages, loading, errors) and survives screen rotations.
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.firebase.model.FirebaseMessage
import com.ers.emergencyresponseapp.firebase.model.FirebaseUser
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebaseChatViewModel : ViewModel() {

    // ── Repository instance ───────────────────────────────────────────────────
    private val repository = FirebaseChatRepository()

    // ── UI state ──────────────────────────────────────────────────────────────
    // StateFlow is like a live variable — when it changes, the UI recomposes

    /** The list of messages in the currently open chat */
    private val _messages = MutableStateFlow<List<FirebaseMessage>>(emptyList())
    val messages: StateFlow<List<FirebaseMessage>> = _messages.asStateFlow()

    /** True while sending a message (disables the send button to prevent double-send) */
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /** The chat partner's user info (name, department, online status) */
    private val _chatPartner = MutableStateFlow<FirebaseUser?>(null)
    val chatPartner: StateFlow<FirebaseUser?> = _chatPartner.asStateFlow()

    /** Error message to show in a Snackbar/Toast if something fails */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────
    private var currentChatId  = ""
    private var currentUserId  = ""

    // ─────────────────────────────────────────────────────────────────────────
    //  OPEN CHAT — call when navigating to the chat screen
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Sets up everything needed for a chat between two users:
     *   1. Calculates the chatId
     *   2. Starts listening for messages in real-time
     *   3. Fetches the chat partner's user info
     *   4. Marks existing messages as "seen"
     *
     * Call this from the ChatScreen when it first appears:
     *   LaunchedEffect(Unit) {
     *       viewModel.openChat(myUserId, partnerUserId)
     *   }
     */
    fun openChat(myUserId: String, partnerUserId: String) {
        currentUserId = myUserId
        currentChatId = repository.buildChatId(myUserId, partnerUserId)

        // Start the real-time message listener
        startListeningToMessages()

        // Fetch the chat partner's profile
        loadChatPartnerInfo(partnerUserId)

        // Mark all incoming messages as "seen" (we opened the chat)
        viewModelScope.launch {
            repository.markMessagesSeen(currentChatId, myUserId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  START LISTENING — sets up the real-time Firebase listener
    // ─────────────────────────────────────────────────────────────────────────
    private fun startListeningToMessages() {
        viewModelScope.launch {
            // collect() runs forever until the coroutine is cancelled
            // Every time a new message arrives, this block runs and updates _messages
            repository.listenToMessages(currentChatId).collect { updatedMessages ->
                _messages.value = updatedMessages
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND MESSAGE
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Sends a message and updates the sending state.
     *
     * Usage in the UI:
     *   viewModel.sendMessage(
     *       senderId   = currentUserId,
     *       receiverId = partnerUserId,
     *       text       = inputText
     *   )
     */
    fun sendMessage(senderId: String, receiverId: String, text: String) {
        // Don't send empty messages
        if (text.isBlank()) return

        viewModelScope.launch {
            _isSending.value = true

            val success = repository.sendMessage(
                senderId   = senderId,
                receiverId = receiverId,
                text       = text.trim()
            )

            if (!success) {
                _errorMessage.value = "Failed to send message. Check your connection."
            }

            _isSending.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD CHAT PARTNER INFO
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadChatPartnerInfo(partnerUserId: String) {
        viewModelScope.launch {
            // Load once
            _chatPartner.value = repository.getUserInfo(partnerUserId)

            // Then keep listening for online/offline changes
            repository.listenToUserPresence(partnerUserId).collect { user ->
                _chatPartner.value = user
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CLEAR ERROR
    // ─────────────────────────────────────────────────────────────────────────
    fun clearError() {
        _errorMessage.value = null
    }
}