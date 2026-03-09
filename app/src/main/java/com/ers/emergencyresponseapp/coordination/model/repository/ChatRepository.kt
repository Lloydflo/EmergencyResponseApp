package com.ers.emergencyresponseapp.coordination.model.repository

import com.ers.emergencyresponseapp.coordination.model.ChatMessage
import com.ers.emergencyresponseapp.coordination.model.ChatThread
import com.ers.emergencyresponseapp.coordination.model.MessageType
import com.ers.emergencyresponseapp.coordination.model.ThreadType

class ChatRepository {

    private val threads = mutableMapOf<String, ChatThread>()
    private val messagesByThread = mutableMapOf<String, MutableList<ChatMessage>>()

    fun getOrCreatePrivateThread(
        meId: String,
        peerId: String,
        peerName: String
    ): ChatThread {
        val threadId = buildPrivateThreadId(meId, peerId)
        return threads.getOrPut(threadId) {
            ChatThread(
                id = threadId,
                type = ThreadType.PRIVATE,
                name = peerName,
                participants = listOf(meId, peerId)
            )
        }
    }

    fun getOrCreateDepartmentThread(
        departmentName: String,
        displayName: String
    ): ChatThread {
        val threadId = "dept_$departmentName"
        return threads.getOrPut(threadId) {
            ChatThread(
                id = threadId,
                type = ThreadType.DEPARTMENT,
                name = displayName,
                participants = listOf(departmentName)
            )
        }
    }

    fun getMessages(threadId: String): List<ChatMessage> {
        return messagesByThread[threadId]?.toList() ?: emptyList()
    }

    fun replaceMessages(threadId: String, items: List<ChatMessage>) {
        messagesByThread[threadId] = items.toMutableList()
    }

    fun addMessage(message: ChatMessage) {
        val list = messagesByThread.getOrPut(message.threadId) { mutableListOf() }
        list.add(message)
    }

    fun clearThread(threadId: String) {
        messagesByThread[threadId]?.clear()
    }

    fun seedPrivateHistoryIfEmpty(
        threadId: String,
        meId: String,
        peerId: String,
        peerRole: String
    ) {
        if (messagesByThread[threadId].isNullOrEmpty()) {
            messagesByThread[threadId] = mutableListOf(
                ChatMessage(
                    id = "h1",
                    threadId = threadId,
                    senderId = peerId,
                    senderName = "Peer",
                    role = peerRole,
                    type = MessageType.TEXT,
                    text = "Hey, need backup?",
                    createdAt = System.currentTimeMillis() - 120_000,
                    isOwn = false
                ),
                ChatMessage(
                    id = "h2",
                    threadId = threadId,
                    senderId = meId,
                    senderName = "You",
                    role = peerRole,
                    type = MessageType.TEXT,
                    text = "On my way.",
                    createdAt = System.currentTimeMillis() - 90_000,
                    isOwn = true
                )
            )
        }
    }

    fun seedDepartmentHistoryIfEmpty(
        threadId: String,
        department: String
    ) {
        if (messagesByThread[threadId].isNullOrEmpty()) {
            messagesByThread[threadId] = mutableListOf(
                ChatMessage(
                    id = "d1",
                    threadId = threadId,
                    senderId = "2",
                    senderName = "Alice",
                    role = department,
                    type = MessageType.TEXT,
                    text = "Fire team, status update.",
                    createdAt = System.currentTimeMillis() - 60_000,
                    isOwn = false
                ),
                ChatMessage(
                    id = "d2",
                    threadId = threadId,
                    senderId = "3",
                    senderName = "Bob",
                    role = department,
                    type = MessageType.TEXT,
                    text = "Medical on scene.",
                    createdAt = System.currentTimeMillis() - 30_000,
                    isOwn = false
                )
            )
        }
    }

    private fun buildPrivateThreadId(a: String, b: String): String {
        return listOf(a, b).sorted().joinToString("_", prefix = "pm_")
    }
}